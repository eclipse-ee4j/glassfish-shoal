/*
 * Copyright (c) 1997, 2018 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.shoal.gms.mgmt;

import java.io.IOException;
import java.io.Serializable;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.shoal.gms.api.core.GMSConstants;
import org.glassfish.shoal.gms.api.core.GMSException;
import org.glassfish.shoal.gms.api.core.GroupManagementService;
import org.glassfish.shoal.gms.api.core.MemberNotInViewException;
import org.glassfish.shoal.gms.base.CustomTagNames;
import org.glassfish.shoal.gms.base.PeerID;
import org.glassfish.shoal.gms.base.SystemAdvertisement;
import org.glassfish.shoal.gms.base.SystemAdvertisementImpl;
import org.glassfish.shoal.gms.base.Utility;
import org.glassfish.shoal.gms.logging.GMSLogDomain;
import org.glassfish.shoal.gms.mgmt.transport.AbstractNetworkManager;
import org.glassfish.shoal.gms.mgmt.transport.Message;
import org.glassfish.shoal.gms.mgmt.transport.MessageEvent;
import org.glassfish.shoal.gms.mgmt.transport.MessageIOException;
import org.glassfish.shoal.gms.mgmt.transport.MessageImpl;
import org.glassfish.shoal.gms.mgmt.transport.MessageListener;
import org.glassfish.shoal.gms.mgmt.transport.NetworkManager;
import org.glassfish.shoal.gms.mgmt.transport.NetworkUtility;

/**
 * The ClusterManager is the entry point for using the cluster management module which provides group communications and
 * membership semantics on top of JXTA, Grizzly and others.
 */
public class ClusterManager implements MessageListener {
    private static final Logger LOG = GMSLogDomain.getLogger(GMSLogDomain.GMS_LOGGER);
    private MasterNode masterNode = null;
    private ClusterViewManager clusterViewManager = null;
    private HealthMonitor healthMonitor = null;
    private NetworkManager netManager = null;
    private String groupName = null;
    private String instanceName = null;
    private String bindInterfaceAddress = null;
    private volatile boolean started = false;
    private volatile boolean stopped = true;
    private boolean loopbackMessages = false;
    private final Object closeLock = new Object();
    private SystemAdvertisement systemAdv = null;

    private static final String NODEADV = "NAD";
    private Map<String, String> identityMap;
    private static final String APPMESSAGE = "APPMESSAGE";
    private List<ClusterMessageListener> cmListeners;
    private volatile boolean stopping = false;

    final Object MASTERBYFORCELOCK = new Object();
    final private String memberType;
    final String gmsContextProviderTransport;

