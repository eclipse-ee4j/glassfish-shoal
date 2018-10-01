/*
 * Copyright (c) 1997, 2018 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

package com.sun.enterprise.mgmt;

import com.sun.enterprise.ee.cms.core.GMSMember;
import com.sun.enterprise.ee.cms.impl.base.GMSThreadFactory;
import com.sun.enterprise.ee.cms.impl.base.PeerID;
import com.sun.enterprise.ee.cms.impl.base.SystemAdvertisement;
import com.sun.enterprise.ee.cms.impl.base.Utility;
import com.sun.enterprise.ee.cms.logging.GMSLogDomain;
import com.sun.enterprise.mgmt.transport.Message;
import com.sun.enterprise.mgmt.transport.MessageEvent;
import com.sun.enterprise.mgmt.transport.MessageImpl;
import com.sun.enterprise.mgmt.transport.MessageIOException;
import com.sun.enterprise.mgmt.transport.MessageListener;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * HealthMonitor utilizes MasterNode to determine self designation. All nodes cache other node's states, and can act as
 * a master node at any given point in time. The intention behind the designation is that no node other than the master
 * node should determine collective state and communicate it to group members.
 * <p>
 * TODO: Convert the InDoubt Peer Determination and Failure Verification into Callable FutureTask using
 * java.util.concurrent
 */
public class HealthMonitor implements MessageListener, Runnable {
	private static final Logger LOG = GMSLogDomain.getLogger(GMSLogDomain.GMS_LOGGER);
	// Default health reporting period
	private long timeout = 10 * 1000L;
	private long verifyTimeout = 10 * 1000L;
	private int maxMissedBeats = 3;
	private final Object threadLock = new Object();
	private final Object indoubtthreadLock = new Object();
	private final ConcurrentHashMap<PeerID, HealthMessage.Entry> cache = new ConcurrentHashMap<PeerID, HealthMessage.Entry>();
	private MasterNode masterNode = null;
	private ClusterManager manager = null;
	private final PeerID localPeerID;
	private volatile boolean started = false;
	private volatile boolean stop = false;

	private ConcurrentHashMap<String, MsgSendStats> msgSendStats = new ConcurrentHashMap<String, MsgSendStats>();

	private Thread healthMonitorThread = null;
	private Thread failureDetectorThread = null;
	private static final String NODEADV = "NAD";

	private InDoubtPeerDetector inDoubtPeerDetector;
	static final String[] states = { "starting", "started", "alive", "clusterstopping", "peerstopping", "stopped", "dead", "indoubt", "unknown", "ready",
	        "aliveandready" };
	// "alive_in_isolation"};
	private static final short STARTING = 0;
	private static final short ALIVE = 2;
	public static final short CLUSTERSTOPPING = 3;
	public static final short PEERSTOPPING = 4;
	public static final short STOPPED = 5;
	public static final short DEAD = 6;
	private static final short INDOUBT = 7;
	private static final short UNKNOWN = 8;
	private static final short READY = 9;
	private static final short ALIVEANDREADY = 10;
	public static final String HEALTHM = "HM";
	private final Object cacheLock = new Object();
	private final Object verifierLock = new Object();
	public volatile boolean outstandingFailureToVerify = false;

	private static final String MEMBER_STATE_QUERY = "MEMBERSTATEQUERY";
	private static final String MEMBER_STATE_RESPONSE = "MEMBERSTATERESPONSE";
	private static final String WATCHDOG_NOTIFICATION = "WATCHDOG_NOTIFICATION";
	private volatile boolean readyStateComplete = false;
	private volatile boolean JoinedAndReadyReceived = false; // keep sending READY till receive JoinedAndReadyNotification.
	private List<String> joinedAndReadyMembers = new LinkedList<String>();

	private Message aliveMsg = null;
	private Message aliveAndReadyMsg = null;

	// counter for keeping track of the seq ids of the health messages
	AtomicLong hmSeqID = new AtomicLong();

	// use LWRMulticast to send messages for getting the member state
	LWRMulticast mcast = null;
	int lwrTimeout = 6000;
	public static final long DEFAULT_MEMBERSTATE_TIMEOUT = 0L;
	final private long defaultThreshold;
	private ReentrantLock sendStopLock = new ReentrantLock(true);

	private static final String CONNECTION_REFUSED = "Connection refused";
	private long failureDetectionTCPTimeout;
	private int failureDetectionTCPPort;
	private final ThreadPoolExecutor isConnectedPool;
	private ConcurrentHashMap<PeerID, MemberStateResult> memberStateResults = new ConcurrentHashMap<PeerID, MemberStateResult>();
	// private ShutdownHook shutdownHook;

	/**
	 * Constructor for the HealthMonitor object
	 *
	 * @param manager the ClusterManager
	 * @param maxMissedBeats Maximum retries before failure
	 * @param verifyTimeout timeout in milliseconds that the health monitor waits before finalizing that the in doubt peer
	 * is dead.
	 * @param timeout in milliseconds that the health monitor waits before retrying an indoubt peer's availability.
	 * @param failureDetectionTCPPort the tcp port of failure Detection
	 * @param failureDetectionTCPTimeout the timeout to detect the failure
	 */
	public HealthMonitor(final ClusterManager manager, final long timeout, final int maxMissedBeats, final long verifyTimeout,
	        final long failureDetectionTCPTimeout, final int failureDetectionTCPPort) {
		this.timeout = timeout;
		this.defaultThreshold = timeout * maxMissedBeats;
		this.maxMissedBeats = maxMissedBeats;
		this.verifyTimeout = verifyTimeout;
		this.manager = manager;
		this.masterNode = manager.getMasterNode();
		this.localPeerID = manager.getPeerID();
		this.failureDetectionTCPTimeout = failureDetectionTCPTimeout;
		this.failureDetectionTCPPort = failureDetectionTCPPort;
		ThreadFactory isConnectedThreadFactory = new GMSThreadFactory("GMS-isConnected-Group-" + manager.getGroupName() + "-thread");
		isConnectedPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(3, isConnectedThreadFactory);
		if (LOG.isLoggable(Level.CONFIG)) {
			LOG.config("HealthMonitor: heartBeatTimeout(ms)=" + timeout + " maxMissedBeats=" + maxMissedBeats + " failureDetectionTCPTimeout(ms)="
			        + failureDetectionTCPTimeout + " failureDetectionTCPPort=" + failureDetectionTCPPort);
		}

		try {
			mcast = new LWRMulticast(manager, this);
			mcast.setSoTimeout(lwrTimeout);
		} catch (IOException e) {
			LOG.log(Level.WARNING, "mgmt.healthmonitor.lwrmulticastioexception", e.getLocalizedMessage());
		}

		// this.shutdownHook = new ShutdownHook();
		// Runtime.getRuntime().addShutdownHook(shutdownHook);
	}

	/**
	 * A member is considered INDOUBT when a heartbeat has not been received in this amout of time. (in milliseconds.)
	 * 
	 * @return the duration
	 */
	public long getIndoubtDuration() {
		return timeout * maxMissedBeats;
	}

	void fine(String msg, Object[] obj) {
		if (LOG.isLoggable(Level.FINE)) {
			LOG.log(Level.FINE, msg, obj);
		}
	}

	void fine(String msg) {
		if (LOG.isLoggable(Level.FINE)) {
			LOG.log(Level.FINE, msg);
		}
	}

	/**
	 * Creates a Message containing this node's state
	 *
	 * @param state member state
	 * @return a Message containing this node's state
	 */
	private Message createHealthMessage(final short state) {
		return createMessage(state, HEALTHM, manager.getSystemAdvertisement());
	}

	private Message createMessage(final short state, final String tag, final SystemAdvertisement adv) {
		final Message msg = new MessageImpl(Message.TYPE_HEALTH_MONITOR_MESSAGE);
		final HealthMessage hm = new HealthMessage();
		hm.setSrcID(localPeerID);
		final HealthMessage.Entry entry = new HealthMessage.Entry(adv, states[state], hmSeqID.incrementAndGet());
		hm.add(entry);
		msg.addMessageElement(tag, hm);
		// add this state to the local health cache.
		fine("createMessage() => putting into cache " + entry.adv.getName() + " state is " + entry.state);
		synchronized (cacheLock) {
			cache.put(entry.id, entry);
		}
		// fine(" after putting into cache " + cache + " , contents are :-");
		// print(cache);
		return msg;
	}

