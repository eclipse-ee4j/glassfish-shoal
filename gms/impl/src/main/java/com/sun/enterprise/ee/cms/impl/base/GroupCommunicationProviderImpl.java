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

package com.sun.enterprise.ee.cms.impl.base;

import com.sun.enterprise.ee.cms.core.GMSException;
import com.sun.enterprise.ee.cms.core.MemberNotInViewException;
import com.sun.enterprise.ee.cms.core.GMSConstants;
import com.sun.enterprise.ee.cms.impl.common.GMSContextFactory;
import com.sun.enterprise.ee.cms.impl.common.GMSMonitor;
import com.sun.enterprise.ee.cms.logging.GMSLogDomain;
import com.sun.enterprise.ee.cms.spi.GMSMessage;
import com.sun.enterprise.ee.cms.spi.GroupCommunicationProvider;
import com.sun.enterprise.ee.cms.spi.MemberStates;
import com.sun.enterprise.mgmt.*;
import com.sun.enterprise.mgmt.transport.Message;
import com.sun.enterprise.mgmt.transport.MessageIOException;

import java.io.IOException;
import java.io.Serializable;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Hashtable;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implements the GroupCommunicationProvider interface to plug in
 * JxtaClusterManagement layer as a Group Communication Provider for GMS.
 *
 * @author Shreedhar Ganapathy
 *         Date: Jun 26, 2006
 * @version $Revision$
 */
