/*
 * Copyright (c) 1997, 2018 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020 Payara Services Ltd.
 * Copyright (c) 2024 Contributors to the Eclipse Foundation. All rights reserved.
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

import static com.sun.enterprise.ee.cms.core.ServiceProviderConfigurationKeys.DISCOVERY_URI_LIST;
import static com.sun.enterprise.ee.cms.core.ServiceProviderConfigurationKeys.VIRTUAL_MULTICAST_URI_LIST;
import static com.sun.enterprise.mgmt.ClusterViewEvents.ADD_EVENT;
import static com.sun.enterprise.mgmt.ClusterViewEvents.JOINED_AND_READY_EVENT;

import java.io.IOException;
import java.io.Serializable;
import java.text.MessageFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.SortedSet;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.enterprise.ee.cms.core.GMSConstants;
import com.sun.enterprise.ee.cms.core.GMSMember;
import com.sun.enterprise.ee.cms.core.RejoinSubevent;
import com.sun.enterprise.ee.cms.impl.base.CustomTagNames;
import com.sun.enterprise.ee.cms.impl.base.GMSThreadFactory;
import com.sun.enterprise.ee.cms.impl.base.PeerID;
import com.sun.enterprise.ee.cms.impl.base.SystemAdvertisement;
import com.sun.enterprise.ee.cms.impl.base.Utility;
import com.sun.enterprise.ee.cms.impl.common.GMSContext;
import com.sun.enterprise.ee.cms.impl.common.GMSContextFactory;
import com.sun.enterprise.ee.cms.logging.GMSLogDomain;
import com.sun.enterprise.ee.cms.spi.MemberStates;
import com.sun.enterprise.mgmt.transport.Message;
import com.sun.enterprise.mgmt.transport.MessageEvent;
import com.sun.enterprise.mgmt.transport.MessageIOException;
import com.sun.enterprise.mgmt.transport.MessageImpl;
import com.sun.enterprise.mgmt.transport.MessageListener;

/**
 * Master Node defines a protocol by which a set of nodes connected to a JXTA infrastructure group may dynamically
 * organize into a group with a determinically self elected master. The protocol is composed of a JXTA message
 * containing a "NAD", and "ROUTE" and one of the following :
 * <p/>
 * -"MQ", is used to query for a master node
 * <p/>
 * -"NR", is used to respond to a "MQ", which also contains the following
 * <p/>
 * -"AMV", is the MasterView of the cluster
 * <p/>
 * -"GS", true if MasterNode is aware that all members in group are starting as part of a GroupStarting, sent as part of
 * MasterNodeResponse NR
 * <p/>
 * -"GSC", sent when GroupStarting phase has completed. Subsequent JOIN & JoinedAndReady are considered
 * INSTANCE_STARTUP.
 * <p/>
 * -"CCNTL", is used to indicate collision between two nodes
 * <p/>
 * -"NADV", contains a node's <code>SystemAdvertisement</code>
 * <p/>
 * -"ROUTE", contains a node's list of current physical addresses, which is used to issue ioctl to the JXTA endpoint to
 * update any existing routes to the nodes. (useful when a node changes physical addresses.
 *
 * <p/>
 * MasterNode will attempt to discover a master node with the specified timeout (timeout * number of iterations) after
 * which, it determine whether to become a master, if it happens to be first node in the ordered list of discovered
 * nodes. Note: due to startup time, a master node may not always be the first node node. However if the master node
 * fails, the first node is expected to assert it's designation as the master, otherwise, all nodes will repeat the
 * master node discovery process.
 */
class MasterNode implements MessageListener, Runnable {
    private static final Logger LOG = GMSLogDomain.getLogger(GMSLogDomain.GMS_LOGGER);
    private static final Logger mcastLogger = GMSLogDomain.getMcastLogger();
    private static final Logger masterLogger = GMSLogDomain.getMasterNodeLogger();
    private static final Logger monitorLogger = GMSLogDomain.getMonitorLogger();
    private static final Logger nomLog = GMSLogDomain.getNoMCastLogger();

    private final ClusterManager manager;
    private String instanceName = "";
    private String groupName = "";

    private boolean masterAssigned = false;
    private volatile boolean discoveryInProgress = true;
    private PeerID localNodeID;
    private final SystemAdvertisement sysAdv;
    private volatile boolean started = false;
    private volatile boolean stop = false;
    private Thread thread = null;
    private ClusterViewManager clusterViewManager;
    private ClusterView discoveryView;
    private final AtomicLong masterViewID = new AtomicLong();
    // Collision control
    final Object MASTERLOCK = new Object();
    private static final String CCNTL = "CCNTL";
    private static final String MASTERNODE = "MN";
    private static final String MASTERQUERY = "MQ";
    private static final String NODEQUERY = "NQ";
    private static final String MASTERNODERESPONSE = "MR";
    private static final String NODERESPONSE = "NR";
    private static final String NAMESPACE = "MASTER";
    private static final String NODEADV = "NAD";
    private static final String AMASTERVIEW = "AMV";
    private static final String AMASTERVIEWSTATES = "AMVS";
    static final String MASTERVIEWSEQ = "SEQ";
    private static final String GROUPSTARTING = "GS";
    private static final String GROUPMEMBERS = "GN";
    private static final String GROUPSTARTUPCOMPLETE = "GSC";
    private static final String RESENDREQUEST = "RR";
    private static final String LATESTMASTERVIEWID = "LMWID";

    private static final boolean NOTIFY_LISTENERS = true;
    private static final boolean DONOT_NOTIFY_LISTENERS = false;

    private int interval = 6;
    // Default master node discovery timeout
    private long timeout = 10 * 1000L;
    static final String VIEW_CHANGE_EVENT = "VCE";
    private static final String REJOIN_SUBEVENT = "RJSE";

    private boolean groupStarting = false;
    private List<String> groupStartingMembers = null;
    private final Timer timer;
    private DelayedSetGroupStartingCompleteTask groupStartingTask = null;
    static final private long MAX_GROUPSTARTING_TIME_MS = 10L * 60L * 1000L; // 10 minute limit for group starting duration.
    static final private long GROUPSTARTING_COMPLETE_DELAY = 3000; // delay before completing group startup. Allow late arriving JoinedAndReady notifications
                                                                   // to be group starting.
    private boolean clusterStopping = false;
    final Object discoveryLock = new Object();
    private GMSContext ctx = null;
    final private SortedSet<MasterNodeMessageEvent> outstandingMasterNodeMessages;
    private Thread processOutstandingMessagesThread = null;
    private ConcurrentHashMap<String, Object> pendingGroupStartupMembers = new ConcurrentHashMap<String, Object>();
    private SortedSet<String> groupMembers = new TreeSet<String>();
    private ReliableMulticast reliableMulticast;
    private final ExecutorService checkForMissedMasterMsgSingletonExecutor;
    final TreeSet<ProcessedMasterViewId> processedChangeEvents = new TreeSet<ProcessedMasterViewId>();
    final boolean NON_MULTICAST;

    /**
     * Constructor for the MasterNode object
     *
     * @param timeout - waiting intreval to receive a response to a master node discovery
     * @param interval - number of iterations to perform master node discovery
     * @param manager the cluster manager
     */
    MasterNode(final ClusterManager manager, final long timeout, final int interval, Map props) {
        localNodeID = manager.getPeerID();
        if (timeout > 0) {
            this.timeout = timeout;
        }
        this.interval = interval;
        this.manager = manager;
        instanceName = manager.getInstanceName();
        groupName = manager.getGroupName();
        sysAdv = manager.getSystemAdvertisement();
        discoveryView = new ClusterView(sysAdv);
        timer = new Timer(true);
        outstandingMasterNodeMessages = new TreeSet<MasterNodeMessageEvent>();
        checkForMissedMasterMsgSingletonExecutor = Executors
                .newSingleThreadExecutor(new GMSThreadFactory("GMS-validateMasterChangeEvents-Group-" + manager.getGroupName() + "-thread"));
        String value = (String) props.get(DISCOVERY_URI_LIST.toString());
        boolean NON_MULTICAST_VALUE = value != null;
        if (!NON_MULTICAST_VALUE) {
            // check VIRTUAL_MULTICAST_URI_LIST for jxta implementation.
            value = (String) props.get(VIRTUAL_MULTICAST_URI_LIST.toString());
            NON_MULTICAST_VALUE = value != null;
        }
        NON_MULTICAST = NON_MULTICAST_VALUE;
    }

    /**
     * returns the cumulative MasterNode timeout
     *
     * @return timeout
     */
    long getTimeout() {
        return timeout * interval;
    }

    public long getMasterViewID() {
        return masterViewID.get();
    }

    static public long getStartTime(SystemAdvertisement adv) {
        long result = 0L;
        try {
            result = Long.parseLong(adv.getCustomTagValue(CustomTagNames.START_TIME.toString()));
        } catch (NoSuchFieldException ignore) {
        }
        return result;
    }

    /**
     * Returns true if current master is truely senior to new possible master represented by newAdv.
     *
     * @param currentAdv
     * @param newAdv
     * @return
     */
    static public boolean isSeniorMember(SystemAdvertisement currentAdv, SystemAdvertisement newAdv) {
        return getStartTime(currentAdv) < getStartTime(newAdv);
    }

    /**
     * Sets the Master Node peer ID, also checks for collisions at which event A Conflict Message is sent to the conflicting
     * node, and master designation is reiterated over the wire after a short timeout
     *
     * @param systemAdv the system advertisement
     * @return true if no collisions detected, false otherwise
     */
    boolean checkMaster(final SystemAdvertisement systemAdv) {
        if (masterAssigned && isMaster()) {
            LOG.log(Level.FINE, "checkMaster : clusterStopping() = " + clusterStopping);
            if (clusterStopping) {
                // accept the DAS as the new Master
                masterLogger.log(Level.FINE, "Resigning Master Node role in anticipation of a master node announcement");
                masterLogger.log(Level.FINE, "Accepting DAS as new master in the event of cluster stopping...");
                setMaster(systemAdv, NOTIFY_LISTENERS);
                return false;
            }
            LOG.log(Level.INFO, "mgmt.masternode.collision", new Object[] { systemAdv.getName() });
            send(systemAdv.getID(), systemAdv.getName(), createMasterCollisionMessage());

            // TODO add code to ensure whether this node should remain as master or resign
            // if (manager.getPeerID().compareTo(systemAdv.getID()) >= 0) {
            if (isSeniorMember(manager.getSystemAdvertisement(), systemAdv)) {
                masterLogger.log(Level.FINE, "Affirming Master Node role");
            } else {
                masterLogger.log(Level.FINE, "Resigning Master Node role in anticipation of a master node announcement");
                setMaster(systemAdv, DONOT_NOTIFY_LISTENERS);
            }

            return false;
        } else {
            setMaster(systemAdv, NOTIFY_LISTENERS);
            synchronized (MASTERLOCK) {
                MASTERLOCK.notifyAll();
            }
            if (masterLogger.isLoggable(Level.FINE)) {
                masterLogger.log(Level.FINE, "Discovered a Master node :" + systemAdv.getName());
            }
        }
        return true;
    }