	// Fix for glassfish issue 9055
	// This method is used by master to send a healthmessage reporting another peers state.
	// use entry and its entry.seqID that is already in cache.
	// NOTE: the entry.seqID is NEVER incremented for IN_DOUBT state. The IN_DOUBT state is reported by Master.
	// If an INDOUBT peer ever comes back, its HM entry.seqID will be same as last hm it sent.
	// An INDOUBT peer that starts sending HM again either was slowed by high load. To simulate
	// high load, testing has SUSPENDED a gms client for a minute and then CONTINUED it. The peer's hm seqID
	// will be same before and after the master has reported INDOUBT to the gms grou.
	//
	// Master hmseqid was incorrectly getting used when reporting another peers state using #createMessage.
	// Also this method does not update the cache as createMessage does, the entry has already been updated in the cache
	// when
	// Master is reporting another peers state for INDOUBT or DEAD.
	private Message createHealthMessageForOtherPeer(final HealthMessage.Entry entry) {
		final Message msg = new MessageImpl(Message.TYPE_HEALTH_MONITOR_MESSAGE);
		final HealthMessage hm = new HealthMessage();
		hm.setSrcID(localPeerID);
		if (LOG.isLoggable(Level.FINE)) {
			LOG.log(Level.FINE, "create health message state: " + entry.state + " for member: " + entry.adv.getName() + " Master member reporting for peer is "
			        + manager.getInstanceName() + " for group: " + manager.getGroupName());
		}
		hm.add(entry);
		msg.addMessageElement(HEALTHM, hm);
		// do not update cache, it already has correct state in it.
		return msg;
	}

	private Message getAliveMessage() {
		if (aliveMsg == null) {
			aliveMsg = createHealthMessage(ALIVE);
		}
		return aliveMsg;
	}

	private Message getAliveAndReadyMessage() {
		if (aliveAndReadyMsg == null) {
			aliveAndReadyMsg = createHealthMessage(ALIVEANDREADY);
		}
		return aliveAndReadyMsg;
	}

	/**
	 * {@inheritDoc}
	 */
	public void receiveMessageEvent(final MessageEvent event) throws MessageIOException {
		// if this peer is stopping, then stop processing incoming health messages.
		if (manager.isStopping()) {
			return;
		}
		if (started) {
			final Message msg;
			try {
				// grab the message from the event
				msg = event.getMessage();
				if (msg != null) {
					for (Map.Entry<String, Serializable> entry : msg.getMessageElements()) {
						if (entry.getKey().equals(HEALTHM)) {
							Serializable value = entry.getValue();
							if (value instanceof HealthMessage) {
								HealthMessage hm = (HealthMessage) value;
								updateHealthMessage(hm);

								// check if this message has latestMasterViewId from master.
								masterNode.processForLatestMasterViewId(msg, hm.getSrcID());
								process(hm);
							} else {
								LOG.log(Level.WARNING, "mgmt.unknownMessage");
							}
						} else if (entry.getKey().equals(MEMBER_STATE_QUERY)) {
							processMemberStateQuery(msg);
						} else if (entry.getKey().equals(MEMBER_STATE_RESPONSE)) {
							processMemberStateResponse(msg);
						} else if (entry.getKey().equals(WATCHDOG_NOTIFICATION)) {
							processWatchDogNotification(msg);
						}
					}
				}
			} catch (Throwable e) {
				if (LOG.isLoggable(Level.FINE)) {
					e.printStackTrace();
				}
				LOG.log(Level.WARNING, e.getLocalizedMessage());
			}
		}
	}

	public int getType() {
		return Message.TYPE_HEALTH_MONITOR_MESSAGE;
	}