public class GroupCommunicationProviderImpl implements
        GroupCommunicationProvider,
        ClusterViewEventListener,
        ClusterMessageListener {
    private ClusterManager clusterManager;
    private final String groupName;
    private GMSContextImpl ctx;
    private Logger logger = GMSLogDomain.getLogger(GMSLogDomain.GMS_LOGGER);
    private final Logger monitorLogger = GMSLogDomain.getMonitorLogger();
    private GMSMonitor gmsMonitor = null;

    // TBD:  Reintroduce this in future. Comment out unused field for now.
    // private final ExecutorService msgSendPool;
    //private Map<PeerID, CallableMessageSend> instanceCache = new Hashtable<PeerID, CallableMessageSend>();

    public GroupCommunicationProviderImpl(final String groupName) {
        this.groupName = groupName;
        System.setProperty("JXTA_MGMT_LOGGER", logger.getName());
        // TBD: Reintroduce this in future.  Comment unused field for now.
        // msgSendPool = Executors.newCachedThreadPool();
    }

    private GMSContextImpl getGMSContext() {
        if (ctx == null) {
            ctx = (GMSContextImpl) GMSContextFactory.getGMSContext(groupName);
            gmsMonitor = ctx.getGMSMonitor();
        }
        return ctx;
    }

    public void clusterViewEvent(final ClusterViewEvent clusterViewEvent,
                                 final ClusterView clusterView) {
        // TBD: verify okay to delete
        if (!getGMSContext().isShuttingDown()) {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "Received Cluster View Event..." + clusterViewEvent.getEvent().toString() +
                        " from " + clusterViewEvent.getAdvertisement().getName() +
                        " view:" + clusterView.getView().toString());
            }
            final EventPacket ePacket = new EventPacket(clusterViewEvent.getEvent(),
                    clusterViewEvent.getAdvertisement(),
                    clusterView);
            final ArrayBlockingQueue<EventPacket> viewQueue = getGMSContext().getViewQueue();
            try {
                final int remainingCapacity = viewQueue.remainingCapacity();
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, "Adding " + clusterViewEvent.getEvent() + " to viewQueue[size:" + viewQueue.size() + " remaining:" + viewQueue.remainingCapacity() + " ]" +
                            "  for group:" + groupName);
                }
                if (remainingCapacity < 2) {
                    logger.warning("viewQueue for group: " + groupName + " near capacity, remaining capacity is" + remainingCapacity);
                }
                viewQueue.put(ePacket);
                logger.log(Level.FINER,  "Added " + clusterViewEvent.getEvent() + " to viewQueue for group: " + groupName);

            } catch (InterruptedException e) {
                //TODO: Examine all InterruptedException and thread.interrupt cases for better logging.
                logger.log(Level.WARNING, "interruptedexception.occurred",
                        new Object[]{e.getLocalizedMessage()});
            }
        }
    }

    /**
     * Initializes the Group Communication Service Provider with the requisite
     * values of group identity, member(self) identity, and a Map containing
     * recognized and valid configuration properties that can be set/overriden
     * by the employing application. The valid property keys must be specified
     * in a datastructure that is available to the implementation and to GMS.
     *
     * @param memberName       member name
     * @param groupName        group name
     * @param identityMap      valid configuration properties
     * @param configProperties configuration properties
     */
    public void initializeGroupCommunicationProvider(final String memberName,
                                                     final String groupName,
                                                     final Map<String, String> identityMap,
                                                     final Map configProperties) throws GMSException {
        final List<ClusterViewEventListener> cvListeners =
                new ArrayList<ClusterViewEventListener>();
        if (! getGMSContext().isWatchdog()) {
            // don't process cluster view events for WATCHDOG member.
            cvListeners.add(this);
        }
        final List<ClusterMessageListener> cmListeners =
                new ArrayList<ClusterMessageListener>();
        cmListeners.add(this);
        clusterManager = new ClusterManager(groupName,
                memberName,
                identityMap,
                configProperties,
                cvListeners,//View Listener
                cmListeners);//MessageListener

    }

    /**
     * Joins the group using semantics specified by the underlying GCP system
     */
    public void join() {
        logger.log(Level.INFO, "starting cluster " + clusterManager.getGroupName() + " for member:" + clusterManager.getInstanceName());
        clusterManager.start();
    }

    public void announceClusterShutdown(final GMSMessage gmsMessage) {
        try {
            boolean sent = clusterManager.send(null, gmsMessage);
             if (!sent) {
                logger.warning("failed to send announceClusterShutdown to group.  gmsMessage=" + gmsMessage);
             }
        } catch (IOException e) {
            logger.log(Level.WARNING, "ioexception.occurred.cluster.shutdown", new Object[]{e});
        } catch (MemberNotInViewException e) {
            //ignore since this is a broadcast
        }
    }

    /**
     * Demarcate the INITIATION and COMPLETION of group startup.
     *
     * Only useful for an administration utility that statically knows of all members in group and starts them at same time.
     * This API allows for an optimization by GMS clients to know whether GMS Join and JoinedAndReady events are happening
     * as part of group startup or individual instance startups.
     *
     * @param groupName     name of group
     * @param startupState  INITATED, COMPLETED_SUCCESS or COMPLETED_FAILED
     * @param memberTokens  static list of members associated with startupState.  Failed members if state is COMPLETED_FAILED
     */
    public void announceGroupStartup(String groupName,
                                     GMSConstants.groupStartupState startupState,
                                     List<String> memberTokens) {
       clusterManager.groupStartup(startupState, memberTokens);
    }

    /**
     * Leaves the group as a result of a planned administrative action to
     * shutdown.
     */
    public void leave(final boolean isClusterShutdown) {
        clusterManager.stop(isClusterShutdown);
    }

    // the cluster view is in flux when all the members are joining.
    // send synch the distributed state cache WITHOUT checking if instance is in view.
    public boolean sendMessage(final PeerID id, final Serializable msg) {
        boolean sent = false;
        try {
            sent = clusterManager.send(id, msg, false);
        } catch (Throwable t) {
            GMSLogDomain.getDSCLogger().log(Level.FINE, "failed to send DSC message to member:" + id + " cause:" + t.getLocalizedMessage());
        }
        return sent;
    }


    /**
     * Sends a message using the underlying group communication
     * providers'(GCP's) APIs. Requires the users' message to be wrapped into a
     * GMSMessage object.
     *
     * @param targetMemberIdentityToken The member token string that identifies
     *                                  the target member to which this message is addressed.
     *                                  The implementation is expected to provide a mapping
     *                                  the member token to the GCP's addressing semantics.
     *                                  If null, the entire group would receive this message.
     * @param message                   a Serializable object that wraps the user specified
     *                                  message in order to allow remote GMS instances to
     *                                  unpack this message appropriately.
     * @param synchronous               setting true here will call the underlying GCP's api
     *                                  that corresponds to a synchronous message, if
     *                                  available.
     * @throws com.sun.enterprise.ee.cms.core.GMSException the GMS generic expection
     *
     */
    public void sendMessage(final String targetMemberIdentityToken,
                            final Serializable message,
                            final boolean synchronous) throws GMSException, MemberNotInViewException {
        boolean sent = false;
        long duration;
        long startTime = System.currentTimeMillis();
        GMSMessage gmsMessage = null;
        if (gmsMonitor != null && gmsMonitor.ENABLED) {
            if (message instanceof GMSMessage) {
                gmsMessage = (GMSMessage)message;
            }
        }
        try {
            if (targetMemberIdentityToken == null) {
                if (synchronous) {
                    /*
                    Use point-to-point communication with all instances instead of the group-wide (udp) based message.
                    Since we don't have reliable multicast yet, this approach will ensure reliability.
                    Ideally, when a message is sent to the group via point-to-point,
                    the message to each member should be on a separate thread to get concurrency.
                     */
                    List<SystemAdvertisement> currentMemberAdvs = clusterManager.getClusterViewManager().
                            getLocalView().getView();

                    for (SystemAdvertisement currentMemberAdv : currentMemberAdvs) {
                        final PeerID id = currentMemberAdv.getID();
                        final String member = currentMemberAdv.getName();
                        final long INDOUBT_INTERVAL_MS = clusterManager.getHealthMonitor().getIndoubtDuration();
                        MemberStates memberState = getMemberState(member, INDOUBT_INTERVAL_MS, 0);
                        if (memberState == MemberStates.PEERSTOPPING ||
//                          TBD  - should we send message to INDOUBT member? error on side that member is not failed but just busy for now.
//                          memberState == MemberStates.INDOUBT ||
                            memberState == MemberStates.STOPPED || memberState == MemberStates.CLUSTERSTOPPING) {
                            // don't broadcast to departing members
                            if (logger.isLoggable(Level.FINE)) {
                                logger.fine("skipping broadcast message " + message + " to member:" + member + " with state" + memberState);
                            }
                            continue;
                        }

                        //TODO : make this multi-threaded via Callable
                        /* final CallableMessageSend task = getInstanceOfCallableMessageSend(id);
                        logger.fine("Message is = " + message.toString());
                        task.setMessage(message);
                        msgSendPool.submit(task);
                        */
                        logger.log(Level.FINER, "sending message to member: " + currentMemberAdv.getName());
                        try {
                            boolean localSent = clusterManager.send(id, message);
                            if (!localSent) {
                                if (logger.isLoggable(Level.FINE)) {
                                    logger.fine("sendMessage(synchronous=true, to=group) failed to send msg " + message + " to member " + id);
                                }
                            }
                        } catch (MemberNotInViewException e) {
                            if (logger.isLoggable(Level.FINE)) {
                                logger.fine("MemberNotInViewException during synchronous broadcast: " + e.toString());
                            }
                        } catch (MessageIOException mioe) {
                            // this exception is thrown when message size is too big,  discontinue trying to send message and throw this exception to provide feedback to sender.
                            throw new GMSException("message not sent", mioe);
                        } catch (IOException ioe) {
                            // don't allow an exception sending to one instance of the cluster to prevent ptp multicast to all other instances of
                            // of the cluster.  Catch this exception, record it and continue sending to rest of instances in the cluster.
                            if (logger.isLoggable(Level.FINE)) {
                                logger.log(Level.FINE,
                                        "IOException in reliable synchronous ptp multicast sending to instance " + currentMemberAdv.getName() +
                                        ". Perhaps this instance has failed but that has not been detected yet. Peer id=" +
                                        id.toString(),
                                        ioe);
                            }
                        } catch (Throwable t) {
                           // don't allow an exception sending to one instance of the cluster prevent ptp broadcast to all other instances of
                           // of the cluster.  Catch this exception, record it and continue sending to rest of instances in the cluster.
                           if (logger.isLoggable(Level.FINE)) {
                                logger.log(Level.FINE,
                                        "Exception in reliable synchronous ptp multicast sending to instance " + currentMemberAdv.getName() +
                                        ", peer id=" + id.toString(),
                                        t);
                            }
                        }
                    }
                    duration = System.currentTimeMillis() - startTime;
                    monitorDoSend(gmsMessage, duration, true, null);
                } else {
                    sent = clusterManager.send(null, message);//sends to whole group
                    duration = System.currentTimeMillis() - startTime;
                    if (!sent) {
                        GMSException ge = new GMSException("message " + message + " not sent to group, send returned false");
                        monitorDoSend(gmsMessage, duration, false, ge);
                    } else {
                        monitorDoSend(gmsMessage, duration, true, null);
                    }
                }
            } else {
                final PeerID id = clusterManager.getID(targetMemberIdentityToken);
                if (id.equals(PeerID.NULL_PEER_ID)) {
                    logger.log(Level.FINE, "GroupCommunicationProvider.sendMessage(target=" + targetMemberIdentityToken + "): unable to send message: missing mapping from member identifier to network peerid");
                    throw new MemberNotInViewException("No mapping from member identifier:" + targetMemberIdentityToken + " to a network peerid.");
                }
                if (clusterManager.getClusterViewManager().containsKey(id)) {
                    logger.log(Level.FINE, "sending message to PeerID: " + id);
                    sent = clusterManager.send(id, message);
                    duration =  System.currentTimeMillis() - startTime;
                    if (sent) {
                        monitorDoSend(gmsMessage, duration, sent, null);
                    } else {
                        GMSException ge = new GMSException("message " + message + " not sent to " + id + ", send returned false");
                        monitorDoSend(gmsMessage, duration, sent, ge);
                        throw ge;
                    }
                } else {
                    logger.log(Level.FINE, "message not sent to  " + targetMemberIdentityToken +
                            " since it is not in the View");
                    throw new MemberNotInViewException("Member " + targetMemberIdentityToken + " with network peerid:" + id + 
                            " is not in the View anymore. Hence not performing sendMessage operation");
                }
            }
        } catch (IOException e) {
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "exception in sendMessage", e);
            }
            String theMsgOutput = null;
            if (gmsMessage != null) {
                theMsgOutput = gmsMessage.toString();
            }
            duration = startTime - System.currentTimeMillis();
            logSendMessageException("sendMessage msg:" + theMsgOutput + " duration(ms):" + duration + " failed with handled IOException", e);
            monitorDoSend(gmsMessage, duration, false, e);
            throw new GMSException(e);
        }
    }

    protected void monitorDoSend(final GMSMessage msg, final long sendDuration, final boolean sendSucceeded, final Exception e) {
        if (gmsMonitor != null && gmsMonitor.ENABLED  && msg != null) {
                String targetComponent = msg.getComponentName();
                GMSMonitor.MessageStats stats = gmsMonitor.getGMSMessageMonitorStats(targetComponent);
                if (sendSucceeded) {
                    stats.incrementNumMsgsSent();
                    stats.addBytesSent(msg.getMessage().length);
                } else {
                    stats.incrementNumFailMsgSend();
                }
                stats.addSendDuration(sendDuration);
                if (sendDuration > gmsMonitor.getSendWriteTimeout()) {
                    stats.incrementSendWriteTimeout();
                }
        }
    }


    public void sendMessage(Serializable message) throws GMSException, MemberNotInViewException {
        sendMessage(null, message, false);
    }

    private void logSendMessageException(String comment, Throwable t) {
        Logger sendLogger = GMSLogDomain.getSendLogger();
        if (sendLogger.isLoggable(Level.FINE)) {
            if (t == null) {
                sendLogger.fine(comment);
            } else {
                sendLogger.log(Level.FINE, comment + ": sendMessage failed with following internal exception", t);
            }
        }
    }

    /**
     * Returns the address representing the peer identified by this process. The
     * address object is of the type corresponding to the underlying GCP. In
     * this case, the jxta ID of this peer is returned.
     *
     * @return Object - representing this peer's address.
     */
    public Object getLocalAddress() {
        return clusterManager.getSystemAdvertisement().getID();
    }

    /**
     * returns a list of members that are currently alive in the group
     *
     * @return list of current live members
     */
    public List<String> getMembers() {//TODO: BUG. This will result in viewID increment.
        return clusterManager.
                getClusterViewManager().
                getLocalView().
                getPeerNamesInView();
    }

    public boolean isGroupLeader() {
        return clusterManager != null ? clusterManager.isMaster() : false;
    }

    public MemberStates getMemberState(String member, long threshold, long timeout) {
        MemberStates result = MemberStates.UNKNOWN;
        if (clusterManager != null) {
            PeerID id = clusterManager.getID(member);
            if (! id.equals(PeerID.NULL_PEER_ID)) {
                String state = clusterManager.getNodeState(id, threshold, timeout).toUpperCase();
                result = MemberStates.valueOf(state);
            }
        }
        return result;
    }

    public MemberStates getMemberState(final String memberIdentityToken) {
        String result = "UNKNOWN";
        if (clusterManager != null) {
            result =  (clusterManager.getNodeState(clusterManager.getID(memberIdentityToken), 0, 0)).toUpperCase();
        }
        return MemberStates.valueOf(result);
    }

    public String getGroupLeader() {
        String result = "";
        if (clusterManager != null && clusterManager.getClusterViewManager() != null) {
            SystemAdvertisement madv = clusterManager.getClusterViewManager().getMaster();
            if (madv != null) {
                result = madv.getName();
            }
        }
        return result;
    }

    private ArrayBlockingQueue<MessagePacket> msgQueue = null;

    private ArrayBlockingQueue<MessagePacket> getMsgQueue() {
        if (msgQueue == null) {
            msgQueue = getGMSContext().getMessageQueue();
        }
        return msgQueue;
    }

    public void handleClusterMessage(final SystemAdvertisement adv,
                                     final Object message) {
        MessagePacket msgPkt = new MessagePacket(adv, message);
        try {
            //logger.log(Level.FINE, "Received AppMessage Notification, placing in message queue = " + new String(((GMSMessage)message).getMessage()));
            boolean result = getMsgQueue().offer(msgPkt);
            if (result == false) {

                // blocking queue is full.  log how long we were blocked.
                int fullcapacity = getMsgQueue().size();
                long starttime = System.currentTimeMillis();
                try {
                    getMsgQueue().put(msgPkt);
                } finally {
                    long duration = System.currentTimeMillis() - starttime;
                    if (duration > 0) {
                        monitorLogger.info("remote message reception blocked due to incoming message queue being full for " + duration + " ms. Message queue capacity: " + fullcapacity);
                    }
                }
            }
        } catch (InterruptedException e) {
            logger.log(Level.WARNING,
                    MessageFormat.format("Interrupted Exception occured while adding message to Shoal MessageQueue :{0}",
                            e.getLocalizedMessage()));
        }
    }

    public void assumeGroupLeadership() {
        clusterManager.takeOverMasterRole();
    }

    public void setGroupStoppingState() {
        clusterManager.setClusterStopping();
    }

    public void reportJoinedAndReadyState() {
        if (clusterManager == null) {
            throw new IllegalStateException("attempt to report joined and ready when joining group " + groupName + " failed");
        }
        clusterManager.reportJoinedAndReadyState();
    }

    /*
    private CallableMessageSend getInstanceOfCallableMessageSend(ID id) {
        if (instanceCache.get(id) == null) {
            CallableMessageSend c = new CallableMessageSend(id);
            instanceCache.put(id, c);
            return c;
        } else {
            return instanceCache.get(id);
        }
    }
    */

    /**
     * implements Callable.
     * Used for handing off the job of calling sendMessage() method to a ThreadPool.
     * REVISIT
     */
    /*
    private class CallableMessageSend implements Callable<Object> {
        private PeerID member;
        private Serializable msg;

        private CallableMessageSend(final PeerID member) {
            this.member = member;
        }

        public void setMessage(Serializable msg) {
            this.msg = null;
            this.msg = msg;
        }

        public Object call() throws Exception {
            boolean sent = clusterManager.send(member, msg);
            if (!sent) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine("CallableMessageSend failed to send msg " + msg + " to member " + member);
                }
            }
            return null;
        }
    }
    */

    public void announceWatchdogObservedFailure(String serverToken) throws GMSException {
        if (clusterManager == null) {
            logger.severe("cluster manager unexpectedly null");
            return;
        }
        final HealthMonitor hm = clusterManager.getHealthMonitor();
        if (hm == null) {
            logger.severe("clusterManager.getHealthMonitor() unexpectedly null");
            return;
        }
        hm.announceWatchdogObservedFailure(serverToken);
    }

    public boolean isDiscoveryInProgress() {
        return clusterManager.isDiscoveryInProgress();
    }

}