    /**
     * The ClusterManager is created using the instanceName, and a Properties object that contains a set of parameters that
     * the employing application would like to set for its purposes, namely, configuration parameters such as failure
     * detection timeout, retries, address and port on which to communicate with the group, group and instance IDs,etc. The
     * set of allowed constants to be used as keys in the Properties object are specified in JxtaConfigConstants enum.
     *
     * @param groupName Name of Group to which this process/peer seeks membership
     * @param instanceName A token identifying this instance/process
     * @param identityMap Additional identity tokens can be specified through this Map object. These become a part of the
     * SystemAdvertisement allowing the peer/system to be identified by the application layer based on their own notion of
     * identity
     * @param props a Properties object containing parameters that are allowed to be specified by employing application
     * //TODO: specify that INFRA IDs and address/port are composite keys in essence but addresses/ports could be shared
     * across ids with a performance penalty. //TODO: provide an API to send messages, synchronously or asynchronously
     * @param viewListeners listeners interested in group view change events
     * @param messageListeners listeners interested in receiving messages.
     * @throws GMSException Generic exception for handling errors
     */
    public ClusterManager(final String groupName, final String instanceName, final Map<String, String> identityMap, final Map props,
            final List<ClusterViewEventListener> viewListeners, final List<ClusterMessageListener> messageListeners) throws GMSException {
        this.memberType = identityMap.get(CustomTagNames.MEMBER_TYPE.toString());
        this.groupName = groupName;
        this.instanceName = instanceName;
        this.loopbackMessages = isLoopBackEnabled(props);
        // TODO: ability to specify additional rendezvous and also bootstrap a default rendezvous
        // TODO: revisit and document auto composition of transports

        gmsContextProviderTransport = Utility.getStringProperty("SHOAL_GROUP_COMMUNICATION_PROVIDER", GMSConstants.GROUP_COMMUNICATION_PROVIDER, props);
        this.netManager = getNetworkManager(gmsContextProviderTransport);
        LOG.config("instantiated following NetworkManager implementation:" + netManager.getClass().getName());

        this.identityMap = identityMap;
        try {
            netManager.initialize(groupName, instanceName, props);
            netManager.start();
        } catch (IOException ioe) {
            throw new GMSException("initialization failure", ioe);
        } catch (IllegalStateException ise) {
            throw new GMSException("initialization failure", ise);
        }
        // NetworkManagerRegistry.add(groupName, netManager);
        if (props != null && !props.isEmpty()) {
            this.bindInterfaceAddress = (String) props.get(ConfigConstants.BIND_INTERFACE_ADDRESS.toString());
        }
        systemAdv = createSystemAdv(netManager.getLocalPeerID(), instanceName, identityMap, bindInterfaceAddress);
        if (LOG.isLoggable(Level.FINER)) {
            LOG.log(Level.FINER, "Instance ID :" + getSystemAdvertisement().getID());
        }
        if (isWatchdog()) {
            this.clusterViewManager = null;
            this.masterNode = null;
        } else {
            this.clusterViewManager = new ClusterViewManager(getSystemAdvertisement(), this, viewListeners);
            this.masterNode = new MasterNode(this, getDiscoveryTimeout(props), 1, props);
        }

        this.healthMonitor = new HealthMonitor(this, getFailureDetectionTimeout(props), getFailureDetectionRetries(props), getVerifyFailureTimeout(props),
                getFailureDetectionTcpRetransmitTimeout(props), getFailureDetectionTcpRetransmitPort(props));

        cmListeners = messageListeners;
    }

    public boolean isWatchdog() {
        return GroupManagementService.MemberType.WATCHDOG.toString().equals(memberType);
    }

    private boolean isLoopBackEnabled(final Map props) {
        boolean LOOPBACK_DEFAULT = false;
        return Utility.getBooleanProperty(ConfigConstants.LOOPBACK.toString(), LOOPBACK_DEFAULT, props);
    }

    private long getDiscoveryTimeout(Map props) {
        long DISCOVERY_TIMEOUT_DEFAULT = 5000; // milliseconds or 5 seconds.
        return Utility.getLongProperty(ConfigConstants.DISCOVERY_TIMEOUT.toString(), DISCOVERY_TIMEOUT_DEFAULT, props);
    }

    private long getFailureDetectionTimeout(Map props) {
        long DEFAULT_FAILURE_DETECTION_TIMEOUT = 3000;
        return Utility.getLongProperty(ConfigConstants.FAILURE_DETECTION_TIMEOUT.toString(), DEFAULT_FAILURE_DETECTION_TIMEOUT, props);
    }

    private int getFailureDetectionRetries(Map props) {
        int DEFAULT_FAILURE_RETRY = 3;
        return Utility.getIntProperty(ConfigConstants.FAILURE_DETECTION_RETRIES.toString(), DEFAULT_FAILURE_RETRY, props);
    }

    private long getFailureDetectionTcpRetransmitTimeout(Map props) {
        long DEFAULT_FAIL_TCP_TIMEOUT = 10000; // sailfin requirement to discover network outage under 30 seconds.
        // fix for sailfin 626.
        // HealthMonitor.isConnected() is called twice and must time out twice,
        // using 20 seconds.
        // indoubt detection and failure verification takes 8-10 seconds.
        return Utility.getLongProperty(ConfigConstants.FAILURE_DETECTION_TCP_RETRANSMIT_TIMEOUT.toString(), DEFAULT_FAIL_TCP_TIMEOUT, props);
    }

    private int getFailureDetectionTcpRetransmitPort(Map props) {
        int DEFAULT_FAIL_TCP_PORT = 9000;
        return Utility.getIntProperty(ConfigConstants.FAILURE_DETECTION_TCP_RETRANSMIT_PORT.toString(), DEFAULT_FAIL_TCP_PORT, props);
    }