	private SystemAdvertisement getNodeAdvertisement(Message msg) {
		SystemAdvertisement adv = null;
		Object value = msg.getMessageElement(NODEADV);
		if (value instanceof SystemAdvertisement) {
			adv = (SystemAdvertisement) value;
			if (!adv.getID().equals(localPeerID)) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.log(Level.FINER, "Received a System advertisement Name :" + adv.getName());
				}
			}
		}
		return adv;
	}

	private void processMemberStateQuery(Message msg) {
		SystemAdvertisement adv = null;
		LOG.fine(" received a MemberStateQuery...");
		try {
			adv = getNodeAdvertisement(msg);
			if (adv != null) {
				PeerID sender = adv.getID(); // sender of this query
				String state = getStateFromCache(localPeerID);
				Message response = createMemberStateResponse(state);
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine(" sending via LWR response to " + sender.toString() + " with state " + state + " for " + localPeerID);
				}
				final boolean sent = mcast.send((PeerID) sender, response); // send the response back to the query sender
				if (!sent) {
					LOG.log(Level.WARNING, "mgmt.healthmonitor.processmemberstatequery", adv.getName());
				}
			} else {
				LOG.log(Level.WARNING, "mgmt.healthmonitor.invalidquery");
			}
		} catch (IOException e) {
			LOG.log(Level.WARNING, "mgmt.healthmonitor.lwrmulticast.send.failed", e.getLocalizedMessage());
		}
	}

	private void processMemberStateResponse(Message msg) {
		String memberState = null;
		Object value = msg.getMessageElement(MEMBER_STATE_RESPONSE);
		if (value != null)
			memberState = value.toString();
		SystemAdvertisement adv = getNodeAdvertisement(msg);
		if (adv == null) {
			LOG.log(Level.WARNING, "mgmt.healthmonitor.nosenderadv");
			return;
		}

		MemberStateResult result = memberStateResults.get(adv.getID());
		if (result != null) {
			synchronized (result.lock) {
				result.memberState = memberState;
				memberStateResults.remove(adv.getID(), result);
				result.lock.notifyAll();
			}
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine(" member state in processMemberStateResponse() is " + memberState + " for member " + adv.getName());
			}
		} else if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("memberStateResponse received too late. result already removed by timeout. response from " + adv.getName() + " state=" + memberState);
		}
	}

	// Master node processes watchdog notification of all other instances in gms group.

	// All other instances in the gms group watch for watch dog notification of Master failure.
	// Only newly elected master reports failure of failed master (implemented by assignAndReportFailure)
	private void processWatchDogNotification(Message msg) {
		final SystemAdvertisement fromAdv = getNodeAdvertisement(msg);
		if (fromAdv == null) {
			LOG.fine("ignoring WATCHDOG_NOTIFICATION with a null sender advertisement");
			return;
		}
		final GMSMember watchdogMember = Utility.getGMSMember(fromAdv);
		if (!watchdogMember.isWatchDog()) {
			LOG.fine("ignoring WATCHDOG_NOTIFICATION from member:" + watchdogMember.getMemberToken() + " of group " + watchdogMember.getGroupName()
			        + " received one without a WATCHDOG member sender advertisement");
			return;
		}
		Object value = msg.getMessageElement(WATCHDOG_NOTIFICATION);
		if (!(value instanceof String)) {
			LOG.log(Level.WARNING, "mgmt.unknownMessage");
			return;
		}
		String failedTokenName = (String) value;
		final PeerID failedMemberId = manager.getID(failedTokenName);
		boolean masterFailed = (failedMemberId == null ? false : failedMemberId.equals(masterNode.getMasterNodeID()));
		if (masterNode.isMaster() && masterNode.isMasterAssigned() || masterFailed) {
			LOG.log(Level.INFO, "mgmt.healthmonitor.watchdog", new Object[] { failedTokenName, failedMemberId, watchdogMember.getMemberToken(), masterFailed });

			HealthMessage.Entry failedEntry;
			synchronized (cache) {
				failedEntry = cache.get(failedMemberId);

				if (failedEntry == null) {
					LOG.info("ignoring WATCHDOG FAILURE NOTIFICATION: can not find member: " + failedTokenName + " of group: " + manager.getGroupName()
					        + " with id:" + failedMemberId);
					return;
				}

				if (failedEntry.isState(STOPPED) || failedEntry.isState(DEAD) || failedEntry.isState(PEERSTOPPING) || failedEntry.isState(CLUSTERSTOPPING)
				        || failedEntry.isState(STARTING)) {
					String logMsg = MessageFormat.format(
					        "ignoring WATCHDOG FAILURE Notification for member: {0} of group: {1} with last heartbeat state of {2} at {3,time,full} on {3,date}.",
					        failedTokenName, manager.getGroupName(), failedEntry.state, new Date(failedEntry.timestamp));
					LOG.info(logMsg);
					return;
				}

				// Failure validation
				if (failedEntry.isState(INDOUBT)) {
					LOG.info("received WATCHDOG failure notification for " + failedTokenName + " with local hm state=" + failedEntry.state);
				}

				LOG.info("validated FAILURE reported by WATCHDOG FAILURE notification for " + failedTokenName + " of group: " + manager.getGroupName()
				        + " last heartbeat state:" + failedEntry.state + " received at "
				        + MessageFormat.format(" {0,time,full} on {0,date}", new Date(failedEntry.timestamp)));
				assignAndReportFailure(failedEntry);
			}
		}
	}

	/**
	 * creates a node query message specially for querying the member state
	 *
	 * @return msg Message
	 */
	private Message createMemberStateQuery() {
		Message msg = new MessageImpl(Message.TYPE_HEALTH_MONITOR_MESSAGE);
		msg.addMessageElement(NODEADV, manager.getSystemAdvertisement());
		msg.addMessageElement(MEMBER_STATE_QUERY, "member state query");
		LOG.log(Level.FINE, "Created a Member State Query Message ");
		return msg;
	}

	/**
	 * creates a node response message specially for sending the member state
	 *
	 * @return msg Message
	 */
	private Message createMemberStateResponse(String myState) {
		Message msg = new MessageImpl(Message.TYPE_HEALTH_MONITOR_MESSAGE);
		msg.addMessageElement(MEMBER_STATE_RESPONSE, myState);
		msg.addMessageElement(NODEADV, manager.getSystemAdvertisement());
		if (LOG.isLoggable(Level.FINE)) {
			LOG.log(Level.FINE, "Created a Member State Response Message with " + myState);
		}
		return msg;
	}

	/**
	 * creates a WATCHDOG notification for <code>failedServerToken</code>
	 * 
	 * @param failedServerToken failed server token
	 *
	 * @return msg Message
	 */
	private Message createWatchdogNotification(String failedServerToken) {
		Message msg = new MessageImpl(Message.TYPE_HEALTH_MONITOR_MESSAGE);
		msg.addMessageElement(WATCHDOG_NOTIFICATION, failedServerToken);
		msg.addMessageElement(NODEADV, manager.getSystemAdvertisement());
		if (LOG.isLoggable(Level.FINE)) {
			LOG.log(Level.FINE, "Created a WATCHDOG Notification Message for member:" + failedServerToken + " of group:" + manager.getGroupName());
		}
		return msg;
	}

	/**
	 * process a health message received on the wire into cache
	 *
	 * @param hm Health message to process
	 */
	private void process(final HealthMessage hm) {

		// discard loopback messages
		if (!hm.getSrcID().equals(localPeerID)) {
			// current assumption is that there is only 1 entry
			// per health message
			for (HealthMessage.Entry entry : hm.getEntries()) {
				if (LOG.isLoggable(Level.FINEST)) {
					LOG.log(Level.FINEST, "Processing Health Message " + entry.getSeqID() + " for entry " + entry.adv.getName() + " startTime="
					        + entry.getSrcStartTime() + " state=" + entry.state);
				}
				synchronized (cacheLock) {
					HealthMessage.Entry cachedEntry = cache.get(entry.id);
					if (cachedEntry != null) {
						if (LOG.isLoggable(Level.FINEST)) {
							LOG.log(Level.FINEST, "cached entry name=" + cachedEntry.adv.getName() + " startTime=" + cachedEntry.getSrcStartTime() + " seqId="
							        + cachedEntry.getSeqID() + " state=" + cachedEntry.state);
						}
						if (entry.isFromSameMember(cachedEntry) && entry.getSrcStartTime() < cachedEntry.getSrcStartTime()) {
							LOG.fine("Discarding older health message from a previously failed run of member " + entry.adv.getName());
							return;
						}

						// only compare sequence ids if heathmessage entrys from same member with same start time
						// if (entry.getSeqID() <= cachedEntry.getSeqId()) {
						if (cachedEntry.isFromSameMemberStartup(entry) && entry.getSeqID() < cachedEntry.getSeqID()) {
							if (LOG.isLoggable(Level.FINER)) {
								LOG.log(Level.FINER,
								        MessageFormat.format(
								                "Received an older health message from source member {2} seqId={0}."
								                        + " Current cached health message seqId:{1}. ",
								                entry.getSeqID(), cachedEntry.getSeqID(), entry.adv.getName()));
							}
							if (entry.state.equals(states[CLUSTERSTOPPING]) || entry.state.equals(states[PEERSTOPPING])) {
								// dont discard the message
								// and don't alter the state if the cachedEntry's state is stopped
								LOG.log(Level.FINER, "Received an older health message " + "with clusterstopping state."
								        + " Calling handleStopEvent() to handle shutdown state.");

								if (!cachedEntry.state.equals(states[STOPPED])) {
									cache.put(entry.id, entry);
								}
								handleStopEvent(entry);
							} else if (entry.state.equals(states[READY])) {
								LOG.fine("Received an older health message with Joined and Ready state. "
								        + "Calling handleReadyEvent() for handling the peer's ready state");
								handleReadyEvent(entry);
							} else {
								LOG.log(Level.FINER, "Discarding older health message");
							}
							return;
						}
					}
					cache.put(entry.id, entry);
					if (LOG.isLoggable(Level.FINER)) {
						LOG.log(Level.FINER,
						        "Put into cache " + entry.adv.getName() + " state = " + entry.state + " peerid = " + entry.id + " seq id=" + entry.getSeqID());
					}
				}
				// fine("after putting into cache " + cache + " , contents are :-");
				// print(cache);
				if (!manager.getClusterViewManager().containsKey(entry.id) && (!entry.state.equals(states[CLUSTERSTOPPING])
				        && !entry.state.equals(states[PEERSTOPPING]) && !entry.state.equals(states[STOPPED]) && !entry.state.equals(states[DEAD]))) {
					try {
						masterNode.probeNode(entry);
					} catch (IOException e) {
						if (LOG.isLoggable(Level.FINE)) {
							e.printStackTrace();
						}
						LOG.log(Level.FINE, "IOException occured while sending probeNode() Message in HealthMonitor:" + e.getLocalizedMessage());
					}
				}
				if (entry.state.equals(states[READY])) {
					handleReadyEvent(entry);
				}
				if (entry.state.equals(states[PEERSTOPPING]) || entry.state.equals(states[CLUSTERSTOPPING])) {
					handleStopEvent(entry);
				}
				if (entry.state.equals(states[INDOUBT]) || entry.state.equals(states[DEAD])) {
					if (entry.id.equals(localPeerID)) {
						if (readyStateComplete) {
							reportMyState(ALIVEANDREADY, hm.getSrcID());
						} else {
							reportMyState(ALIVE, hm.getSrcID());
						}
					} else {
						if (entry.state.equals(states[INDOUBT])) {
							LOG.log(Level.FINE, "Peer " + entry.id.toString() + " is suspected failed. Its state is " + entry.state);
							notifyLocalListeners(entry.state, entry.adv);
						}
						if (entry.state.equals(states[DEAD])) {
							LOG.log(Level.FINE, "Peer " + entry.id.toString() + " has failed. Its state is " + entry.state);
							cleanAllCaches(entry);
						}
					}
				}
			}
		}
	}

	private void handleReadyEvent(final HealthMessage.Entry entry) {
		synchronized (cacheLock) {
			cache.put(entry.id, entry);
		}
		// fine(" after putting into cache " + cache + " , contents are :-");
		// print(cache);
		if (entry.id.equals(masterNode.getMasterNodeID())) {
			// if this is a ready state sent out by master, take no action here as master would send out a view to the group.
			return;
		}
		// if this is a ready state sent by a non-master, and if I am the assigned master, send out a new view and notify local
		// listeners.
		if (masterNode.isMaster() && masterNode.isMasterAssigned()) {
			synchronized (joinedAndReadyMembers) {
				// don't notify of joined and ready more than once.
				if (!joinedAndReadyMembers.contains(entry.adv.getName())) {
					// note notification.
					joinedAndReadyMembers.add(entry.adv.getName());
					LOG.log(Level.FINEST, MessageFormat.format("Handling Ready Event for peer :{0}", entry.adv.getName()));
					final ClusterViewEvent cvEvent = masterNode.sendReadyEventView(entry.adv);
					manager.getClusterViewManager().notifyListeners(cvEvent);
				}
			}
		}
	}

	private void handleStopEvent(final HealthMessage.Entry entry) {
		LOG.log(Level.FINEST, MessageFormat.format("Handling Stop Event for peer :{0}", entry.adv.getName()));
		short stateByte = PEERSTOPPING;
		if (entry.state.equals(states[CLUSTERSTOPPING])) {
			stateByte = CLUSTERSTOPPING;
		}
		if (entry.adv.getID().equals(masterNode.getMasterNodeID())) {
			// if masternode is resigning, remove master node from view and start discovery
			LOG.log(Level.FINER, MessageFormat.format("Removing master node {0} from view as it has stopped.", entry.adv.getName()));
			removeMasterAdv(entry, stateByte);
			masterNode.resetMaster();
			masterNode.appointMasterNode();
		} else if (masterNode.isMaster() && masterNode.isMasterAssigned()) {
			manager.getClusterViewManager().remove(entry.adv);
			LOG.log(Level.FINE, "Announcing Peer Stop Event of " + entry.adv.getName() + " to group ...");
			final ClusterViewEvent cvEvent;
			if (stateByte == CLUSTERSTOPPING) {
				cvEvent = new ClusterViewEvent(ClusterViewEvents.CLUSTER_STOP_EVENT, entry.adv);
			} else {
				cvEvent = new ClusterViewEvent(ClusterViewEvents.PEER_STOP_EVENT, entry.adv);
			}
			masterNode.viewChanged(cvEvent);
		}
		cleanAllCaches(entry);
	}

	private void cleanAllCaches(HealthMessage.Entry entry) {
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("HealthMonitor.cleanAllCaches : removing pipes and route from cache..." + entry.id);
		}
		manager.getNetworkManager().removePeerID(entry.id);
		synchronized (joinedAndReadyMembers) {
			joinedAndReadyMembers.remove(entry.adv.getName());
		}
	}

	void cleanAllCaches(String memberToken) {
		HealthMessage.Entry entry = null;
		PeerID id = manager.getID(memberToken);
		synchronized (cache) {
			entry = cache.get(id);
		}
		if (entry != null) {
			cleanAllCaches(entry);
		} else {
			synchronized (joinedAndReadyMembers) {
				joinedAndReadyMembers.remove(memberToken);
			}
		}
	}

	private Map<PeerID, HealthMessage.Entry> getCacheCopy() {
		ConcurrentHashMap<PeerID, HealthMessage.Entry> clone = new ConcurrentHashMap<PeerID, HealthMessage.Entry>();
		// fine(" cache object in getCacheCopy() = " + cache);
		// fine(" getCacheCopy => printing main cache contents (size = " + cache.size() + ") ...");
		// print(cache);
		synchronized (cacheLock) {
			for (Map.Entry<PeerID, HealthMessage.Entry> entry : cache.entrySet()) {
				try {
					clone.put(entry.getKey(), (HealthMessage.Entry) entry.getValue().clone());
				} catch (CloneNotSupportedException e) {
					LOG.fine("Exception occurred : " + e);
				}
			}
		}
		// fine(" getCacheCopy => printing clone cache contents (size = " + clone.size() + ")...");
		// print(clone);
		return clone;
	}

	private void print(ConcurrentHashMap<PeerID, HealthMessage.Entry> c) {

		for (Iterator i = c.values().iterator(); i.hasNext();) {
			HealthMessage.Entry e = (HealthMessage.Entry) i.next();
			fine("cache contents => " + e.adv.getName() + " state => " + e.state);
		}

	}

	/**
	 * Reports on the wire the specified state
	 *
	 * @param state specified state can be ALIVE|SLEEP|HIBERNATING|SHUTDOWN|DEAD
	 * @param id destination node ID, if null broadcast to group
	 */
	private void reportMyState(final short state, final PeerID id) {
		if (LOG.isLoggable(Level.FINER)) {
			LOG.log(Level.FINER, MessageFormat.format("Sending state {0} to {1}", states[state], id == null ? "group" : id));
		}
		boolean sent = true;
		IOException ioe = null;
		try {
			Message msg = null;
			if (state == ALIVE) {
				msg = getAliveMessage();
			} else if (state == ALIVEANDREADY) {
				msg = getAliveAndReadyMessage();
			} else {
				msg = createHealthMessage(state);
			}

			// if the master of gms group, add an element to the heartbeat that has latest master view id.
			// instances use this info to calculate if they have missed any master change view events.
			masterNode.addMasterViewSeqId(msg);
			sent = send(id, msg);
		} catch (IOException e) {
			ioe = e;
			sent = false;
		} finally {
			if (!sent) {
				String reason = "";
				MsgSendStats msgSendStats = this.getMsgSendStats(manager.getGroupName());
				if (msgSendStats.reportSendFailed()) {
					if (ioe != null) {
						reason = ioe.getClass().getSimpleName() + ":" + ioe.getLocalizedMessage();
					} else {
						reason = "sent returned false";
					}
					final String target = id == null ? "group " + manager.getGroupName() : "member " + id;
					LOG.log(Level.WARNING, "mgmt.healthmonitor.reportstatefailed", new Object[] { states[state], target, reason });
				}
			}
		}
	}

	private void reportOtherPeerState(final HealthMessage.Entry entry) {
		final Message msg = createHealthMessageForOtherPeer(entry);
		masterNode.addMasterViewSeqId(msg);
		LOG.log(Level.FINEST, MessageFormat.format("Reporting {0} health state as {1}", entry.adv.getName(), entry.state));
		boolean sent = false;
		IOException ioe = null;
		try {
			sent = send(null, msg);
		} catch (IOException e) {
			ioe = e;
			sent = false;
		} finally {
			if (!sent) {
				MsgSendStats msgSendStats = this.getMsgSendStats(manager.getGroupName());
				if (msgSendStats.reportSendFailed()) {
					String reason = "";
					if (ioe != null) {
						reason = ioe.getClass().getSimpleName() + ":" + ioe.getLocalizedMessage();
					} else {
						reason = "sent returned false";
					}
					LOG.log(Level.WARNING, "mgmt.healthmonitor.reportotherstatefailed",
					        new Object[] { entry.adv.getName(), entry.state, manager.getGroupName(), reason });
				}
			}
		}
	}

	/**
	 * Main processing method for the HealthMonitor object
	 */
	public void run() {
		// At start, the starting state is reported. But reporting ready state is dependent on
		// the parent application making the call that it is in the ready state, which is not
		// necessarily deterministic. Hence we move on from starting state to reporting
		// this peer's alive state. For join notification event, the liveness state of a peer
		// would be one of starting or ready or alive (other than peerstopping, etc).
		reportMyState(STARTING, null);
		// System.out.println("Running HealthMonitor Thread at interval :"+actualto);
		if (LOG.isLoggable(Level.CONFIG)) {
			SystemAdvertisement myAdv = manager.getSystemAdvertisement();
			GMSMember member = Utility.getGMSMember(myAdv);
			LOG.config("MySystemAdvertisement(summary): " + member.toString() + " ID:" + myAdv.getID().toString() + " TCP uri(s):" + myAdv.getURIs());
			LOG.config("MySystemAdvertisement(dump)=" + myAdv.toString());
		}
		while (!stop) {
			try {
				synchronized (threadLock) {
					threadLock.wait(timeout);
				}
				if (!stop) {
					if (readyStateComplete) {
						// keep sending READY till receive JoinedAndReady notification from MASTER.
						final short state = JoinedAndReadyReceived ? ALIVEANDREADY : READY;
						reportMyState(state, null);
					} else {
						reportMyState(ALIVE, null);
					}
				}
			} catch (InterruptedException e) {
				stop = true;
				break;
			} catch (Throwable all) {
				LOG.log(Level.WARNING, "mgmt.healthmonitor.threaduncaughtexception", new Object[] { Thread.currentThread().getName(), all });
				if (LOG.isLoggable(Level.INFO)) {
					LOG.log(Level.INFO, "stack trace", all);
				}
			}
		}
	}

	/**
	 * Send a message to a specific node. In case the peerId is null the message is multicast to the group
	 *
	 * @param peerid Peer ID to send massage to
	 * @param msg the message to send
	 * @return boolean <code>true</code> if the message has been sent otherwise <code>false</code>. <code>false</code>. is
	 * commonly returned for non-error related congestion, meaning that you should be able to send the message after waiting
	 * some amount of time.
	 */
	private boolean send(final PeerID peerid, final Message msg) throws IOException {
		MsgSendStats msgSendStat = null;
		boolean sent = false;
		sendStopLock.lock();
		try {
			// the message contains only one messageElement and
			// the healthMessage contains only 1 entry
			Object value = msg.getMessageElement(HEALTHM);
			if (value instanceof HealthMessage) {
				HealthMessage hm = (HealthMessage) value;
				for (HealthMessage.Entry healthEntry : hm.getEntries()) {

					// if stop() has been called before the sendStopLock was acquired
					// then don't send any alive/aliveready/ready messages
					if (stop && (!healthEntry.state.equals(states[CLUSTERSTOPPING]) && !healthEntry.state.equals(states[PEERSTOPPING])
					        && !healthEntry.state.equals(states[STOPPED]))) {
						LOG.fine("HealthMonitor.send()=> not sending the message since HealthMonitor is trying to stop. state = " + healthEntry.state);
						// don't flag this send as a failure to send, it is a graceful stop
						return true;
					}
				}
			} else {
				LOG.log(Level.WARNING, "mgmt.unknownMessage");
			}

			if (peerid != null) {
				// Unicast datagram
				// create a op pipe to the destination peer
				LOG.log(Level.FINER, "Unicasting Message to :" + peerid.toString());
				msgSendStat = getMsgSendStats(peerid.getInstanceName());
				sent = manager.getNetworkManager().send(peerid, msg);
				msgSendStat.sendSucceeded();

			} else {
				msgSendStat = getMsgSendStats(manager.getGroupName());
				sent = manager.getNetworkManager().broadcast(msg);
				msgSendStat.sendSucceeded();
			}
		} catch (IOException io) {
			msgSendStat.sendFailed();
			throw io;
		} finally {
			sendStopLock.unlock();
		}
		return sent;
	}

	/**
	 * Creates both input/output pipes, and starts monitoring service
	 */
	void start() {

		if (!started) {
			LOG.log(Level.FINE, "Starting HealthMonitor");
			// no indoubt peer detector needed for HeatlhMonitor associated with a GMS WatchDog membertype.
			if (!isWatchDog()) {
				manager.getNetworkManager().addMessageListener(this);
				this.healthMonitorThread = new Thread(this, "GMS HealthMonitor for Group-" + manager.getGroupName());
				this.healthMonitorThread.setDaemon(true);
				healthMonitorThread.start();
				inDoubtPeerDetector = new InDoubtPeerDetector();
				inDoubtPeerDetector.start();
			}
			started = true;
		}
	}

	/**
	 * Announces Stop event to all members indicating that this peer will gracefully exit the group. TODO: Make this a
	 * synchronous call or a simulated synchronous call so that responses from majority of members can be collected before
	 * returning from this method
	 *
	 * @param isClusterShutdown boolean value indicating whether this announcement is in the context of a clusterwide
	 * shutdown or a shutdown of this peer only.
	 */
	void announceStop(final boolean isClusterShutdown) {
		if (isWatchDog()) {
			return;
		}
		// System.out.println("Announcing Shutdown");
		LOG.log(Level.FINE, MessageFormat.format("Announcing stop event to group with clusterShutdown set to {0}", isClusterShutdown));
		if (isClusterShutdown) {
			reportMyState(CLUSTERSTOPPING, null);
		} else {
			reportMyState(PEERSTOPPING, null);
		}

	}

	/**
	 * Stops this service
	 *
	 * @param isClusterShutdown true if the cluster is shutting down
	 */
	void stop(boolean isClusterShutdown) {
		sendStopLock.lock();
		try {
			stop = true;
			started = false;
		} finally {
			sendStopLock.unlock();
		}

		announceStop(isClusterShutdown);
		reportMyState(STOPPED, null);

		if (isConnectedPool != null) {
			isConnectedPool.shutdownNow();
		}

		// acquire lock again since dont want send() to send messages while stop()
		// is clearing the caches
		sendStopLock.lock();
		try {
			LOG.log(Level.FINE, "Stopping HealthMonitor");
			final Thread tmpThread = healthMonitorThread;
			healthMonitorThread = null;
			if (tmpThread != null) {
				tmpThread.interrupt();
			}
			inDoubtPeerDetector.stop();
		} finally {
			sendStopLock.unlock();
		}
		manager.getNetworkManager().removeMessageListener(this);
	}

	private HealthMessage updateHealthMessage(final HealthMessage hm) throws IOException {
		if (hm != null) {
			long currentTime = System.currentTimeMillis();
			for (HealthMessage.Entry entry : hm.getEntries()) {
				entry.timestamp = currentTime;
			}
		}
		return hm;
	}

	private void notifyLocalListeners(final String state, final SystemAdvertisement adv) {
		if (state.equals(states[INDOUBT])) {
			manager.getClusterViewManager().setInDoubtPeerState(adv);
		} else if (state.equals(states[ALIVE])) {
			manager.getClusterViewManager().setPeerNoLongerInDoubtState(adv);
		} else if (state.equals(states[ALIVEANDREADY])) {
			manager.getClusterViewManager().setPeerNoLongerInDoubtState(adv);
		} else if (state.equals(states[CLUSTERSTOPPING])) {
			manager.getClusterViewManager().setClusterStoppingState(adv);
		} else if (state.equals(states[PEERSTOPPING])) {
			manager.getClusterViewManager().setPeerStoppingState(adv);
		} else if (state.equals(states[READY])) {
			manager.getClusterViewManager().setPeerReadyState(adv);
		} /*
		   * else if (state.equals(states[ALIVE_IN_ISOLATION])) { manager.getClusterViewManager().setInIsolationState(adv); }
		   */
	}

	/**
	 *
	 * @param peerID is the peer id
	 * @param threshold is a positive value if the user wants to look at the caller's local cache to get the state
	 * @param timeout is a positive value if the user desires to make a network call directly to the member whose state it
	 * wants if both the above parameters are specified, then fisrt attempt is to get the state from the local cache. If it
	 * comes back as UNKNOWN, then another attempt is made via LWR multicast to get the state directly from the concerned
	 * member.
	 * @return state
	 */
	public String getMemberState(PeerID peerID, long threshold, long timeout) {
		if (peerID == null) {
			throw new IllegalArgumentException("getMemberState parameter PeerID must be non-null");
		}
		if (peerID.equals(localPeerID)) {
			// handle getting your own member state
			return getStateFromCache(peerID);
		}
		// if threshold is a positive value and there is no timeout specified i.e.
		// timeout is a negative value or simply 0

		if (threshold > 0 && timeout <= 0) {
			return getMemberStateFromHeartBeat(peerID, threshold);
		} else if (threshold <= 0 && timeout > 0) {
			return getMemberStateViaLWR(peerID, timeout);
		} else {
			// in this case, threshold and timeout are both either positive or both set to 0
			// if state is UNKNOWN, it means that the last heartbeat was received longer than the specified
			// threshold. So we make a network call to that instance to get its state
			if (timeout == 0 && threshold == 0) {
				timeout = DEFAULT_MEMBERSTATE_TIMEOUT;
				threshold = defaultThreshold;
			}
			String state = getMemberStateFromHeartBeat(peerID, threshold);
			if (state.equals(states[UNKNOWN])) {
				return getMemberStateViaLWR(peerID, timeout);
			} else
				return state;
		}
	}

	public String getMemberStateFromHeartBeat(final PeerID peerID, long threshold) {
		if (threshold <= 0) {
			threshold = defaultThreshold;
		}
		HealthMessage.Entry entry;
		synchronized (cacheLock) {
			entry = cache.get((PeerID) peerID);
		}
		if (entry != null) {
			// calculate if the timestamp of the entry and the current time gives a delta that is <= threshold
			if (System.currentTimeMillis() - entry.timestamp <= threshold) {
				return entry.state;
			} else {
				return states[UNKNOWN]; // this means that either the heartbeat from that instance has'nt come in yet so the state in the cache could be stale
				// so rather than give a stale state, give it as UNKNOWN
			}
		} else {
			return states[UNKNOWN];
		}
	}

	static class MemberStateResult {
		final Object lock = new Object();
		String memberState = null;
	}

	public String getMemberStateViaLWR(final PeerID peerID, long timeout) {
		if (peerID.equals(localPeerID)) {
			// handle getting your own member state
			return getStateFromCache(peerID);
		}

		// never allow a non-timed wait
		if (timeout <= 0) {
			return getStateFromCache(peerID);
		}

		if (!manager.getClusterViewManager().containsKey(peerID)) {
			// avoid race condition of checking alive and ready state for a member that has not joined the cluster yet.
			// speeds up start-cluster case when not all instances are up yet.
			return states[UNKNOWN];
		}

		// check if there are any outstanding calls, if so just wait for reply
		MemberStateResult result = memberStateResults.get(peerID);
		boolean sent = false;
		boolean ioe = false;
		if (result == null) {
			// no outstanding calls at this time.
			result = new MemberStateResult();
			MemberStateResult prevResult = memberStateResults.putIfAbsent(peerID, result);
			if (prevResult != null) {
				// some other thread put a result into result cache, let that thread send the query, we will wait for the reply
				result = prevResult;
				sent = true;
			} else {
				// only thread that puts result into result cache will send a message to peerID
				// instead send a query to that instance to get the most up-to-date state
				if (LOG.isLoggable(Level.FINER)) {
					LOG.finer("getMemberStateViaLWR send query to " + peerID.toString());
				}
				Message msg = createMemberStateQuery();

				// send it via LWRMulticast
				try {
					sent = mcast.send((PeerID) peerID, msg);
				} catch (IOException e) {
					ioe = true;
					LOG.log(Level.FINE,
					        "Could not send the LWR Multicast message to get the member state of " + peerID.toString() + " IOException : " + e.getMessage());
				}
				if (!sent && !ioe) {
					LOG.log(Level.FINE, "failed to send LWRMulticast message, send returned false");
				}
			}
		}

		synchronized (result.lock) {
			try {
				if (sent && result.memberState == null) { // check for result coming back
					result.lock.wait(timeout);
				}
			} catch (InterruptedException e) {
				LOG.log(Level.FINE, "wait() was interrupted : " + e.getMessage());
			}
		}
		if (result.memberState != null) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.finer("getMemberStateViaLWR got state=" + result.memberState + " id=" + peerID);
			}
			return result.memberState;
		} else {
			// timed out, remove result from result cache.
			memberStateResults.remove(peerID, result);
			String state = states[UNKNOWN];
			if (LOG.isLoggable(Level.FINER)) {
				LOG.finer("getMemberStateViaLWR timeout id=" + peerID);
			}
			return state;
		}
	}

	String getStateFromCache(final PeerID peerID) {
		HealthMessage.Entry entry;
		final String state;
		entry = cache.get((PeerID) peerID);
		if (entry != null) {
			state = entry.state;
		} else {
			if (((PeerID) peerID).equals(localPeerID)) {
				if (!started) {
					state = states[STARTING];
				} else if (readyStateComplete) {
					state = states[ALIVEANDREADY];
				} else {
					state = states[ALIVE];
				}
			} else {
				entry = cache.get((PeerID) peerID);
				if (entry != null) {
					state = entry.state;
				} else {
					if (manager.getClusterViewManager().containsKey(peerID)) {
						state = states[STARTING];// we assume that the peer is in starting state hence its state is not yet known in this peer
					} else {
						state = states[UNKNOWN];
					}
				}
			}
		}
		return state;
	}

	public boolean addHealthEntryIfMissing(final SystemAdvertisement adv) {
		final PeerID id = adv.getID();
		synchronized (cacheLock) {
			HealthMessage.Entry entry = cache.get(id);
			if (entry == null) {
				final long BEFORE_FIRST_HEALTHMESSAGE_SEQ_ID = 0;
				entry = new HealthMessage.Entry(adv, states[STARTING], BEFORE_FIRST_HEALTHMESSAGE_SEQ_ID);
				HealthMessage.Entry result = cache.putIfAbsent(id, entry);
				return result == null;
			} else {
				return false;
			}
		}
	}

	public void reportJoinedAndReadyState() {
		// if I am the master send out a new view with ready event but wait for master discovery to be over.Also send out a
		// health message
		// with state set to READY. Recipients of health message would only update their health state cache.
		// Master Node discovery completion and assignment is necessary for the ready state of a peer to be sent out as an event
		// with a view.
		// if I am not the master, report my state to the group as READY. MasterNode will send out a
		// JOINED_AND_READY_EVENT view to the group on receipt of this health state message.

		if (isWatchDog()) {
			return;
		}

		if (masterNode.isDiscoveryInProgress()) {
			synchronized (masterNode.discoveryLock) {
				try {
					masterNode.discoveryLock.wait();
					LOG.log(Level.FINEST, "reportJoinedAndReadyState() waiting for masternode discovery to finish...");
				} catch (InterruptedException e) {
					LOG.log(Level.FINEST, "MasterNode's DiscoveryLock Thread is interrupted " + e);
				}
			}
		}
		readyStateComplete = true;
		if (masterNode.isMaster() && masterNode.isMasterAssigned()) {
			// since is master node and sending joined and ready event, just set joined and ready now.
			setJoinedAndReadyReceived();
			if (LOG.isLoggable(Level.FINEST)) {
				LOG.log(Level.FINEST, "Sending Ready Event View for " + manager.getSystemAdvertisement().getName());
			}
			ClusterViewEvent cvEvent = masterNode.sendReadyEventView(manager.getSystemAdvertisement());
			if (LOG.isLoggable(Level.FINEST)) {
				LOG.log(Level.FINEST, MessageFormat.format("Notifying Local listeners about " + "Joined and Ready Event View for peer :{0}",
				        manager.getSystemAdvertisement().getName()));
			}
			manager.getClusterViewManager().notifyListeners(cvEvent);
		}
		if (LOG.isLoggable(Level.FINE)) {
			LOG.log(Level.FINE, "mgmt.heatlhmonitor.ready");
		}
		reportMyState(READY, null);
	}

	/**
	 * Detects suspected failures and then delegates final decision to FailureVerifier
	 *
	 * @author : Shreedhar Ganapathy
	 */
	private class InDoubtPeerDetector implements Runnable {
		private Thread fvThread = null;

		void start() {
			final String GROUPNAME = manager.getGroupName();
			failureDetectorThread = new Thread(this, "GMS InDoubtPeerDetector Thread for Group-" + GROUPNAME);
			failureDetectorThread.setDaemon(true);
			LOG.log(Level.FINE, "Starting InDoubtPeerDetector Thread");
			failureDetectorThread.start();
			FailureVerifier fverifier = new FailureVerifier();
			fvThread = new Thread(fverifier, "GMS FailureVerifier Thread for Group-" + GROUPNAME);
			fvThread.setDaemon(true);
			LOG.log(Level.FINE, "Starting FailureVerifier Thread");
			fvThread.start();
		}

		void stop() {
			final Thread tmpThread = failureDetectorThread;
			failureDetectorThread = null;
			if (tmpThread != null) {
				tmpThread.interrupt();
			}

			// Interrupt failure verifier rather than notify.
			// Getting verifierLock deadlocked with FV.assignAndReportFailure trying to acquire stopSendLock.
			final Thread tmpFVThread = fvThread;
			fvThread = null;
			if (tmpFVThread != null) {
				tmpFVThread.interrupt();
			}

			// Commented out since resulted in deadlock with FailureVerifier thread already holding verifierLock and trying to
			// acquire sendStopLock to send a message.
//            synchronized (verifierLock) {
//                verifierLock.notify();<
//            }
		}

		public void run() {
			while (!stop) {
				try {
					// System.out.println("InDoubtPeerDetector failureDetectorThread waiting for :"+timeout);
					// wait for specified timeout or until woken up
					synchronized (indoubtthreadLock) {
						indoubtthreadLock.wait(timeout);
					}
					// LOG.log(Level.FINEST, "Analyzing cache for health...");
					// get the copy of the states cache
					if (!manager.isStopping()) {
						processCacheUpdate();
					}
				} catch (InterruptedException ex) {
					LOG.log(Level.FINEST, "InDoubtPeerDetector Thread stopping as it is now interrupted :" + ex.getLocalizedMessage());
					break;
				} catch (Throwable all) {
					LOG.log(Level.FINE, "Uncaught Throwable in failureDetectorThread " + Thread.currentThread().getName() + ":" + all, all);
				}
			}

		}

		/**
		 * computes the number of heart beats missed based on an entry's timestamp
		 *
		 * @param cacheSnapShotTime time that a copy of the health cache was made
		 * @param entry the Health entry
		 * @return the number heart beats missed
		 */
		int computeMissedBeat(long cacheSnapShotTime, HealthMessage.Entry entry) {
			return (int) ((cacheSnapShotTime - entry.timestamp) / timeout);
		}

		private void processCacheUpdate() {
			final Map<PeerID, HealthMessage.Entry> cacheCopy = getCacheCopy();
			final long cacheSnapShotTime = System.currentTimeMillis();
			// for each peer id
			for (HealthMessage.Entry entry : cacheCopy.values()) {
				// don't check for isConnected with your own self
				// don't check when state is not a running state.
				if (!entry.id.equals(manager.getSystemAdvertisement().getID())) {
					if (entry.state.equals(states[STARTING]) || entry.state.equals(states[ALIVE]) || entry.state.equals(states[READY])
					        || entry.state.equals(states[ALIVEANDREADY])) {
						if (LOG.isLoggable(Level.FINER)) {
							LOG.finer("processCacheUpdate : " + entry.adv.getName() + " 's state is " + entry.state);
						}
						// if there is a record, then get the number of
						// retries performed in an earlier iteration
						try {
							determineInDoubtPeers(entry, cacheSnapShotTime);
						} catch (NumberFormatException nfe) {
							if (LOG.isLoggable(Level.FINER)) {
								nfe.printStackTrace();
							}
							LOG.log(Level.WARNING, "mgmt.healthmonitor.timestampconversionexception", nfe.getLocalizedMessage());
						}
					}
				}
			}
		}

		private void determineInDoubtPeers(final HealthMessage.Entry entry, long cacheSnapShotTime) {

			// if current time exceeds the last state update timestamp from this peer id, by more than the
			// the specified max timeout
			if (!stop) {

				final boolean processInDoubt = canProcessInDoubt(entry);
				if (processInDoubt && LOG.isLoggable(Level.FINER)) {
					LOG.log(Level.FINER, MessageFormat.format("For instance = {0}; last recorded heart-beat = {1}ms ago, heart-beat # {2} out of a max of {3}",
					        entry.adv.getName(), (cacheSnapShotTime - entry.timestamp), computeMissedBeat(cacheSnapShotTime, entry), maxMissedBeats));
				}

				// Optimization. Avoid calling isConnected() (network call) when not authorized to declare entry as indoubt.
				if (processInDoubt && computeMissedBeat(cacheSnapShotTime, entry) >= maxMissedBeats && !isConnected(entry)) {
					LOG.log(Level.FINER, "Designating InDoubtState for " + entry.adv.getName());
					designateInDoubtState(entry);
					// delegate verification to Failure Verifier
					LOG.log(Level.FINER, "Notifying FailureVerifier for " + entry.adv.getName());
					synchronized (verifierLock) {
						outstandingFailureToVerify = true;
						verifierLock.notify();
					}
					LOG.log(Level.FINER, "Done Notifying FailureVerifier for " + entry.adv.getName());

				}
			}
		}

		private boolean canProcessInDoubt(final HealthMessage.Entry entry) {
			boolean canProcessIndoubt = false;

			// dont suspect self
			if (!entry.id.equals(localPeerID)) {
				if (masterNode.getMasterNodeID().equals(entry.id)) {
					canProcessIndoubt = true;
				} else if (masterNode.isMaster()) {
					canProcessIndoubt = true;
				}
			}
			return canProcessIndoubt;
		}

		private void designateInDoubtState(final HealthMessage.Entry entry) {
			HealthMessage.Entry reportEntry = null;
			fine(" in designateInDoubtState, going to set the state of " + entry.adv.getName() + " to indoubt");
			synchronized (cacheLock) {
				entry.state = states[INDOUBT];

				// make a copy before placing back in cache.
				reportEntry = new HealthMessage.Entry(entry);
				cache.put(entry.id, entry);
			}
			// fine(" after putting into cache " + cache + " , contents are :-");
			// print(cache);
			if (masterNode.isMaster()) {
				// do this only when masternode is not suspect.
				// When masternode is suspect, all members update their states
				// anyways so no need to report
				// Send group message announcing InDoubt State
				fine("Sending INDOUBT state message about member: " + entry.adv.getName() + " to the group: " + manager.getGroupName());
				// fix glassfish issue 9055. entry sent in health message should not have use Master's health message sequence id.
				// health message sequence id's should only be used from instance that the health message is reporting about.
				reportOtherPeerState(reportEntry);
			}
			LOG.log(Level.FINEST, "Notifying Local Listeners of designated indoubt state for " + entry.adv.getName());
			notifyLocalListeners(reportEntry.state, reportEntry.adv);
		}
	}

	private class FailureVerifier implements Runnable {
		static private final long BUFFER_TIMEOUT_MS = 500;

		public void run() {
			try {
				while (!stop) {
					LOG.log(Level.FINE, "FV: Entering verifierLock Wait....");
					synchronized (verifierLock) {
						if (!outstandingFailureToVerify) {
							verifierLock.wait();
						}
						outstandingFailureToVerify = false;
					}
					LOG.log(Level.FINE, "FV: Woken up from verifierLock Wait by a notify ....");
					if (!stop) {
						LOG.log(Level.FINE, "FV: Calling verify() ....");
						verify();
						LOG.log(Level.FINE, "FV: Done verifying ....");
					}
				}

			} catch (InterruptedException ex) {
				LOG.log(Level.FINE, MessageFormat.format("failure Verifier Thread stopping as it is now interrupted: {0}", ex.getLocalizedMessage()));
				print(cache);
			}
		}

		void verify() throws InterruptedException {
			// wait for the specified timeout for verification
			Thread.sleep(verifyTimeout + BUFFER_TIMEOUT_MS);
			HealthMessage.Entry entry;
			for (HealthMessage.Entry entry1 : getCacheCopy().values()) {
				entry = entry1;
				LOG.log(Level.FINE, "FV: Verifying state of " + entry.adv.getName() + " state = " + entry.state);
				if (entry.state.equals(states[INDOUBT]) && !isConnected(entry)) {
					LOG.log(Level.FINE, "FV: Assigning and reporting failure ....");
					assignAndReportFailure(entry);
				}
			}
		}
	}

	private void assignAndReportFailure(final HealthMessage.Entry entry) {
		HealthMessage.Entry deadEntry = null;
		if (entry != null) {
			synchronized (cacheLock) {
				// before updating cache that entry is dead, make sure that no other thread has concurrently done this.
				HealthMessage.Entry lastCheck = cache.get(entry.id);
				if (lastCheck == null || lastCheck.isState(DEAD)) {
					// another thread has already called assignedAndReportFailure. There exist 2 ways to declare an instance dead.
					// The FailureVerifier is one thread and processWatchdogNotification is second way. Ensure only one thread ever runs
					// this method.
					// TBD: change back to FINE before checkin.
					String deadTime = lastCheck == null ? "" : MessageFormat.format(" at {0,time,full} on {0,date}", new Date(lastCheck.timestamp));
					LOG.log(Level.INFO, "mgmt.healthmonitor.alreadydead", new Object[] { entry.id, deadTime });
					return;
				}
				// master is generating a DEAD health entry on behalf of DEAD instance.
				// DO NOT bump the HM seqid just in case the master is declaring an instance dead and it is only
				// isolated by a network failure. When isolated instance rejoins cluster, its HM seqid will still
				// be this value, so keep it same now. Part of fix for BugDB 13375653.
				deadEntry = new HealthMessage.Entry(lastCheck.adv, states[DEAD], lastCheck.getSeqID());
				cache.put(lastCheck.id, deadEntry);
			}
			if (LOG.isLoggable(Level.FINE)) {
				fine(" assignAndReportFailure => going to put into cache " + entry.adv.getName() + " state is " + entry.state);
			}
			// fine(" after putting into cache " + cache + " , contents are :-");
			// print(cache);
			if (masterNode.isMaster()) {
				LOG.log(Level.FINE, MessageFormat.format("Reporting Failed Node {0}", entry.id.toString()));
				reportOtherPeerState(deadEntry);
			}
			final boolean masterFailed = (masterNode.getMasterNodeID()).equals(entry.id);
			if (masterNode.isMaster() && masterNode.isMasterAssigned()) {
				LOG.log(Level.FINE, MessageFormat.format("Removing System Advertisement :{0} for name {1}", entry.id.toString(), entry.adv.getName()));
				manager.getClusterViewManager().remove(entry.adv);
				LOG.log(Level.FINE, MessageFormat.format("Announcing Failure Event of {0} for name {1}...", entry.id, entry.adv.getName()));
				final ClusterViewEvent cvEvent = new ClusterViewEvent(ClusterViewEvents.FAILURE_EVENT, entry.adv);
				masterNode.viewChanged(cvEvent);
			} else if (masterFailed) {
				// remove the failed node
				LOG.log(Level.FINE, MessageFormat.format("Master Failed. Removing System Advertisement :{0} for master named {1}", entry.id.toString(),
				        entry.adv.getName()));
				manager.getClusterViewManager().remove(entry.adv);
				masterNode.resetMaster();
				masterNode.appointMasterNode();
				if (masterNode.isMaster() && masterNode.isMasterAssigned()) {
					LOG.log(Level.FINE, MessageFormat.format("Announcing Failure Event of {0} for name {1}...", entry.id, entry.adv.getName()));
					final ClusterViewEvent cvEvent = new ClusterViewEvent(ClusterViewEvents.FAILURE_EVENT, entry.adv);
					masterNode.viewChanged(cvEvent);
				}
			}
			cleanAllCaches(entry);
		}
	}

	private void removeMasterAdv(HealthMessage.Entry entry, short state) {
		manager.getClusterViewManager().remove(entry.adv);
		if (entry.adv != null) {
			switch (state) {
			case DEAD:
				if (LOG.isLoggable(Level.FINER)) {
					LOG.log(Level.FINER, "FV: Notifying local listeners of Failure of " + entry.adv.getName());
				}
				manager.getClusterViewManager().notifyListeners(new ClusterViewEvent(ClusterViewEvents.FAILURE_EVENT, entry.adv));
				break;
			case PEERSTOPPING:
				if (LOG.isLoggable(Level.FINER)) {
					LOG.log(Level.FINER, "FV: Notifying local listeners of Shutdown of " + entry.adv.getName());
				}
				manager.getClusterViewManager().notifyListeners(new ClusterViewEvent(ClusterViewEvents.PEER_STOP_EVENT, entry.adv));
				break;
			case CLUSTERSTOPPING:
				if (LOG.isLoggable(Level.FINER)) {
					LOG.log(Level.FINER, "FV: Notifying local listeners of Cluster_Stopping of " + entry.adv.getName());
				}
				manager.getClusterViewManager().notifyListeners(new ClusterViewEvent(ClusterViewEvents.CLUSTER_STOP_EVENT, entry.adv));
				break;
			default:
				if (LOG.isLoggable(Level.FINEST)) {
					LOG.log(Level.FINEST, MessageFormat.format("Invalid State for removing adv from view {0}", state));
				}
			}
		} else {
			LOG.log(Level.WARNING, "mgmt.healthmonitor.removemasteradvfail", new Object[] { states[state], entry.id });
		}
	}

	/**
	 * This method is for designating myself in network isolation if my network interface is not up. It does not matter if I
	 * am the master or not.
	 */
	/*
	 * private void designateInIsolationState(final HealthMessage.Entry entry) {
	 * 
	 * entry.state = states[ALIVE_IN_ISOLATION]; cache.put(entry.id, entry); LOG.log(Level.FINE,
	 * "Sending ALIVE_IN_ISOLATION state message about node ID: " + entry.id + " to the cluster...");
	 * reportMyState(ALIVE_IN_ISOLATION, entry.id); LOG.log(Level.FINE,
	 * "Notifying Local Listeners of designated ALIVE_IN_ISOLATION state for " + entry.adv.getName());
	 * notifyLocalListeners(entry.state, entry.adv); }
	 */

	static public class PeerMachineConnectionResult {
		public Boolean isConnectionUp = null;
		public SocketAddress connectionUpSocketAddress = null;
		public AtomicBoolean completed = new AtomicBoolean(false);
		public long startTime = 0;

		public boolean isConnectionUp() {
			return isConnectionUp != null && isConnectionUp.booleanValue();
		}

		PeerMachineConnectionResult() {
			if (LOG.isLoggable(Level.FINE)) {
				startTime = System.currentTimeMillis();
			}
		}
	}

	public boolean isConnected(HealthMessage.Entry entry) {
		boolean result = false;
		List<URI> list = entry.adv.getURIs();
		List<CheckConnectionToPeerMachine> connections = new ArrayList<CheckConnectionToPeerMachine>(list.size());
		AtomicInteger outstandingConnections = new AtomicInteger(list.size());
		PeerMachineConnectionResult checkConnectionsResult = new PeerMachineConnectionResult();

		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("HealthMonitor.isConnected() peerMachine=" + entry.adv.getName() + " number of network interfaces=" + outstandingConnections);
		}
		for (URI uri : list) {
			if (!checkConnectionsResult.completed.get()) {

				// optimization. Short-circuit checking all uris after first connection is detected up.
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("Checking for machine status for network interface : " + uri.toString());
				}
				CheckConnectionToPeerMachine connectToPeer = new CheckConnectionToPeerMachine(entry, uri.getHost(), outstandingConnections,
				        (int) failureDetectionTCPTimeout, checkConnectionsResult);
				connections.add(connectToPeer);

				// starts checking if it can create a socket connection to peer using uri in another thread.
				connectToPeer.setFuture(isConnectedPool.submit(connectToPeer));
			}
		}

		try {
			// wait until one network interface is confirmed to be up or all network interface addresses are confirmed to be down.
			synchronized (checkConnectionsResult) {
				if (!checkConnectionsResult.completed.get()) {
					long startTime = 0;
					if (LOG.isLoggable(Level.FINE)) {
						startTime = System.currentTimeMillis();
					}
					checkConnectionsResult.wait(failureDetectionTCPTimeout);
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("waited " + (System.currentTimeMillis() - startTime) + " for CheckConnectionsToPeer to complete. failureDetectionTCPTimeout="
						        + failureDetectionTCPTimeout);
					}
				} else {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("check connection completed with no waiting");
					}
				}
			}
		} catch (InterruptedException e) {
			fine("InterruptedException occurred..." + e.getMessage(), new Object[] { e });
		}

		if (checkConnectionsResult.isConnectionUp()) {
			long startTime = 0;
			if (LOG.isLoggable(Level.FINE)) {
				startTime = System.currentTimeMillis();
			}
			result = manager.getNetworkManager().isConnected(entry.id);
			if (LOG.isLoggable(Level.FINE)) {
				fine("routeControl.isConnected() for " + entry.adv.getName() + " is => " + result + " call elapsed time="
				        + (System.currentTimeMillis() - startTime) + "ms");
			}
		}

		// cleanup outstanding tasks
		// also be sure to cancel any outstanding future tasks. (Could be hung trying to create a socket to a failed network
		// interface)
		boolean canceled = false;
		for (CheckConnectionToPeerMachine connection : connections) {
			try {
				Future future = connection.getFuture();
				// if the task has completed after waiting for the
				// specified timeout
				if (!future.isDone()) {

					// interrupt the thread which is still running the task submitted to it.
					// chances are that the thread is hung on socket creation to a failed network interface.
					future.cancel(true);
					canceled = true;
				}
			} catch (Throwable t) {
				// ignore. must iterate over all connections.
			}
		}
		if (canceled) {
			// be sure to purge since there was one or more cancelled future threads.
			isConnectedPool.purge();
		}
		String machineStatus = result ? "up!" : "down!";
		if (LOG.isLoggable(Level.FINE)) {
			fine("HealthMonitor.isConnected(): Peer Machine for " + entry.adv.getName() + " is " + machineStatus + " computeTime="
			        + (System.currentTimeMillis() - checkConnectionsResult.startTime) + "ms");
		}
		return result;
	}

	// This is the Callable Object that gets executed by the ExecutorService
	private class CheckConnectionToPeerMachine implements Callable<Object> {
		HealthMessage.Entry entry;
		String address;
		Boolean connectionIsUp;
		boolean running;
		Future future;
		AtomicInteger outstandingConnectionChecks;
		PeerMachineConnectionResult result;
		final int socketConnectTimeout;

		CheckConnectionToPeerMachine(HealthMessage.Entry entry, String address, AtomicInteger outstanding, int socketConnectTimeout,
		        PeerMachineConnectionResult result) {
			this.entry = entry;
			this.address = address;
			this.connectionIsUp = null; // unknown
			this.running = true;
			this.outstandingConnectionChecks = outstanding;
			this.result = result;
			this.socketConnectTimeout = socketConnectTimeout;
		}

		void setFuture(Future future) {
			this.future = future;
		}

		Future getFuture() {
			return future;
		}

		boolean isConnectionUp() {
			return connectionIsUp != null && connectionIsUp;
		}

		boolean completedComputation() {
			return !running;
		}

		public Object call() {
			SocketAddress siaddr = null;
			Socket socket = null;
			try {
				if (LOG.isLoggable(Level.FINE)) {
					fine("Attempting to connect to a socket at " + address + ":" + failureDetectionTCPPort + " timeout=" + socketConnectTimeout);
				}
				socket = new Socket();
				siaddr = new InetSocketAddress(address, failureDetectionTCPPort);
				socket.connect(siaddr, socketConnectTimeout);
				if (LOG.isLoggable(Level.FINE)) {
					fine("Socket created at " + address + ":" + failureDetectionTCPPort);
				}

				// for whatever reason a socket got created, finally block will close it and return true
				// i.e. this instance was able to create a socket on that machine so it is up
				connectionIsUp = Boolean.TRUE;
			} catch (SocketTimeoutException toe) {
				connectionIsUp = Boolean.FALSE;
				LOG.fine("socket connection to " + siaddr + " timed out. " + toe.getLocalizedMessage());
			} catch (InterruptedIOException intioe) {
				connectionIsUp = null;
			} catch (IOException e) {
				if (LOG.isLoggable(Level.FINE)) {
					fine("IOException occurred while trying to connect to peer " + entry.adv.getName() + "'s machine : " + e.getMessage(), new Object[] { e });
				}
				if (e.getMessage().trim().contains(CONNECTION_REFUSED)) {
					connectionIsUp = Boolean.TRUE;
				} else {
					connectionIsUp = Boolean.FALSE;
				}
			} catch (Throwable t) {
			} finally {
				running = false;
				outstandingConnectionChecks.decrementAndGet();
				if (socket != null) {
					try {
						socket.close();
					} catch (IOException e) {
						fine("Could not close the socket due to " + e.getMessage());
					} catch (Throwable t) {
					}
				}

				if (isConnectionUp() || outstandingConnectionChecks.get() <= 0) {

					// completed computation, notify caller to proceed.
					// either one network interface has been verified working OR
					// all network interfaces have been confirmed to not be connectable
					boolean completed = false;
					synchronized (result) {
						if (!result.completed.get()) {
							result.isConnectionUp = Boolean.valueOf(isConnectionUp());
							if (result.isConnectionUp) {
								result.connectionUpSocketAddress = siaddr;
							}
							completed = true;
							result.completed.set(true);
							result.notifyAll();
						}
					}
					if (completed) {
						if (LOG.isLoggable(Level.FINE)) {
							fine("completed computation that one of the network interfaces is up.  isConnectionUp=" + isConnectionUp()
							        + " outstandingNetworkInterfaceChecks =" + outstandingConnectionChecks.get());
						}
					}
				}
				return connectionIsUp;
			}
		}
	}

	boolean isWatchDog() {
		return manager.isWatchdog();
	}

	public void announceWatchdogObservedFailure(String failedMemberToken) {
		IOException ioe = null;
		Message msg = createWatchdogNotification(failedMemberToken);
		boolean sent = false;
		try {
			sent = send(null, msg);
		} catch (IOException io) {
			ioe = io;
		} finally {
			if (!sent) {
				String reason = "";
				MsgSendStats msgSendStats = this.getMsgSendStats(manager.getGroupName());
				if (msgSendStats.reportSendFailed()) {
					if (ioe != null) {
						reason = ioe.getClass().getSimpleName() + ":" + ioe.getLocalizedMessage();
					} else {
						reason = "send returned false";
					}
					LOG.log(Level.WARNING, "mgmt.healthmonitor.failedwatchdognotify", new Object[] { failedMemberToken, manager.getGroupName(), reason });

				}
			}
		}
	}

	public void setJoinedAndReadyReceived() {
		JoinedAndReadyReceived = true;
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("JoinedAndReady notification received from master, set JoinedAndReadyReceived to true for member:" + manager.getInstanceName());
		}
	}

	public MsgSendStats getMsgSendStats(String memberName) {
		MsgSendStats result = msgSendStats.get(memberName);
		if (result == null) {
			result = new MsgSendStats(memberName);
			MsgSendStats putresult = msgSendStats.putIfAbsent(memberName, result);
			if (putresult != null) {
				result = putresult;
			}
		}
		return result;
	}

	private static class MsgSendStats {
		static final long MAX_DURATION_BETWEEN_FAILURE_REPORT_MS = (60000 * 60) * 2; // two hours between fail to send to an instance.
		String memberName;
		AtomicLong numSends;
		AtomicLong numFailSends;
		AtomicLong lastReportedFailSendTime;
		AtomicLong lastSendTime;

		MsgSendStats(String memberName) {
			this.memberName = memberName;
			numSends = new AtomicLong(0);
			numFailSends = new AtomicLong(0);
			lastReportedFailSendTime = new AtomicLong(-1);
			lastSendTime = new AtomicLong(0);
		}

		MsgSendStats sendSucceeded() {
			long value = numSends.incrementAndGet();
			if (value == Long.MAX_VALUE) {
				numSends.set(1); // reset to avoid overflow.
			}
			lastSendTime.set(System.currentTimeMillis());
			return this;
		}

		void sendFailed() {
			numSends.incrementAndGet();
			numFailSends.incrementAndGet();
		}

		boolean reportSendFailed() {
			if (lastSendTime.get() > lastReportedFailSendTime.get()
			        || (System.currentTimeMillis() - this.lastReportedFailSendTime.get()) > MAX_DURATION_BETWEEN_FAILURE_REPORT_MS) {
				lastReportedFailSendTime.set(System.currentTimeMillis());
				return true;
			} else {
				return false;
			}
		}

		String getFailureReport() {
			return memberName + " (failed to send " + numFailSends + " times)";
		}
	}
}