    public Message createResendRequest(PeerID master, List<Long> missed) {
        final Message msg = createSelfNodeAdvertisement();
        msg.addMessageElement(RESENDREQUEST, (Serializable) missed);
        return msg;
    }

    /**
     * Creates a Master Collision Message. A collision message is used to indicate the conflict. Nodes receiving this
     * message then required to assess the candidate master node based on their knowledge of the network should await for an
     * assertion of the master node candidate
     *
     * @return Master Collision Message
     */
    private Message createMasterCollisionMessage() {
        final Message msg = createSelfNodeAdvertisement();
        msg.addMessageElement(CCNTL, localNodeID);
        LOG.log(Level.FINER, "Created a Master Collision Message");
        return msg;
    }

    private Message createSelfNodeAdvertisement() {
        Message msg = new MessageImpl(Message.TYPE_MASTER_NODE_MESSAGE);
        msg.addMessageElement(NODEADV, sysAdv);
        return msg;
    }

    private void sendSelfNodeAdvertisement(final PeerID id, final String name) {
        final Message msg = createSelfNodeAdvertisement();
        LOG.log(Level.FINER, "Sending a Node Response Message ");
        msg.addMessageElement(NODERESPONSE, "noderesponse");
        send(id, name, msg);
    }

    private void sendGroupStartupComplete() {
        final Message msg = createSelfNodeAdvertisement();
        if (LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE, "Sending GroupStartupComplete Message for group:" + manager.getGroupName());
        }
        msg.addMessageElement(GROUPSTARTUPCOMPLETE, "true");
        send(null, null, msg);
    }

    /**
     * Creates a Master Query Message
     *
     * @return a message containing a master query
     */
    private Message createMasterQuery() {
        final Message msg = createSelfNodeAdvertisement();
        msg.addMessageElement(MASTERQUERY, "query");
        LOG.log(Level.FINER, "Created a Master Node Query Message ");
        return msg;
    }

    /**
     * Creates a Node Query Message
     *
     * @return a message containing a node query
     */
    private Message createNodeQuery() {
        final Message msg = createSelfNodeAdvertisement();
        msg.addMessageElement(NODEQUERY, "nodequery");
        LOG.log(Level.FINER, "Created a Node Query Message ");
        return msg;
    }

    /**
     * Creates a Master Response Message
     *
     * @param masterID the MasterNode ID
     * @param announcement if true, creates an anouncement type message, otherwise it creates a response type.
     * @return a message containing a MasterResponse element
     */
    private Message createMasterResponse(boolean announcement, final PeerID masterID) {
        final Message msg = createSelfNodeAdvertisement();
        String type = MASTERNODE;
        if (!announcement) {
            type = MASTERNODERESPONSE;
        }
        msg.addMessageElement(type, masterID);
        if (groupStarting) {
            msg.addMessageElement(GROUPSTARTING, Boolean.valueOf(groupStarting));
            msg.addMessageElement(GROUPMEMBERS, (Serializable) groupMembers);
        }
        if (LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE,
                    "Created a Master Response Message with masterId = " + masterID.toString() + " groupStarting=" + Boolean.toString(groupStarting));
        }
        return msg;
    }

    /**
     * Returns the ID of a discovered Master node
     *
     * @return the MasterNode ID
     */
    boolean discoverMaster() {
        masterViewID.set(clusterViewManager.getMasterViewID());
        final long timeToWait = timeout;
        LOG.log(Level.FINER, "Attempting to discover a master node");

        Message query = createMasterQuery();
        send(null, null, query);
        if (LOG.isLoggable(Level.FINER)) {
            LOG.log(Level.FINER, " waiting for " + timeout + " ms");
        }
        try {
            synchronized (MASTERLOCK) {
                MASTERLOCK.wait(timeToWait);
            }
        } catch (InterruptedException intr) {
            Thread.interrupted();
        }
        if (LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE, "masterAssigned=" + masterAssigned);
        }
        return masterAssigned;
    }

    /**
     * Returns true if this node is the master node
     *
     * @return The master value
     */
    boolean isMaster() {
        if (masterLogger.isLoggable(Level.FINEST)) {
            masterLogger.log(Level.FINEST,
                    "isMaster :" + clusterViewManager.isMaster() + " MasterAssigned :" + masterAssigned + " View Size :" + clusterViewManager.getViewSize());
        } else if (LOG.isLoggable(Level.FINEST)) {
            LOG.log(Level.FINEST,
                    "isMaster :" + clusterViewManager.isMaster() + " MasterAssigned :" + masterAssigned + " View Size :" + clusterViewManager.getViewSize());
        }
        return clusterViewManager.isMaster();
    }

    /**
     * Returns true if this node is the master node
     *
     * @return The master value
     */
    boolean isMasterAssigned() {
        return masterAssigned;
    }

    /**
     * Returns master node ID
     *
     * @return The master node ID
     */
    PeerID getMasterNodeID() {
        return clusterViewManager.getMaster().getID();
    }

    /**
     * return true if this service has been started, false otherwise
     *
     * @return true if this service has been started, false otherwise
     */
    synchronized boolean isStarted() {
        return started;
    }

    /**
     * Resets the master node designation to the original state. This is typically done when an existing master leaves or
     * fails and a new master node is to selected.
     */
    void resetMaster() {
        LOG.log(Level.FINER, "Resetting Master view");
        masterAssigned = false;
    }

    /**
     * Parseses out the source SystemAdvertisement
     *
     * @param msg the Message
     * @return true if the message is a MasterNode announcement message
     * @throws IOException if an io error occurs
     */
    SystemAdvertisement processNodeAdvertisement(final Message msg) throws IOException {
        final Object msgElement = msg.getMessageElement(NODEADV);
        if (msgElement == null) {
            // no need to go any further
            LOG.log(Level.WARNING, "mgmt.masternode.missingna", new Object[] { msg.toString() });
            return null;
        }
        final SystemAdvertisement adv;
        if (msgElement instanceof SystemAdvertisement) {
            adv = (SystemAdvertisement) msgElement;
            if (!adv.getID().equals(localNodeID)) {
                LOG.log(Level.FINER, "Received a System advertisment Name :" + adv.getName());
            }
        } else {
            LOG.log(Level.WARNING, "mgmt.unknownMessage");
            adv = null;
        }
        return adv;
    }

    /**
     * Processes a MasterNode announcement.
     *
     * @param msg the Message
     * @param source the source node SystemAdvertisement
     * @return true if the message is a MasterNode announcement message
     * @throws IOException if an io error occurs
     */
    @SuppressWarnings("unchecked")
    boolean processMasterNodeAnnouncement(final Message msg, final SystemAdvertisement source) throws IOException {
        Object msgElement = msg.getMessageElement(MASTERNODE);
        if (msgElement == null) {
            return false;
        }

        GMSMember member = Utility.getGMSMember(source);
        long seqID = getLongFromMessage(msg, NAMESPACE, MASTERVIEWSEQ);
        if (masterLogger.isLoggable(Level.FINE)) {
            masterLogger.fine("Received a Master Node Announcement from  member:" + member.getMemberToken() + " of group:" + member.getGroupName()
                    + " masterViewSeqId:" + seqID + " masterAssigned:" + masterAssigned + " isMaster:" + isMaster());
        }
        if (checkMaster(source)) {
            msgElement = msg.getMessageElement(AMASTERVIEW);
            if (msgElement != null && msgElement instanceof List) {
                final List<SystemAdvertisement> newLocalView = (List<SystemAdvertisement>) msgElement;
                if (LOG.isLoggable(Level.FINER)) {
                    LOG.log(Level.FINER, MessageFormat.format("Received an authoritative view from {0}, of size {1}" + " resetting local view containing {2}",
                            source.getName(), newLocalView.size(), clusterViewManager.getLocalView().getSize()));
                }
                msgElement = msg.getMessageElement(VIEW_CHANGE_EVENT);
                if (msgElement != null && msgElement instanceof ClusterViewEvent) {
                    LOG.log(Level.FINE,
                            "MasterNode:PMNA: Received Master View with Seq Id=" + seqID + "Current sequence is " + clusterViewManager.getMasterViewID());
                    if (!isDiscoveryInProgress() && seqID <= clusterViewManager.getMasterViewID()) {
                        LOG.log(Level.WARNING,
                                MessageFormat.format("Received an older clusterView sequence {0}." + " Current sequence :{1} discarding out of sequence view",
                                        seqID, clusterViewManager.getMasterViewID()));
                        return true;
                    }
                    final ClusterViewEvent cvEvent = (ClusterViewEvent) msgElement;
                    if (!newLocalView.contains(manager.getSystemAdvertisement())) {
                        LOG.log(Level.FINE, "New ClusterViewManager does not contain self. Publishing Self");
                        sendSelfNodeAdvertisement(source.getID(), null);
                        // update the view once the the master node includes this node
                        return true;
                    }
                    clusterViewManager.setMasterViewID(seqID);
                    masterViewID.set(seqID);
                    if (LOG.isLoggable(Level.FINE)) {
                        LOG.log(Level.FINE, "MN: New MasterViewID = " + clusterViewManager.getMasterViewID());
                    }
                    clusterViewManager.addToView(newLocalView, true, cvEvent);
                }
            } else {
                LOG.log(Level.WARNING, "mgmt.masternode.noview");
                // TODO according to the implementation MasterNode does not include VIEW_CHANGE_EVENT
                // when it announces a Authortative master view
                // throw new IOException("New View Received without corresponding ViewChangeEvent details");
            }
        }
        synchronized (MASTERLOCK) {
            MASTERLOCK.notifyAll();
        }
        return true;
    }

    /**
     * Processes a MasterNode response.
     *
     * @param msg the Message
     * @param source the source node SystemAdvertisement
     * @return true if the message is a master node response message
     * @throws IOException if an io error occurs
     */
    @SuppressWarnings("unchecked")
    boolean processMasterNodeResponse(final Message msg, final SystemAdvertisement source) throws IOException {
        Object msgElement = msg.getMessageElement(MASTERNODERESPONSE);
        if (msgElement == null) {
            return false;
        }
        long seqID = getLongFromMessage(msg, NAMESPACE, MASTERVIEWSEQ);
        if (LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE,
                    "Received a MasterNode Response from Member:" + source.getName() + " PMNR masterViewSeqId:" + seqID + " current MasterViewSeqId:"
                            + masterViewID.get() + " masterAssigned=" + masterAssigned + " isMaster=" + isMaster() + " discoveryInProgress:"
                            + isDiscoveryInProgress());
        }
        msgElement = msg.getMessageElement(GROUPSTARTING);
        if (msgElement != null && !groupStarting) {
            Set<String> groupmembers = null;
            msgElement = msg.getMessageElement(GROUPMEMBERS);
            if (msgElement != null && msgElement instanceof Set) {
                groupmembers = (Set<String>) msgElement;
                getGMSContext().setGroupStartupJoinMembers(groupmembers);
            }
            if (LOG.isLoggable(Level.FINE)) {
                LOG.log(Level.FINE, "MNR indicates GroupStart for group: " + manager.getGroupName() + " members:" + groupmembers);
            }
            setGroupStarting(true);
            delayedSetGroupStarting(false, MAX_GROUPSTARTING_TIME_MS); // place a boundary on length of time that GroupStarting state is true.
        }
        msgElement = msg.getMessageElement(AMASTERVIEW);
        if (msgElement == null || !(msgElement instanceof List)) {
            setMaster(source, NOTIFY_LISTENERS);
            return true;
        }
        final List<SystemAdvertisement> newLocalView = (List<SystemAdvertisement>) msgElement;
        msgElement = msg.getMessageElement(VIEW_CHANGE_EVENT);
        if (msgElement == null || !(msgElement instanceof ClusterViewEvent)) {
            setMaster(source, NOTIFY_LISTENERS);
            return true;
        }
        if (!isDiscoveryInProgress() && seqID <= clusterViewManager.getMasterViewID()) {
            setMaster(source, NOTIFY_LISTENERS);
            LOG.log(Level.WARNING, "mgmt.masternode.staleview", new Object[] { seqID, newLocalView.size(), clusterViewManager.getMasterViewID() });
            return true;
        }
        final ClusterViewEvent cvEvent = (ClusterViewEvent) msgElement;
        boolean masterChanged;
        synchronized (this) {
            clusterViewManager.setMasterViewID(seqID);
            masterViewID.set(seqID);
            masterChanged = clusterViewManager.setMaster(newLocalView, source);
            masterAssigned = true;
        }
        if (masterChanged) {
            clusterViewManager.notifyListeners(cvEvent);
            clearChangeEventsNotFromCurrentMaster(source.getID());
        } else {
            clusterViewManager.addToView(newLocalView, true, cvEvent);
        }
        synchronized (MASTERLOCK) {
            MASTERLOCK.notifyAll();
        }
        return true;
    }

    /**
     * Processes a Group Startup Complete message
     *
     * @param msg the Message
     * @param source the source node SystemAdvertisement
     * @return true if the message is a Group Startup Complete message
     * @throws IOException if an io error occurs
     */
    boolean processGroupStartupComplete(final Message msg, final SystemAdvertisement source) throws IOException {
        Object msgElement = msg.getMessageElement(GROUPSTARTUPCOMPLETE);
        if (msgElement == null) {
            return false;
        }

        LOG.fine("received GROUPSTARTUPCOMPLETE from Master");
        // provide wiggle room to enable JoinedAndReady Notifications to be considered part of group startup.
        // have a small delay before transitioning out of GROUP_STARTUP state.
        delayedSetGroupStarting(false, GROUPSTARTING_COMPLETE_DELAY);
        return true;
    }

    /**
     * Processes a cluster change event. This results in adding the new members to the cluster view, and subsequently in
     * application notification.
     *
     * @param msg the Message
     * @param source the source node SystemAdvertisement
     * @return true if the message is a change event message
     * @throws IOException if an io error occurs
     */
    @SuppressWarnings("unchecked")
    boolean processChangeEvent(final Message msg, final SystemAdvertisement source) throws IOException {
        MemberStates[] memberStates = null;
        Object msgElement = msg.getMessageElement(VIEW_CHANGE_EVENT);
        if (LOG.isLoggable(Level.FINER)) {
            LOG.log(Level.FINER, "Inside processChangeEvent for group: " + manager.getGroupName());
        }
        if (msgElement != null && msgElement instanceof ClusterViewEvent) {
            final ClusterViewEvent cvEvent = (ClusterViewEvent) msgElement;
            RejoinSubevent rjse = null;
            switch (cvEvent.getEvent()) {
            case ADD_EVENT:
                rjse = (RejoinSubevent) msg.getMessageElement(REJOIN_SUBEVENT);
                if (rjse != null) {
                    getGMSContext().getInstanceRejoins().put(cvEvent.getAdvertisement().getName(), rjse);
                }
                break;
            case JOINED_AND_READY_EVENT:
                rjse = (RejoinSubevent) msg.getMessageElement(REJOIN_SUBEVENT);
                if (rjse != null) {
                    getGMSContext().getInstanceRejoins().put(cvEvent.getAdvertisement().getName(), rjse);
                }
                msgElement = msg.getMessageElement(AMASTERVIEWSTATES);
                if (msgElement != null) {
                    memberStates = (MemberStates[]) msgElement;
                }
                break;
            default:
            }
            msgElement = msg.getMessageElement(AMASTERVIEW);
            if (msgElement != null && msgElement instanceof List) {
                final List<SystemAdvertisement> newLocalView = (List<SystemAdvertisement>) msgElement;
                if (cvEvent.getEvent() == ClusterViewEvents.JOINED_AND_READY_EVENT) {
                    if (cvEvent.getAdvertisement().getID().equals(localNodeID)) {

                        // after receiving JOINED_AND_READY_EVENT from Master, stop sending READY heartbeat.
                        manager.getHealthMonitor().setJoinedAndReadyReceived();
                    }
                    if (memberStates != null) {

                        // get health state from DAS.
                        int i = 0;
                        SortedSet<String> readyMembers = new TreeSet<String>();
                        for (SystemAdvertisement adv : newLocalView) {
                            switch (memberStates[i]) {
                            case READY:
                            case ALIVEANDREADY:
                                readyMembers.add(adv.getName());
                                break;
                            }
                            i++;
                        }
                        getGMSContext().getAliveAndReadyViewWindow().put(cvEvent.getAdvertisement().getName(), readyMembers);
                    }
                }
                long seqID = getLongFromMessage(msg, NAMESPACE, MASTERVIEWSEQ);
                if (seqID <= clusterViewManager.getMasterViewID()) {
                    LOG.log(Level.WARNING, "mgmt.masternode.staleviewnotify", new Object[] { seqID, clusterViewManager.getMasterViewID(),
                            cvEvent.getEvent().toString(), cvEvent.getAdvertisement().getName(), manager.getGroupName() });
                    clusterViewManager.notifyListeners(cvEvent);
                    return true;
                }
                if (LOG.isLoggable(Level.FINER)) {
                    LOG.log(Level.FINER,
                            MessageFormat.format("Received a new view of size :{0}, event :{1}", newLocalView.size(), cvEvent.getEvent().toString()));
                }
                if (!newLocalView.contains(manager.getSystemAdvertisement())) {
                    LOG.log(Level.FINER, "Received ClusterViewManager does not contain self. Publishing Self");
                    sendSelfNodeAdvertisement(source.getID(), null);
                    clusterViewManager.notifyListeners(cvEvent);

                    // update the view once the the master node includes this node
                    return true;
                }
                clusterViewManager.setMasterViewID(seqID);
                masterViewID.set(seqID);
                clusterViewManager.addToView(newLocalView, true, cvEvent);
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    boolean processResendRequest(final Message msg, final SystemAdvertisement adv, boolean isAdvAddedToView) throws IOException {
        if (isMaster() && masterAssigned) {
            Object element = msg.getMessageElement(RESENDREQUEST);
            if (element != null && element instanceof List) {
                List<Long> missedList = (List<Long>) element;
                for (Long missed : missedList) {
                    reliableMulticast.resend(adv.getID(), missed);
                }
                if (isAdvAddedToView) {
                    final ClusterViewEvent cvEvent = new ClusterViewEvent(ADD_EVENT, adv);
                    Message masterResponseMsg = createMasterResponse(false, localNodeID);
                    synchronized (masterViewID) {
                        clusterViewManager.setMasterViewID(masterViewID.incrementAndGet());
                        addAuthoritativeView(masterResponseMsg);
                    }
                    clusterViewManager.notifyListeners(cvEvent);
                    if (LOG.isLoggable(Level.FINE)) {
                        LOG.log(Level.FINE, "Rejoin initiated due to resend request: Master " + manager.getInstanceName()
                                + " broadcasting ADD_EVENT  of member: " + adv.getName() + " to GMS group: " + manager.getGroupName());
                    }
                    sendNewView(null, cvEvent, masterResponseMsg, false);
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Processes a Masternode Query message. This results in a master node response if this node is a master node.
     *
     * @param msg the Message
     * @param adv the source node SystemAdvertisement
     * @return true if the message is a query message
     * @throws IOException if an io error occurs
     */
    boolean processMasterNodeQuery(final Message msg, final SystemAdvertisement adv, boolean isAdvAddedToView) throws IOException {

        final Object msgElement = msg.getMessageElement(MASTERQUERY);

        if (msgElement == null || adv == null) {
            return false;
        }
        if (isMaster() && masterAssigned) {
            LOG.log(Level.FINE, MessageFormat.format("Received a MasterNode Query from Name :{0} ID :{1}", adv.getName(), adv.getID()));
            if (isAdvAddedToView) {
                final ClusterViewEvent cvEvent = new ClusterViewEvent(ADD_EVENT, adv);
                Message masterResponseMsg = createMasterResponse(false, localNodeID);
                synchronized (masterViewID) {
                    clusterViewManager.setMasterViewID(masterViewID.incrementAndGet());
                    addAuthoritativeView(masterResponseMsg);
                }
                clusterViewManager.notifyListeners(cvEvent);
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.log(Level.FINE, "Master " + manager.getInstanceName() + " broadcasting ADD_EVENT  of member: " + adv.getName() + " to GMS group: "
                            + manager.getGroupName());
                }
                sendNewView(null, cvEvent, masterResponseMsg, false);
            } else if (LOG.isLoggable(Level.FINER)) {
                LOG.log(Level.FINER, "Node " + adv.getName() + " is already in the view. Hence not sending ADD_EVENT.");
            }
        } else if (masterAssigned && NON_MULTICAST) {

            // forward master node query to Master.
            send(getMasterNodeID(), "", msg);
        }
        // for issue 484
        // when the master is killed and restarted very quickly
        // there is no failure notification sent out and no new master elected
        // this results in the instance coming back up and assuming group leadership
        // instance which is the master never sends out a join notif for itself.
        // this will get fixed with the following code

        // check if this instance has an older start time of the restarted instance
        // i.e. check if the restarted instance is in the cache of this instance

        // check if the adv.getID was the master before ...
        SystemAdvertisement madv = clusterViewManager.getMaster();
        SystemAdvertisement oldSysAdv = clusterViewManager.get(adv.getID());
        if (madv != null && madv.getID().equals(adv.getID())) {
            // master restarted
            // check for the start times for both advs i.e. the one in the view and the one passed into this method.
            // If they are different that means the master has restarted for sure
            // put a warning that master has restarted without failure

            if (confirmInstanceHasRestarted(oldSysAdv, adv)) {
                LOG.log(Level.WARNING, "mgmt.masternode.unreportedmasterfailure", new Object[] { madv.getName() });
                // re-elect a new master so that a join and joinedandready event
                // can be sent for the restarted master
                if (LOG.isLoggable(Level.FINER)) {
                    LOG.finer("MasterNode.processMasterNodeQuery() : clusterViewManager.getMaster().getID() = " + clusterViewManager.getMaster().getID());
                    LOG.finer("MasterNode.processMasterNodeQuery() : adv.getID() = " + adv.getID());
                    LOG.finer("MasterNode.processMasterNodeQuery() : clusterViewManager.getMaster().getname() = " + clusterViewManager.getMaster().getName());
                    LOG.finer("MasterNode.processMasterNodeQuery() : adv.getID() = " + adv.getName());
                }
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine("MasterNode.processMasterNodeQuery() : re-electing the master...");
                }
                // remove outdated advertisement for failed previous master (with start time of previous master)
                manager.getClusterViewManager().remove(oldSysAdv);
                // add back in the restarted member with the new start time.
                // otherwise, restarted member will receive a view without itself in it.
                manager.getClusterViewManager().add(adv);
                resetMaster();
                appointMasterNode();
            } else {
                LOG.fine("MasterNode.processMasterNodeQuery() : master node did not restart as suspected");
            }
        } else {
            // some instance other than the master has restarted
            // without a failure notification

            // todo: this is an intermediate fix for ADD_EVENT with REJOIN to work.
            // issue is that addToView() does a reset of view, flushing old systemAdvertisements necessary
            // to detect restart.
            boolean restarted = confirmInstanceHasRestarted(oldSysAdv, adv, false);
            if (restarted) {
                manager.getClusterViewManager().add(adv);
            }

        }
        return true;
    }

    boolean confirmInstanceHasRestarted(SystemAdvertisement oldSysAdv, SystemAdvertisement newSysAdv) {
        return confirmInstanceHasRestarted(oldSysAdv, newSysAdv, true);
    }

    boolean confirmInstanceHasRestarted(SystemAdvertisement oldSysAdv, SystemAdvertisement newSysAdv, boolean reportWarning) {
        if (oldSysAdv != null && newSysAdv != null) {
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("MasterNode.confirmInstanceHasRestarted() : oldSysAdv.getName() = " + oldSysAdv.getName());
            }
            long cachedAdvStartTime = Utility.getStartTime(oldSysAdv);
            if (cachedAdvStartTime == Utility.NO_SUCH_TIME) {
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine("MasterNode.confirmInstanceHasRestarted : Could not find the START_TIME field in the cached system advertisement");
                }
                return false;
            }
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("MasterNode.confirmInstanceHasRestarted() : cachedAdvStartTime = " + cachedAdvStartTime);
            }
            long currentAdvStartTime = Utility.getStartTime(newSysAdv);
            if (currentAdvStartTime == Utility.NO_SUCH_TIME) {
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine("MasterNode.confirmInstanceHasRestarted : Could not find the START_TIME field in the current system advertisement");
                }
                return false;
            }
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("MasterNode.confirmInstanceHasRestarted() : currentAdvStartTime = " + currentAdvStartTime);
            }
            if (currentAdvStartTime != cachedAdvStartTime) {
                // previous instance has restarted w/o a FAILURE detection. Clean cache of references to previous instantiation of the
                // instance.
                manager.getHealthMonitor().cleanAllCaches(oldSysAdv.getName());
                // that means the instance has really restarted
                LOG.log(Level.WARNING, "mgmt.masternode.restarted", new Object[] { newSysAdv.getName(), new Date(currentAdvStartTime) });
                LOG.log(Level.WARNING, "mgmt.masternode.nofailurereported", new Object[] { new Date(cachedAdvStartTime) });
                return true;
            } else {
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine("MasterNode.confirmInstanceHasRestarted : currentAdvStartTime and cachedAdvStartTime have the same value = "
                            + new Date(cachedAdvStartTime) + " .Instance " + newSysAdv.getName() + "was not restarted.");
                }
                return false;
            }
        } else {
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("MasterNode.confirmInstanceHasRestarted : oldSysAdv or newSysAdv is null");
            }
            return false;
        }
    }

    /**
     * Processes a Node Query message. This results in a node response.
     *
     * @param msg the Message
     * @param adv the source node SystemAdvertisement
     * @return true if the message is a query message
     * @throws IOException if an io error occurs
     */
    boolean processNodeQuery(final Message msg, final SystemAdvertisement adv, boolean isAdvAddedToView) throws IOException {
        final Object msgElement = msg.getMessageElement(NODEQUERY);

        if (msgElement == null || adv == null) {
            if (LOG.isLoggable(Level.FINER)) {
                LOG.log(Level.FINER, "WARNING: returning without processing in processNodeResponse msgElement=" + msgElement + " adv=" + adv);
            }
            return false;
        }
        if (LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINER, MessageFormat.format("Received a Node Query from Name :{0} ID :{1}", adv.getName(), adv.getID()));
        }
        if (isMaster() && masterAssigned) {
            if (LOG.isLoggable(Level.FINE)) {
                LOG.log(Level.FINE, MessageFormat.format("Received a Node Query from Name :{0} ID :{1}", adv.getName(), adv.getID()));
            }
            if (isAdvAddedToView) {
                final ClusterViewEvent cvEvent = new ClusterViewEvent(ADD_EVENT, adv);
                Message responseMsg = createMasterResponse(false, localNodeID);
                synchronized (masterViewID) {
                    clusterViewManager.setMasterViewID(masterViewID.incrementAndGet());
                    addAuthoritativeView(responseMsg);
                }
                clusterViewManager.notifyListeners(cvEvent);
                sendNewView(null, cvEvent, responseMsg, false);
            } else if (LOG.isLoggable(Level.FINER)) {
                LOG.log(Level.FINER, "Node " + adv.getName() + " is already in the view. Hence not sending ADD_EVENT.");
            }
        } else {
            final Message response = createSelfNodeAdvertisement();
            response.addMessageElement(NODERESPONSE, "noderesponse");
            if (LOG.isLoggable(Level.FINER)) {
                LOG.log(Level.FINER, "Sending Node response to  :" + adv.getName());
            }

            // Part of fix for BugDB 13375653. Broadcast node response. to all nodes.
            // TCP connnection between isolated instance and master does not repair in time to
            // receive this message.
            // send(adv.getID(), null, response);
            send(null, null, response);
        }
        return true;
    }

    /**
     * Processes a Node Response message.
     *
     * @param msg the Message
     * @param adv the source node SystemAdvertisement
     * @return true if the message is a response message
     * @throws IOException if an io error occurs
     */
    boolean processNodeResponse(final Message msg, final SystemAdvertisement adv, boolean isAdvAddedToView) throws IOException {
        final Object msgElement = msg.getMessageElement(NODERESPONSE);

        if (msgElement == null || adv == null) {
            if (LOG.isLoggable(Level.FINER)) {
                LOG.log(Level.FINER, "WARNING: returning without processing in processNodeResponse msgElement=" + msgElement + " adv=" + adv);
            }
            return false;
        }
        if (isMaster() && masterAssigned) {
            if (LOG.isLoggable(Level.FINE)) {
                LOG.log(Level.FINE, MessageFormat.format("Received a Node Response from Name :{0} ID :{1} isAdvAddedToView :{2}", adv.getName(), adv.getID(),
                        isAdvAddedToView));
            }
            if (isAdvAddedToView) {
                final ClusterViewEvent cvEvent = new ClusterViewEvent(ADD_EVENT, adv);
                Message responseMsg = createMasterResponse(false, localNodeID);
                synchronized (masterViewID) {
                    clusterViewManager.setMasterViewID(masterViewID.incrementAndGet());
                    addAuthoritativeView(responseMsg);
                }
                clusterViewManager.notifyListeners(cvEvent);
                sendNewView(null, cvEvent, responseMsg, false);
            } else if (LOG.isLoggable(Level.FINE)) {
                LOG.log(Level.FINE, "Node " + adv.getName() + " is already in the view. Hence not sending ADD_EVENT.");
            }
        } else if (LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE, "Received a node response from " + adv.getName() + " id:" + adv.getID());
        }
        return true;
    }

    /**
     * Processes a MasterNode Collision. When two nodes assume a master role (by assertion through a master node
     * announcement), each node can indepedentaly and deterministically elect the master node. This is done through electing
     * the node atop of the NodeID sort order. If there are more than two nodes in collision, this same process is repeated.
     *
     * @param msg the Message
     * @param adv the source node SystemAdvertisement
     * @return true if the message was indeed a collision message
     * @throws IOException if an io error occurs
     */
    boolean processMasterNodeCollision(final Message msg, final SystemAdvertisement adv) throws IOException {

        final Object msgElement = msg.getMessageElement(CCNTL);
        if (msgElement == null) {
            return false;
        }
        final SystemAdvertisement madv = manager.getSystemAdvertisement();
        if (LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE, MessageFormat.format("Candidate Master: " + madv.getName() + "received a MasterNode Collision from Name :{0} ID :{1}",
                    adv.getName(), adv.getID()));
        }
        if (madv.getID().compareTo(adv.getID()) >= 0) {
            if (LOG.isLoggable(Level.FINE)) {
                LOG.log(Level.FINE, "Member " + madv.getName() + " affirming Master Node role over member:" + adv.getName());
            }
            synchronized (MASTERLOCK) {
                // Ensure the view SeqID is incremented by 2
                clusterViewManager.setMasterViewID(masterViewID.incrementAndGet());
                announceMaster(manager.getSystemAdvertisement());
                MASTERLOCK.notifyAll();
            }
        } else {
            LOG.log(Level.FINE, "Resigning Master Node role");
            clusterViewManager.setMaster(adv, true);
        }
        return true;
    }

    /**
     * Probes a node. Used when a node does not exist in local view
     *
     * @param entry node entry
     * @throws IOException if an io error occurs sending the message
     */
    void probeNode(final HealthMessage.Entry entry) throws IOException {
        if (isMaster() && masterAssigned) {
            if (LOG.isLoggable(Level.FINER)) {
                LOG.log(Level.FINER, "Probing ID = " + entry.id + ", name = " + entry.adv.getName());
            }
            send(entry.id, null, createNodeQuery());
        }
    }

    public void receiveMessageEvent(final MessageEvent event) throws MessageIOException {
        final Message msg = event.getMessage();
        if (msg == null) {
            return;
        }
        final MasterNodeMessageEvent mnme = new MasterNodeMessageEvent(event);
        if (monitorLogger.isLoggable(Level.FINE)) {
            monitorLogger.fine("MasterNode.receiveMessageEvent:" + mnme.toString());
        }
        if (mcastLogger.isLoggable(Level.FINE)) {
            mcastLogger.fine("receiveMessageEvent: process master node message masterViewSeqId:" + mnme.seqId + " from member:" + event.getSourcePeerID());
        }
        if (mnme.seqId == -1) {
            processNextMessageEvent(mnme);
        } else {
            final boolean added;

            if (masterAssigned && !isMaster() && !isDiscoveryInProgress()) {

                ProcessedMasterViewId processed = new ProcessedMasterViewId((PeerID) msg.getMessageElement(Message.SOURCE_PEER_ID_TAG), mnme.seqId);
                boolean result;
                synchronized (processedChangeEvents) {
                    result = processedChangeEvents.add(processed);
                }
                if (!result) {
                    if (mcastLogger.isLoggable(Level.FINE)) {
                        mcastLogger.log(Level.FINE, "dropping master node message with masterViewID=" + mnme.seqId + " it was already processed.");
                    }
                    return;
                }
            }

            synchronized (outstandingMasterNodeMessages) {

                // place message into set ordered via MasterViewSequenceId.
                added = outstandingMasterNodeMessages.add(mnme);
                outstandingMasterNodeMessages.notify();
            }
            if (added) {
                if (monitorLogger.isLoggable(Level.FINE)) {
                    monitorLogger
                            .fine("receiveMessageEvent: added master node message masterViewSeqId:" + mnme.seqId + " from member:" + event.getSourcePeerID());
                }
            } else {
                LOG.log(Level.WARNING, "mgmt.masternode.dupmasternodemsg", new Object[] { mnme.seqId, event.getSourcePeerID(), mnme.toString() });
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public void processNextMessageEvent(final MasterNodeMessageEvent masterNodeMessage) throws MessageIOException {
        boolean result = false;
        if (manager.isStopping()) {
            if (mcastLogger.isLoggable(Level.FINE)) {
                mcastLogger.log(Level.FINE, "Since this Peer is Stopping, returning without processing incoming master node message. ");
            }
            return;
        }

        if (isStarted()) {
            final Message msg;
            // grab the message from the event
            msg = masterNodeMessage.msg;
            long seqId = masterNodeMessage.seqId;
            if (msg == null) {
                LOG.log(Level.WARNING, "mgmt.masternode.nullmessage");
                return;
            }
            try {
                final SystemAdvertisement adv = processNodeAdvertisement(msg);
                if (adv != null && adv.getID().equals(localNodeID)) {
                    LOG.log(Level.FINEST, "Discarding loopback message");
                    return;
                }
                // add the advertisement to the list
                if (adv != null) {
                    if (isMaster() && masterAssigned) {

                        // MEMBER IS ADDED TO GROUP HERE. Must authenticate BEFORE adding.
                        result = clusterViewManager.add(adv);
                    } else if (discoveryInProgress) {
                        result = false; // never report Join event during discovery mode.

                        // MEMBER IS ADDED TO GROUP while in DISCOVERY HERE. Should we authenitcate BEFORE adding to
                        // discoveryView.
                        discoveryView.add(adv);
                    }
                }
                if (processResendRequest(msg, adv, result)) {
                    return;
                }

                // HERE IS PATH FOR A MEMBER JOINING THE GROUP
                if (processMasterNodeQuery(msg, adv, result)) {
                    return;
                }
                if (processNodeQuery(msg, adv, result)) {
                    return;
                }
                if (processNodeResponse(msg, adv, result)) {
                    return;
                }
                if (processGroupStartupComplete(msg, adv)) {
                    return;
                }
                if (processMasterNodeResponse(msg, adv)) {
                    return;
                }
                if (processMasterNodeAnnouncement(msg, adv)) {
                    return;
                }
                if (processMasterNodeCollision(msg, adv)) {
                    return;
                }
                if (processChangeEvent(msg, adv)) {
                    return;
                }
            } catch (IOException e) {
                LOG.log(Level.WARNING, e.getLocalizedMessage(), e);
            }
            if (LOG.isLoggable(Level.FINER)) {
                LOG.log(Level.FINER, MessageFormat.format("ClusterViewManager contains {0} entries", clusterViewManager.getViewSize()));
            }
        } else {
            if (LOG.isLoggable(Level.FINER)) {
                LOG.log(Level.FINER, "Started : " + isStarted());
            }
        }
    }

    public int getType() {
        return Message.TYPE_MASTER_NODE_MESSAGE;
    }

    private void announceMaster(SystemAdvertisement adv) {
        if (reliableMulticast != null) {
            reliableMulticast.stop();
            reliableMulticast = null;
        }
        reliableMulticast = new ReliableMulticast(manager);
        final Message msg = createMasterResponse(true, adv.getID());
        final ClusterViewEvent cvEvent = new ClusterViewEvent(ClusterViewEvents.MASTER_CHANGE_EVENT, adv);
        if (masterAssigned && isMaster()) {
            LOG.log(Level.INFO, "mgmt.masternode.announcemasternode",
                    new Object[] { clusterViewManager.getViewSize(), manager.getInstanceName(), manager.getGroupName() });

            sendNewView(null, cvEvent, msg, true);
        }
    }

    /**
     * MasterNode discovery thread. Starts the master node discovery protocol
     */
    public void run() {
        startMasterNodeDiscovery();
    }

    /**
     * Starts the master node discovery protocol
     */
    void startMasterNodeDiscovery() {
        int count = 0;
        // assumes self as master node
        synchronized (this) {
            if (!masterAssigned) {

                // if masterAssigned, no longer in discovery mode, so skip making this member the MASTER during discoverymode.
                // there was a race condition, that a MemberQueryResponse was processed BEFORE discovery.
                // thus masterAssigned was set to true, master was assigned to true master and then the next line promoted this
                // instance to be the master of entire cluster. (bug)
                clusterViewManager.start();
            }
        }
        if (masterAssigned) {
            discoverMaster(); // ensure all instances send out MasterQuery whether or not they already know the master.
                              // keep discovery of new members by Master consistent.
            discoveryInProgress = false;
            if (LOG.isLoggable(Level.FINE)) {
                LOG.log(Level.FINE, "startMasterNodeDiscovery: discovery completed. masterSequenceId:" + this.masterViewID.get()
                        + " clusterViewManager.masterViewID:" + clusterViewManager.getMasterViewID());
            }
            synchronized (discoveryLock) {
                discoveryLock.notifyAll();
            }
            return;
        }
        while (!stop && count < interval) {
            if (!discoverMaster()) {
                // TODO: Consider changing this approach to a background reaper
                // that would reconcole the group from time to time, consider
                // using an incremental timeout interval ex. 800, 1200, 2400,
                // 4800, 9600 ms for iteration periods, then revert to 800
                count++;
            } else {
                break;
            }
        }
        // timed out
        if (!masterAssigned) {
            LOG.log(Level.FINER, "MN Discovery timeout, appointing master");
            appointMasterNode();
        }
        if (LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE, "startMasterNodeDiscovery: after discoverMaster polling, discovery completed. masterSequenceId:" + this.masterViewID.get()
                    + " clusterViewManager.masterViewID:" + clusterViewManager.getMasterViewID());
        }
        discoveryInProgress = false;
        synchronized (discoveryLock) {
            discoveryLock.notifyAll();
        }
    }

    /**
     * determines a master node candidate, if the result turns to be this node then a master node announcement is made to
     * assert such state
     */
    void appointMasterNode() {
        if (masterAssigned) {
            return;
        }
        final SystemAdvertisement madv;
        if (LOG.isLoggable(Level.FINER)) {
            LOG.log(Level.FINER, "MasterNode: discoveryInProgress=" + discoveryInProgress);
        }
        if (discoveryInProgress) {
            // ensure current instance is in the view.
            discoveryView.add(sysAdv);
            madv = discoveryView.getMasterCandidate();
        } else {
            // ensure current instance is in the view.
            clusterViewManager.add(sysAdv);
            madv = clusterViewManager.getMasterCandidate();
        }
        if (LOG.isLoggable(Level.FINER)) {
            LOG.log(Level.FINER, "MasterNode: Master Candidate=" + madv.getName());
        }
        setMaster(madv, DONOT_NOTIFY_LISTENERS);
        if (madv.getID().equals(localNodeID)) {
            LOG.log(Level.FINER, "MasterNode: Setting myself as MasterNode ");
            clusterViewManager.setMasterViewID(masterViewID.incrementAndGet());
            if (LOG.isLoggable(Level.FINER)) {
                LOG.log(Level.FINER, "MasterNode: masterViewId =" + masterViewID);
            }
            // generate view change event
            if (discoveryInProgress) {
                List<SystemAdvertisement> list = discoveryView.getView();
                final ClusterViewEvent cvEvent = new ClusterViewEvent(ClusterViewEvents.MASTER_CHANGE_EVENT, madv);
                clusterViewManager.addToView(list, true, cvEvent);
            } else {
                LOG.log(Level.FINER, "MasterNode: Notifying Local Listeners of  Master Change");
                final ClusterViewEvent cvEvent = new ClusterViewEvent(ClusterViewEvents.MASTER_CHANGE_EVENT, madv);
                clusterViewManager.notifyListeners(cvEvent);
            }

        }
        discoveryView.clear();
        discoveryView.add(sysAdv);
        synchronized (MASTERLOCK) {
            if (madv.getID().equals(localNodeID)) {
                // this thread's job is done
                LOG.log(Level.INFO, "mgmt.masternode.assumingmasternode", new Object[] { madv.getName(), manager.getGroupName() });
                // broadcast we are the masternode for the cluster
                LOG.log(Level.FINER, "MasterNode: announcing MasterNode assumption ");
                announceMaster(manager.getSystemAdvertisement());
                MASTERLOCK.notifyAll();
            }
        }
    }

    /**
     * Send a message to a specific node. In the case where the id is null the message multicast
     *
     * @param peerid the destination node, if null, the message is sent to the cluster
     * @param msg the message to send
     * @param name name used for debugging messages
     */
    private void send(final PeerID peerid, final String name, final Message msg) {
        try {
            if (peerid != null) {
                // Unicast datagram
                // create a op pipe to the destination peer
                if (LOG.isLoggable(Level.FINER)) {
                    LOG.log(Level.FINER, "Unicasting Message to :" + name + "ID=" + peerid);
                }
                final boolean sent = manager.getNetworkManager().send(peerid, msg);
                if (!sent) {
                    LOG.log(Level.WARNING, "mgmt.masternode.sendmsgfailed", new Object[] { msg, name });
                }
            } else {
                // multicast
                LOG.log(Level.FINER, "Broadcasting Message");
                final boolean sent = manager.getNetworkManager().broadcast(msg);
                if (!sent) {
                    LOG.log(Level.WARNING, "mgmt.masternode.broadcastmsgfailed", new Object[] { msg, manager.getGroupName() });
                }
            }
        } catch (IOException io) {
            LOG.log(Level.FINEST, "Failed to send message", io);
        }
    }

    /**
     * Sends the discovered view to the group indicating a new membership snapshot has been created. This will lead to all
     * members replacing their localviews to this new view.
     *
     * @param event The ClusterViewEvent object containing details of the event.
     * @param msg The message to send
     * @param toID receipient ID
     * @param includeView if true view will be included in the message
     */
    void sendNewView(final PeerID toID, final ClusterViewEvent event, final Message msg, final boolean includeView) {
        final String memberName = event.getAdvertisement().getName();
        RejoinSubevent rjse = null;
        if (includeView) {
            addAuthoritativeView(msg);
        }
        // LOG.log(Level.FINER, MessageFormat.format("Created a view element of size {0}bytes", cvEvent.getByteLength()));
        msg.addMessageElement(VIEW_CHANGE_EVENT, event);
        switch (event.getEvent()) {
        case ADD_EVENT:
        case JOINED_AND_READY_EVENT:
            GMSContext gmsCtx = getGMSContext();
            if (gmsCtx != null) {
                rjse = gmsCtx.getInstanceRejoins().get(memberName);
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine("sendNewView: clusterViewEvent:" + event.getEvent().toString() + " rejoinSubevent=" + rjse + " member: " + memberName);
                }
                if (rjse != null) {
                    msg.addMessageElement(REJOIN_SUBEVENT, rjse);
                }
            }
            break;
        default:
            break;
        }
        if (LOG.isLoggable(Level.FINER)) {
            LOG.log(Level.FINER, "Sending new authoritative cluster view to group, event :" + event.getEvent().toString() + " viewSeqId: "
                    + clusterViewManager.getMasterViewID());
        }
        if (toID != null) {
            send(toID, null, msg);
        } else {
            reliableBroadcastSend(msg);
        }
        if (rjse != null && event.getEvent() == JOINED_AND_READY_EVENT) {
            ctx.getInstanceRejoins().remove(memberName);
        }
    }

    /**
     * Adds an authoritative message element to a Message
     *
     * @param msg The message to add the view to
     */
    void addAuthoritativeView(final Message msg) {
        final List<SystemAdvertisement> view;
        ClusterView cv = clusterViewManager.getLocalView();
        view = cv.getView();
        if (LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE, "MasterNode: Adding Authoritative View of size " + view.size() + "  to view message masterSeqId=" + cv.masterViewId);
        }
        msg.addMessageElement(AMASTERVIEW, (Serializable) view);
        addLongToMessage(msg, NAMESPACE, MASTERVIEWSEQ, cv.masterViewId);
    }

    /**
     * Adds an authoritative message element to a Message
     *
     * @param msg The message to add the view to
     */
    void addReadyAuthoritativeView(final Message msg) {
        final List<SystemAdvertisement> view;
        ClusterView cv = clusterViewManager.getLocalView();
        view = cv.getView();
        if (LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE, "MasterNode: Adding Authoritative View of size " + view.size() + "  to view message masterSeqId=" + cv.masterViewId);
        }
        msg.addMessageElement(AMASTERVIEW, (Serializable) view);

        // compute MemberStates for AMASTERVIEW list.
        MemberStates[] memberStates = new MemberStates[view.size()];
        int i = 0;
        for (SystemAdvertisement adv : view) {
            MemberStates value = MemberStates.UNKNOWN;
            try {
                value = MemberStates.valueOf(manager.getHealthMonitor().getMemberStateFromHeartBeat(adv.getID(), 10000).toUpperCase());
            } catch (Throwable t) {
            }
            memberStates[i] = value;
            i++;
        }
        msg.addMessageElement(AMASTERVIEWSTATES, memberStates);

        addLongToMessage(msg, NAMESPACE, MASTERVIEWSEQ, cv.masterViewId);
    }

    /**
     * Stops this service
     */
    synchronized void stop() {
        LOG.log(Level.FINER, "Stopping MasterNode");
        discoveryView.clear();
        thread = null;
        masterAssigned = false;
        started = false;
        stop = true;
        discoveryInProgress = false;
        synchronized (discoveryLock) {
            discoveryLock.notifyAll();
        }
        manager.getNetworkManager().removeMessageListener(this);
        processOutstandingMessagesThread.interrupt();
        if (reliableMulticast != null) {
            reliableMulticast.stop(); // stop reaper TimerTask.
        }
        this.checkForMissedMasterMsgSingletonExecutor.shutdownNow();
    }

    /**
     * Starts this service. Creates the communication channels, and the MasterNode discovery thread.
     */
    synchronized void start() {
        LOG.log(Level.FINER, "Starting MasterNode");
        this.clusterViewManager = manager.getClusterViewManager();
        started = true;
        manager.getNetworkManager().addMessageListener(this);
        LOG.log(Level.INFO, "mgmt.masternode.registeredlistener", new Object[] { manager.getInstanceName(), manager.getGroupName() });
        processOutstandingMessagesThread = new Thread(new ProcessOutstandingMessagesTask(),
                "GMS MasterNode processOutstandingChangeEvents Group-" + manager.getGroupName());
        processOutstandingMessagesThread.setDaemon(true);
        processOutstandingMessagesThread.start();
        thread = new Thread(this, "GMS MasterNode Group-" + manager.getGroupName());
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Sends a ViewChange event to the cluster.
     *
     * @param event VievChange event
     */
    void viewChanged(final ClusterViewEvent event) {
        if (isMaster() && masterAssigned) {
            clusterViewManager.notifyListeners(event);
            Message msg = createSelfNodeAdvertisement();
            synchronized (masterViewID) {
                // increment the view seqID
                clusterViewManager.setMasterViewID(masterViewID.incrementAndGet());
                addAuthoritativeView(msg);
            }
            sendNewView(null, event, msg, false);
        }
    }

    /**
     * Adds a long to a message
     *
     * @param message The message to add to
     * @param nameSpace The namespace of the element to add. a null value assumes default namespace.
     * @param elemName Name of the Element.
     * @param data The feature to be added to the LongToMessage attribute
     */
    private static void addLongToMessage(Message message, String nameSpace, String elemName, long data) {
        message.addMessageElement(elemName, data);
    }

    /**
     * Returns an long from a message
     *
     * @param message The message to retrieve from
     * @param nameSpace The namespace of the element to get.
     * @param elemName Name of the Element.
     * @return The long value, -1 if element does not exist in the message
     * @throws NumberFormatException If the String does not contain a parsable int.
     */
    private static long getLongFromMessage(Message message, String nameSpace, String elemName) throws NumberFormatException {
        Object value = message.getMessageElement(elemName);
        if (value != null) {
            return Long.parseLong(value.toString());
        } else {
            return -1;
        }
    }

    /**
     * This method allows the DAS to become a master by force. This is especially important when the the DAS is going down
     * and then coming back up. This way only the DAS will ever be the master.
     */
    void takeOverMasterRole() {
        synchronized (MASTERLOCK) {
            final SystemAdvertisement madv = clusterViewManager.get(localNodeID);
            if (LOG.isLoggable(Level.FINER)) {
                LOG.log(Level.FINER, "MasterNode: Forcefully becoming the Master..." + madv.getName());
            }
            // avoid notifying listeners
            setMaster(madv, DONOT_NOTIFY_LISTENERS);
            clusterViewManager.setMasterViewID(masterViewID.incrementAndGet());
            if (LOG.isLoggable(Level.FINER)) {
                LOG.log(Level.FINER, "MasterNode: becomeMaster () : masterViewId =" + masterViewID);
                // generate view change event
                LOG.log(Level.FINER, "MasterNode: becomeMaster () : Notifying Local Listeners of  Master Change");
            }
            final ClusterViewEvent cvEvent = new ClusterViewEvent(ClusterViewEvents.MASTER_CHANGE_EVENT, madv);
            clusterViewManager.notifyListeners(cvEvent);

            discoveryView.clear();
            discoveryView.add(sysAdv);

            // broadcast we are the masternode
            LOG.log(Level.FINER, "MasterNode: becomeMaster () : announcing MasterNode assumption ");
            announceMaster(manager.getSystemAdvertisement());
            MASTERLOCK.notifyAll();
            manager.notifyNewMaster();

        }
    }

    void setClusterStopping() {
        clusterStopping = true;
    }

    ClusterViewEvent sendReadyEventView(final SystemAdvertisement adv) {
        boolean isGroup = this.pendingGroupStartupMembers.containsKey(adv.getName());
        final ClusterViewEvent cvEvent = new ClusterViewEvent(ClusterViewEvents.JOINED_AND_READY_EVENT, adv);
        LOG.log(Level.FINEST, MessageFormat.format("Sending to Group, Joined and Ready Event View for peer :{0}", adv.getName()));
        Message msg = this.createSelfNodeAdvertisement();
        synchronized (masterViewID) {
            clusterViewManager.setMasterViewID(masterViewID.incrementAndGet());
            this.addReadyAuthoritativeView(msg);
        }
        sendNewView(null, cvEvent, msg, false);

        if (isGroup) {
            this.pendingGroupStartupMembers.remove(adv.getName());
            LOG.fine("sendReadyEventView: pendingGroupStartupMembers: member=" + adv.getName() + " size=" + pendingGroupStartupMembers.size()
                    + " pending members remaining:" + pendingGroupStartupMembers.keySet());
            if (pendingGroupStartupMembers.size() == 0) {
                LOG.fine("sendReadyEventView:  start-cluster groupStartup completed");
                setGroupStarting(false);
            }
        }
        return cvEvent;
    }

    boolean isDiscoveryInProgress() {
        return discoveryInProgress;
    }

    void setGroupStarting(boolean value) {
        boolean sendMessage = false;
        if (groupStarting && !value && this.isMaster() && this.isMasterAssigned()) {
            sendMessage = true;
        }
        groupStarting = value;
        getGMSContext().setGroupStartup(value);
        if (sendMessage) {
            // send a message to other instances in cluster that group startup has completed
            sendGroupStartupComplete();
        }
    }

    /**
     * Avoid boundary conditions for group startup completed. Delay setting to false for delaySet time.
     *
     * @param value
     * @param delaySet in milliseconds, time to delay setting group starting to value
     */
    private void delayedSetGroupStarting(boolean value, long delaySet) {
        synchronized (timer) {
            if (groupStartingTask != null) {
                groupStartingTask.cancel();
            }
            groupStartingTask = new DelayedSetGroupStartingCompleteTask(delaySet);

            // place a delay duration on group startup duration to allow possibly late arriving JoinedAndReady notifications to be
            // considered part of group.
            timer.schedule(groupStartingTask, delaySet);
        }
    }

    public boolean isGroupStartup() {
        return groupStarting;
    }

    /**
     * Demarcate start and end of group startup.
     *
     * All members started with in this duration of time are considered part of GROUP_STARTUP in GMS JoinNotification and
     * JoinAndReadyNotifications. All other members restarted after this duration are INSTANCE_STARTUP.
     *
     * Method is called once when group startup is initiated with <code>INITIATED</code> as startupState. Method is called
     * again when group startup has completed and indication of success or failure is indicated by startupState of
     * <code>COMPLETED_SUCCESS</code> or <code>COMPLATED_FAILED</code>.
     *
     * @param startupState either the start or successful or failed completion of group startup.
     * @param memberTokens list of members associated with <code>startupState</code>
     */
    void groupStartup(GMSConstants.groupStartupState startupState, List<String> memberTokens) {
        StringBuilder sb = new StringBuilder();
        groupStartingMembers = memberTokens;

        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("MasterNode:groupStartup: memebrTokens:" + memberTokens + " startupState:" + startupState);
        }
        switch (startupState) {
        case INITIATED:
            // Optimization: Rather than broadcast to members of cluster (that have not been started yet when INIATIATED group
            // startup),
            // assume this is called by a static administration utility that is running in same process as master node.
            // This call toggles state of whether gms MasterNode is currently part of group startup depending on the value of
            // startupState.
            // See createMasterResponse() for how this group starting is sent from Master to members of the group.
            // See processMasterNodeResponse() for how members process this sent info.
            setGroupStarting(true);
            groupMembers = new TreeSet<String>(memberTokens);
            getGMSContext().setGroupStartupJoinMembers(new TreeSet<String>(memberTokens));
            sb.append(" Starting Members: ");
            pendingGroupStartupMembers = new ConcurrentHashMap<String, Object>();
            for (String member : memberTokens) {
                pendingGroupStartupMembers.put(member, member);
            }
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("groupStartup: pendingGroupStartupMembers:" + pendingGroupStartupMembers.size() + " members:" + pendingGroupStartupMembers.keySet()
                        + " members passed in:" + memberTokens);

            }
            break;
        case COMPLETED_FAILED:

            // todo: communicate to all clustered isntances that they should no longer consider the failed members
            // to be starting within start-cluster. Need to pass failure and list of failed members to all instances.
            // Send groupstartup complete needs to send more info in future. Now just sends start-clsuter is done.
            // Need to send whether succeeded or failed. If failed, need to send list of failing instances so they
            // be removed from pendingJoins list in ViewWindowImpl.
            setGroupStarting(false);
            sb.append(" Failed Members: ");
            if (this.isMaster() && this.isMasterAssigned()) {
                // send a message to other instances in cluster that group startup has completed
                sendGroupStartupComplete();
            }
            break;
        case COMPLETED_SUCCESS:
            if (pendingGroupStartupMembers.size() == 0) {
                setGroupStarting(false);
            }
            sb.append(" Started Members: ");

            break;
        }
        for (String member : memberTokens) {
            sb.append(member).append(",");
        }
        LOG.log(Level.INFO, "mgmt.masternode.groupstartcomplete", new Object[] { getGMSContext().getGroupName(), startupState.toString(), sb.toString() });
    }

    private GMSContext getGMSContext() {
        if (ctx == null) {
            ctx = GMSContextFactory.getGMSContext(manager.getGroupName());
        }
        return ctx;
    }

    class DelayedSetGroupStartingCompleteTask extends TimerTask {
        final private long delay; // millisecs

        public DelayedSetGroupStartingCompleteTask(long delay) {
            this.delay = delay;
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("GroupStartupCompleteTask scheduled in " + delay + " ms");
            }
        }

        public void run() {
            if (delay == MasterNode.MAX_GROUPSTARTING_TIME_MS) {
                LOG.log(Level.WARNING, "mgmt.masternode.missgroupstartupcomplete", new Object[] { MasterNode.MAX_GROUPSTARTING_TIME_MS / 1000 });
            }
            synchronized (timer) {
                setGroupStarting(false);
                groupStartingTask = null;
            }
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("Received GroupStartupComplete for group:" + manager.getGroupName() + " delay(ms)=" + delay);
            }
        }
    }

    static public class MasterNodeMessageEvent implements Comparable {
        public final long seqId;
        public final Message msg;
        public final MessageEvent event;

        public MasterNodeMessageEvent(MessageEvent event) {
            this.event = event;
            msg = event.getMessage();
            seqId = getLongFromMessage(msg, NAMESPACE, MASTERVIEWSEQ);
        }

        public int compareTo(Object o) {
            if (o instanceof MasterNodeMessageEvent) {
                MasterNodeMessageEvent e = (MasterNodeMessageEvent) o;
                int peerCompareToResult = event.getSourcePeerID().compareTo(e.event.getSourcePeerID());

                // CAUTION: note that comparison should include which peerid that the master view sequence id is from.
                // does not make sense to compare between instances.
                if (peerCompareToResult != 0) {
                    return peerCompareToResult;
                } else {
                    return (int) (seqId - ((MasterNodeMessageEvent) o).seqId);
                }
            } else {
                throw new IllegalArgumentException();
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public String toString() {
            StringBuilder result = new StringBuilder(100);
            try {
                if (seqId != -1) {
                    result.append("masterViewSeqId:").append(seqId);
                }
                Object msgElement = msg.getMessageElement(NODEADV);
                String fromInstance = "";
                if (msgElement != null && msgElement instanceof SystemAdvertisement) {
                    fromInstance = ((SystemAdvertisement) msgElement).getName();
                }

                for (Map.Entry<String, Serializable> entry : msg.getMessageElements()) {
                    String key = entry.getKey();
                    if (key.equals(VIEW_CHANGE_EVENT)) {
                        if (entry.getValue() != null && entry.getValue() instanceof ClusterViewEvent) {
                            final ClusterViewEvent cvEvent = (ClusterViewEvent) entry.getValue();
                            result.append(" ViewChangeEvent: ").append(cvEvent.getEvent().toString());
                        }
                    } else if (key.equals(MASTERQUERY)) {
                        result.append("masterquery").append(" from ").append(fromInstance);
                    } else if (key.equals(NODEQUERY)) {
                        result.append("nodequery").append(" from ").append(fromInstance);

                    } else if (key.equals(MASTERNODERESPONSE)) {
                        result.append("masternoderesponse").append(" from ").append(fromInstance);

                    } else if (key.equals(NODERESPONSE)) {
                        result.append("noderesponse").append(" from ").append(fromInstance);

                    }
                }
                msgElement = msg.getMessageElement(AMASTERVIEW);
                if (msgElement != null && msgElement instanceof List) {
                    result.append(" masterview: size:");
                    final List<SystemAdvertisement> newLocalView = (List<SystemAdvertisement>) msgElement;
                    result.append(newLocalView.size());

                    for (SystemAdvertisement adv : newLocalView) {
                        result.append(" ").append(adv.getName());
                    }
                } else {
                    result.append(0);
                }
            } catch (Throwable t) {
                // don't allow any NPEs in this debug aid to cause a failure.
            }
            return result.toString();
        }
    }

    /**
     * Process incoming Master Node messages in sorted order via MasterViewSequenceId. Serializes processing to one at a
     * time.
     */
    public class ProcessOutstandingMessagesTask implements Runnable {

        public ProcessOutstandingMessagesTask() {
        }

        public void run() {
            MasterNodeMessageEvent msg;

            while (manager != null && !manager.isStopping()) {
                msg = null;
                try {
                    synchronized (outstandingMasterNodeMessages) {

                        // Only process one outstanding message at a time. Allow incoming handlers to add new events to be processed.
                        if (outstandingMasterNodeMessages.size() > 0) {
                            msg = outstandingMasterNodeMessages.first();
                            if (msg != null) {
                                outstandingMasterNodeMessages.remove(msg);
                            }
                        } else {
                            outstandingMasterNodeMessages.wait();
                        }
                    } // only synchronize removing first item from set.

                    if (msg != null) {
                        processNextMessageEvent(msg);
                        if (isDiscoveryInProgress() || !isMaster()) {
                            // delay window before processing next message. allow messages received out of order to be ordered.
                            Thread.sleep(30);
                        }
                    }
                } catch (InterruptedException ie) {
                } catch (Throwable t) {
                    LOG.log(Level.WARNING, "mgmt.masternode.processmsgexception", new Object[] { t.getLocalizedMessage() });
                    LOG.log(Level.WARNING, "stack trace", t);
                }
            }
            LOG.log(Level.CONFIG, "mgmt.masternode.processmsgcompleted", new Object[] { instanceName, groupName, outstandingMasterNodeMessages.size() });
        }
    }

    private void reliableBroadcastSend(Message msg) {
        if (reliableMulticast != null) {
            try {
                reliableMulticast.broadcast(msg);
            } catch (IOException ioe) {
                if (mcastLogger.isLoggable(Level.FINE)) {
                    mcastLogger.log(Level.FINE, "unexpected exception in reliablceBroadcastSend", ioe);
                }
            }
        } else {
            send(null, null, msg);
        }
    }

    private void processChangeEventsExpirations() {
        synchronized (processedChangeEvents) {
            Iterator<ProcessedMasterViewId> iter = processedChangeEvents.descendingIterator();
            if (iter.hasNext()) {
                iter.next(); // leave last one always.
            }
            while (iter.hasNext()) {
                ProcessedMasterViewId current = iter.next();
                if (current.isExpired()) {
                    iter.remove();
                }
            }
        }
    }

    private void clearChangeEventsNotFromCurrentMaster(PeerID currentMaster) {
        synchronized (processedChangeEvents) {
            Iterator<ProcessedMasterViewId> iter = processedChangeEvents.iterator();
            while (iter.hasNext()) {
                ProcessedMasterViewId current = iter.next();
                if (!current.master.equals(currentMaster)) {
                    if (mcastLogger.isLoggable(Level.FINE)) {
                        mcastLogger.log(Level.FINE, "expire due to new master: drop history of processing past master change event " + current
                                + " due to new master:" + currentMaster);
                    }
                    iter.remove();
                }
            }
        }
    }

    private PeerID lastCheckedMaster = null;
    private long lastCheckedMasterViewID = -1;

    static final Level DEBUG_TRACE = Level.FINE;

    public List<Long> checkForMissedMasterChangeEvents(PeerID master, long latestMasterViewID) {
        List<Long> missed = new LinkedList<Long>();
        PeerID masterId = getMasterNodeID();
        ProcessedMasterViewId current = null;

        // NOTE: have caller check if this is not the master. junit simulation testing tests this code when junit instance is
        // running as master.
        if (isMasterAssigned() && masterId != null && masterId.equals(master)) {
            boolean entered = false;
            if (lastCheckedMaster == null || lastCheckedMasterViewID != latestMasterViewID) {
                entered = true;
                if (mcastLogger.isLoggable(DEBUG_TRACE)) {
                    mcastLogger.log(DEBUG_TRACE,
                            "enter checkForMissedMasterChangeEvents master:" + master.getInstanceName() + " latestMasterViewSeqID=" + latestMasterViewID);
                }
            }

            // check over all processed master view ids from this sender.
            // can request sender to rebroadcast missing ones.

            // must be ready to receive multiple copies of same MasterViewIdSeq.
            // only the first one received should be processed, repeat ones must be discarded.
            long validateIdx = -1;
            synchronized (this.processedChangeEvents) {

                // check for missed messages by checking for gaps in unchecked processed master view messages.
                SortedSet<ProcessedMasterViewId> tailToProcess;
                if (lastCheckedMasterViewID == -1) {
                    tailToProcess = processedChangeEvents; // initial time use whole array
                    try {
                        validateIdx = processedChangeEvents.first().masterViewIdSeq;
                    } catch (NoSuchElementException e) {
                    }
                } else {
                    final boolean INCLUSIVE_FALSE = false;
                    ProcessedMasterViewId lastProcessed = new ProcessedMasterViewId(master, lastCheckedMasterViewID);
                    tailToProcess = processedChangeEvents.tailSet(lastProcessed, INCLUSIVE_FALSE);
                    validateIdx = lastCheckedMasterViewID + 1;
                }
                Iterator<ProcessedMasterViewId> iter = tailToProcess.iterator();
                while (iter.hasNext()) {
                    current = iter.next();
                    if (current.masterViewIdSeq > latestMasterViewID) {
                        break;
                    }
                    if (mcastLogger.isLoggable(DEBUG_TRACE)) {
                        mcastLogger.log(DEBUG_TRACE, "processedMasterViewID=" + current + " validateIdx=" + validateIdx);
                    }
                    if (validateIdx == current.masterViewIdSeq) {
                        validateIdx++;
                    } else {
                        if (validateIdx > current.masterViewIdSeq) {
                            if (mcastLogger.isLoggable(Level.FINER)) {
                                mcastLogger.log(Level.FINER, "skipping:  validateIdx=" + validateIdx + " greater than current.masterViewIdSeq:"
                                        + current.masterViewIdSeq + " lastCheckedMasterViewID:" + lastCheckedMasterViewID);
                            }
                            continue;
                        }
                        while (validateIdx < current.masterViewIdSeq) {
                            if (mcastLogger.isLoggable(DEBUG_TRACE)) {
                                mcastLogger.fine("first add validateIdx=" + validateIdx + " current.masterViewIdSeq=" + current.masterViewIdSeq);
                            }
                            missed.add(validateIdx);
                            validateIdx++;
                        }
                        validateIdx++;
                    }
                }
            }
            if (validateIdx != -1) {
                while (validateIdx <= latestMasterViewID) {
                    missed.add(validateIdx);
                    if (mcastLogger.isLoggable(DEBUG_TRACE)) {
                        mcastLogger.fine("after add validateIdx=" + validateIdx + " latestMasterViewID=" + latestMasterViewID + " last processed=" + current);
                    }
                    validateIdx++;
                }
            }
            lastCheckedMaster = master;
            if (validateIdx != -1) {
                lastCheckedMasterViewID = latestMasterViewID;
            }

            if (entered) {
                if (missed.size() > 0) {
                    // stats monitor needs to count the number of dropped master change events sent over UDP.
                    if (mcastLogger.isLoggable(Level.FINE)) {
                        mcastLogger.log(Level.FINE, "mgmt.masternode.missed.change.events",
                                new Object[] { master.getInstanceName(), latestMasterViewID, missed, lastCheckedMasterViewID });
                    }
                } else {
                    if (mcastLogger.isLoggable(DEBUG_TRACE)) {
                        mcastLogger.log(DEBUG_TRACE, "exit checkForMissedMasterChangeEvents master:" + master.getInstanceName() + " latestMasterViewSeqID="
                                + latestMasterViewID + " lastCheckedMasterViewID:" + lastCheckedMasterViewID);
                    }
                }
            }
        }
        return missed;
    }

    public void sendResendRequest(PeerID master, List<Long> missed) {
        Message msg = createResendRequest(master, missed);
        send(master, null, msg);
    }

    static public class ProcessedMasterViewId implements Comparable {
        public final PeerID master;
        public final long masterViewIdSeq;
        public final long receivedTime;
        public final long expirationTime;

        public static final long EXPIRATION_DURATION_MS = 30 * 1000;

        public String toString() {
            return "ProcessedMasterViewId master:" + master.getInstanceName() + " masterViewSequenceID=" + masterViewIdSeq + " receivedTime="
                    + new Date(receivedTime);
        }

        public ProcessedMasterViewId(PeerID master, long seqId) {
            this(master, seqId, EXPIRATION_DURATION_MS);
        }

        public ProcessedMasterViewId(PeerID master, long seqId, long expire_duration_ms) {
            this.master = master;
            this.masterViewIdSeq = seqId;
            receivedTime = System.currentTimeMillis();
            expirationTime = receivedTime + expire_duration_ms;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expirationTime;
        }

        @Override
        public int compareTo(Object o) {
            int result;
            if (o instanceof ProcessedMasterViewId) {
                ProcessedMasterViewId e = (ProcessedMasterViewId) o;
                result = master.compareTo(e.master);
                if (result == 0) {
                    result = (int) (masterViewIdSeq - e.masterViewIdSeq);
                }
                return result;
            } else {
                throw new IllegalArgumentException();
            }
        }

        @Override
        public int hashCode() {
            int result = master != null ? master.hashCode() : 0;
            result = 31 * result + (int) (masterViewIdSeq ^ (masterViewIdSeq >>> 32));
            return result;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            ProcessedMasterViewId that = (ProcessedMasterViewId) o;

            if (masterViewIdSeq != that.masterViewIdSeq) {
                return false;
            }
            if (master != null ? !master.equals(that.master) : that.master != null) {
                return false;
            }

            return true;
        }
    }

    static private class CheckForMissedMasterMessages implements Runnable {
        private final PeerID master;
        private final long masterViewSeqId;
        private final MasterNode masterNode;
        static private final long EXPIRE_REAPER_FREQUENCY_MS = 20 * 1000;
        private long nextExpireReaperTime;

        CheckForMissedMasterMessages(MasterNode masterNode, PeerID master, long masterViewSeqId) {
            this.masterNode = masterNode;
            this.master = master;
            this.masterViewSeqId = masterViewSeqId;
            this.nextExpireReaperTime = System.currentTimeMillis() + EXPIRE_REAPER_FREQUENCY_MS;
        }

        public void run() {
            if (!masterNode.isMaster() && masterNode.isMasterAssigned()) {
                List<Long> missed = masterNode.checkForMissedMasterChangeEvents(master, masterViewSeqId);
                if (missed.size() > 0) {
                    masterNode.sendResendRequest(master, missed);
                }
            }
            long currentTime = System.currentTimeMillis();
            if (currentTime > nextExpireReaperTime) {
                masterNode.processChangeEventsExpirations();
                nextExpireReaperTime = currentTime + EXPIRE_REAPER_FREQUENCY_MS;
            }
        }
    }

    private void setMaster(SystemAdvertisement adv, boolean notify) {
        boolean newMaster = false;
        synchronized (this) {
            newMaster = clusterViewManager.setMaster(adv, notify);
            masterAssigned = true;
        }
        if (masterLogger.isLoggable(Level.FINE)) {
            masterLogger.log(Level.FINE, "setMaster isNewMaster=" + newMaster + " master=" + adv.getName() + " newMaster=" + newMaster);
        }
        if (newMaster) {
            this.lastCheckedMasterViewID = -1;
            this.lastCheckedMaster = null;
            clearChangeEventsNotFromCurrentMaster(adv.getID());
        }
    }

    // only the master adds its latest master view sequence id to its heartbeat
    void addMasterViewSeqId(Message msg) {
        if (isMaster() && isMasterAssigned() && !isDiscoveryInProgress()) {
            // have master include its current MasterViewSeqID.
            // this allows other members in cluster to check if they have received all master change events.
            // if they have not, they can request a rebroadcast.
            msg.addMessageElement(LATESTMASTERVIEWID, getMasterViewID());
        }
    }

    // the master broadcasts its latest masterViewSeqId to its group members via its heartbeat.
    // thus, check if this heartbeat message has the LATESTMASTERVIEWSEQID on it.
    // if it does, then all none masters will calculate from their cache of processed master change events if they
    // missed any messages.
    void processForLatestMasterViewId(Message msg, PeerID source) {
        if (!isMaster() && masterAssigned && !isDiscoveryInProgress()) {
            Object element = msg.getMessageElement(this.LATESTMASTERVIEWID);
            if (element != null && element instanceof Long) {
                checkForMissedMasterMsgSingletonExecutor.submit(new CheckForMissedMasterMessages(this, source, (Long) element));
            }
        }
    }

    /**
     * returns the source peer id of a message
     *
     * @param msg message
     * @return The source value
     */
    static long getMasterViewSequenceID(Message msg) {
        long result = -1;
        Object value = msg.getMessageElement(MasterNode.MASTERVIEWSEQ);
        if (value instanceof Long) {
            result = (Long) value;
        }
        return result;
    }
}