    private long getVerifyFailureTimeout(Map props) {
        long DEFAULT_VERIFY_TIMEOUT = 2000;
        return Utility.getLongProperty(ConfigConstants.FAILURE_VERIFICATION_TIMEOUT.toString(), DEFAULT_VERIFY_TIMEOUT, props);
    }

    public void addClusteMessageListener(final ClusterMessageListener listener) {
        cmListeners.add(listener);
    }

    public void removeClusterViewEventListener(final ClusterMessageListener listener) {
        cmListeners.remove(listener);
    }

    /**
     * Stops the ClusterManager and all it's services
     *
     * @param isClusterShutdown true if this peer is shutting down as part of cluster wide shutdown
     */
    public synchronized void stop(final boolean isClusterShutdown) {
        if (!stopped) {
            stopping = true;
            healthMonitor.stop(isClusterShutdown);
            if (!isWatchdog()) {
                masterNode.stop();
            }
            netManager.removeMessageListener(this);
            try {
                netManager.stop();
            } catch (IOException e) {
            }
            stopped = true;
            synchronized (closeLock) {
                closeLock.notifyAll();
            }
        }
    }

    /**
     * Starts the ClusterManager and all it's services
     */
    public synchronized void start() {
        if (!started) {
            netManager.addMessageListener(this);
            if (!isWatchdog()) {
                masterNode.start();
            }
            healthMonitor.start();
            started = true;
            stopped = false;
        }
    }

    /**
     * Returns the NetworkManager instance
     *
     * @param transport The type of transport
     *
     * @return The networkManager value
     */
    public NetworkManager getNetworkManager(String transport) {
        if (netManager == null) {
            netManager = AbstractNetworkManager.getInstance(transport);
        }
        return netManager;
    }

    public NetworkManager getNetworkManager() {
        return getNetworkManager(gmsContextProviderTransport);
    }

    /**
     * Returns the MasterNode instance
     *
     * @return The masterNode value
     */
    public MasterNode getMasterNode() {
        return masterNode;
    }

    /**
     * Returns the HealthMonitor instance.
     *
     * @return The healthMonitor value
     */
    public HealthMonitor getHealthMonitor() {
        return healthMonitor;
    }

    /**
     * Returns the ClusterViewManager object. All modules use this common object which represents a synchronized view of a
     * set of AppServers
     *
     * @return The clusterViewManager object
     */
    public ClusterViewManager getClusterViewManager() {
        return clusterViewManager;
    }

    public PeerID getPeerID() {
        return netManager.getLocalPeerID();
    }

    /**
     * Gets the instance name
     *
     * @return The instance name
     */
    public String getInstanceName() {
        return instanceName;
    }

    public boolean isMaster() {
        return clusterViewManager.isMaster() && masterNode.isMasterAssigned();
    }

    /**
     * Send a message to a specific node or the group. In the case where the id is null the message is sent to the entire
     * group
     *
     * @param peerid the node ID
     * @param msg the message to send
     * @param validatePeeridInView the flag to validate the peer id
     * @return boolean <code>true</code> if the message has been sent otherwise <code>false</code>. <code>false</code>. is
     * commonly returned for non-error related congestion, meaning that you should be able to send the message after waiting
     * some amount of time.
     * @throws java.io.IOException if an io error occurs
     * @throws MemberNotInViewException if the peer id is not in view
     */
    public boolean send(final PeerID peerid, final Serializable msg, boolean validatePeeridInView) throws IOException, MemberNotInViewException {
        boolean sent = false;
        if (!stopping) {
            final Message message = new MessageImpl(Message.TYPE_CLUSTER_MANAGER_MESSAGE);
            message.addMessageElement(NODEADV, systemAdv);
            message.addMessageElement(APPMESSAGE, msg);

            if (peerid != null) {
                // check if the peerid is part of the cluster view
                if (!validatePeeridInView || getClusterViewManager().containsKey(peerid, true)) {
                    if (LOG.isLoggable(Level.FINE) && validatePeeridInView) {
                        LOG.fine("ClusterManager.send : Cluster View contains " + peerid.toString());
                    }
                    sent = netManager.send(peerid, message);
                    if (!sent) {
                        LOG.log(Level.WARNING, "mgmt.clustermanager.send.failed", new Object[] { message, peerid });
                    }
                } else {
                    LOG.log(Level.INFO, "mgmt.clustermanager.send.membernotinview", new Object[] { peerid.toString() });

                    // todo I18N?
                    throw new MemberNotInViewException("Member " + peerid + " is not in the View anymore. Hence not performing sendMessage operation");
                }
            } else {
                // multicast
                LOG.log(Level.FINER, "Broadcasting Message");
                sent = netManager.broadcast(message);
                if (!sent) {
                    LOG.log(Level.WARNING, "mgmt.clustermanager.broadcast.failed", new Object[] { message });
                }
            }
        }
        return sent;
    }

    public boolean send(final PeerID peerid, final Serializable msg) throws IOException, MemberNotInViewException {
        return send(peerid, msg, true);
    }

    /**
     * {@inheritDoc}
     */
    public void receiveMessageEvent(final MessageEvent event) throws MessageIOException {
        if (started && !stopping) {
            final Message msg;
            Object msgElement;
            // grab the message from the event
            try {
                msg = event.getMessage();
                if (msg == null) {
                    LOG.log(Level.WARNING, "mgmt.clustermanager.nullmessage");
                    return;
                }
                // JxtaUtil.printMessageStats(msg, true);
                LOG.log(Level.FINEST, "ClusterManager:Received a AppMessage ");
                msgElement = msg.getMessageElement(NODEADV);
                if (msgElement == null) {
                    // no need to go any further
                    LOG.log(Level.WARNING, "mgmt.unknownMessage");
                    return;
                }

                final SystemAdvertisement adv;
                if (msgElement instanceof SystemAdvertisement) {
                    adv = (SystemAdvertisement) msgElement;
                } else {
                    LOG.log(Level.WARNING, "mgmt.unknownMessage");
                    return;
                }
                final PeerID srcPeerID = adv.getID();
                if (!loopbackMessages) {
                    if (srcPeerID.equals(getPeerID())) {
                        LOG.log(Level.FINEST, "CLUSTERMANAGER:Discarding loopback message");
                        return;
                    }
                }

                msgElement = msg.getMessageElement(APPMESSAGE);
                if (msgElement != null) {
                    if (LOG.isLoggable(Level.FINEST)) {
                        LOG.log(Level.FINEST, "ClusterManager: Notifying APPMessage Listeners of " + msgElement.toString() + "and adv = " + adv.getName());
                    }
                    notifyMessageListeners(adv, msgElement);
                }
            } catch (Throwable e) {
                LOG.log(Level.WARNING, e.getLocalizedMessage());
            }
        }
    }

    public int getType() {
        return Message.TYPE_CLUSTER_MANAGER_MESSAGE;
    }

    private void notifyMessageListeners(final SystemAdvertisement senderSystemAdvertisement, final Object appMessage) {
        for (ClusterMessageListener listener : cmListeners) {
            listener.handleClusterMessage(senderSystemAdvertisement, appMessage);
        }
    }

    public SystemAdvertisement getSystemAdvertisementForMember(final PeerID id) {
        return clusterViewManager.get(id);
    }

    /**
     * Gets the systemAdvertisement attribute of the JXTAPlatform object
     *
     * @return The systemAdvertisement value
     */
    public SystemAdvertisement getSystemAdvertisement() {
        if (systemAdv == null) {
            systemAdv = createSystemAdv(netManager.getLocalPeerID(), instanceName, identityMap, bindInterfaceAddress);
        }
        return systemAdv;
    }

    /**
     * Given a peergroup and a SystemAdvertisement is returned
     *
     * @param peerID peer id
     * @param name host name
     * @param customTags A Map object. These are additional system identifiers that the application layer can provide for
     * its own identification.
     * @param bindInterfaceAddress bind interface address
     * @return SystemAdvertisement object
     */
    private static synchronized SystemAdvertisement createSystemAdv(final PeerID peerID, final String name, final Map<String, String> customTags,
            final String bindInterfaceAddress) {
        if (peerID == null) {
            throw new IllegalArgumentException("peer id can not be null");
        }
        if (name == null) {
            throw new IllegalArgumentException("instance name can not be null");
        }
        final SystemAdvertisement sysAdv = new SystemAdvertisementImpl();
        sysAdv.setID(peerID);
        sysAdv.setName(name);
        sysAdv.setOSName(System.getProperty("os.name"));
        sysAdv.setOSVersion(System.getProperty("os.version"));
        sysAdv.setOSArch(System.getProperty("os.arch"));
        sysAdv.setHWArch(System.getProperty("HOSTTYPE", System.getProperty("os.arch")));
        sysAdv.setHWVendor(System.getProperty("java.vm.vendor"));
        sysAdv.setCustomTags(customTags);
        setBindInterfaceAddress(sysAdv, bindInterfaceAddress);
        return sysAdv;
    }

    static private void setBindInterfaceAddress(SystemAdvertisement sysAdv, String bindInterfaceAddress) {
        String bindInterfaceEndpointAddress = null;
        final String TCP_SCHEME = "tcp://";
        final String PORT = ":4000"; // necessary to add a port but its value is ignored.
        if (bindInterfaceAddress != null && !bindInterfaceAddress.equals("")) {
            InetAddress inetAddress = null;
            try {
                inetAddress = NetworkUtility.resolveBindInterfaceName(bindInterfaceAddress);
                if (inetAddress instanceof Inet6Address) {
                    bindInterfaceEndpointAddress = TCP_SCHEME + "[" + bindInterfaceAddress + "]" + PORT;
                } else {
                    bindInterfaceEndpointAddress = TCP_SCHEME + bindInterfaceAddress + PORT;
                }
            } catch (Exception e) {
                LOG.log(Level.WARNING, "mgmt.clustermanager.invalidbindinterfaceaddress",
                        new Object[] { ConfigConstants.BIND_INTERFACE_ADDRESS.toString(), bindInterfaceAddress });
            }
        }
        if (bindInterfaceEndpointAddress != null) {
            if (LOG.isLoggable(Level.CONFIG)) {
                LOG.config("Configured bindInterfaceEndpointAddress URI " + bindInterfaceEndpointAddress + " using property "
                        + ConfigConstants.BIND_INTERFACE_ADDRESS.toString() + " value=" + bindInterfaceAddress);
            }
            sysAdv.addEndpointAddress(bindInterfaceEndpointAddress);
        } else {
            // lookup all public addresses
            List<InetAddress> localAddressList = NetworkUtility.getAllLocalAddresses();
            for (InetAddress inetAddress : localAddressList) {
                if (inetAddress instanceof Inet6Address) {
                    bindInterfaceEndpointAddress = TCP_SCHEME + "[" + inetAddress.getHostAddress() + "]" + PORT;
                } else {
                    bindInterfaceEndpointAddress = TCP_SCHEME + inetAddress.getHostAddress() + PORT;
                }
                sysAdv.addEndpointAddress(bindInterfaceEndpointAddress);
            }
        }
    }

    public String getNodeState(final PeerID peerID, long threshold, long timeout) {
        return getHealthMonitor().getMemberState(peerID, threshold, timeout);
    }

    /**
     * Returns name encoded ID
     *
     * @param name to name to encode
     * @return name encoded ID
     */
    public PeerID getID(final String name) {
        return netManager.getPeerID(name);
    }

    boolean isStopping() {
        return stopping;
    }

    public void takeOverMasterRole() {
        masterNode.takeOverMasterRole();
        // wait until the new Master gets forcefully appointed
        // before processing any further requests.
        waitFor(2000);
    }

    public void setClusterStopping() {
        masterNode.setClusterStopping();
    }

    public void waitFor(long msec) {
        try {
            synchronized (MASTERBYFORCELOCK) {
                MASTERBYFORCELOCK.wait(msec);
            }
        } catch (InterruptedException intr) {
            Thread.interrupted();
            LOG.log(Level.FINER, "Thread interrupted", intr);
        }
    }

    public void notifyNewMaster() {
        synchronized (MASTERBYFORCELOCK) {
            MASTERBYFORCELOCK.notifyAll();
        }
    }

    public void reportJoinedAndReadyState() {
        healthMonitor.reportJoinedAndReadyState();
    }

    public void groupStartup(GMSConstants.groupStartupState startupState, List<String> memberTokens) {
        getMasterNode().groupStartup(startupState, memberTokens);
    }

    public boolean isGroupStartup() {
        return getMasterNode().isGroupStartup();
    }

    public String getGroupName() {
        return groupName;
    }

    public boolean isDiscoveryInProgress() {
        return masterNode.isDiscoveryInProgress();
    }
}
