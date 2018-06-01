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

package com.sun.enterprise.mgmt.transport.grizzly;

import static com.sun.enterprise.mgmt.ConfigConstants.*;
import static com.sun.enterprise.mgmt.transport.grizzly.GrizzlyConfigConstants.*;

import com.sun.enterprise.ee.cms.core.GMSConstants;
import com.sun.enterprise.ee.cms.logging.GMSLogDomain;
import com.sun.enterprise.mgmt.HealthMessage;
import com.sun.enterprise.mgmt.HealthMonitor;
import com.sun.enterprise.mgmt.transport.*;

import com.sun.enterprise.ee.cms.impl.base.PeerID;
import com.sun.enterprise.ee.cms.impl.base.Utility;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author Bongjae Chang
 */
public abstract class GrizzlyNetworkManager extends AbstractNetworkManager {

    // too many protocol warnings/severe when trying to communicate to a stopped/killed member of cluster.
    // only logger to shoal logger when necessary to debug grizzly transport within shoal.  don't leave this way.
    public final Logger LOG;
    public final Logger nomcastLogger;

    public final ConcurrentHashMap<String, PeerID<GrizzlyPeerID>> peerIDMap =
            new ConcurrentHashMap<String, PeerID<GrizzlyPeerID>>();

    public volatile boolean running;
    public MessageSender tcpSender;
    public MessageSender udpSender;
    public MulticastMessageSender multicastSender;
    public int multicastTimeToLive;


    public String instanceName;
    public String groupName;
    public String host;
    public int tcpPort;
    public int tcpStartPort;
    public int tcpEndPort;
    public int multicastPort;
    public String multicastAddress;
    public String networkInterfaceName;
    public long failTcpTimeout; // ms

    protected int highWaterMark;
    protected int numberToReclaim;
    protected int maxParallelSendConnections;
    
    public long startTimeoutMillis; // ms
    public long sendWriteTimeoutMillis; // ms
    public int multicastPacketSize;
    public int writeSelectorPoolSize;
    static final public String UNKNOWN = "Unknown_";

    static final public String DEFAULT_IPv4_MULTICAST_ADDRESS = "230.30.1.1";
    static final public String DEFAULT_IPv6_MULTICAST_ADDRESS = "FF01:0:0:0:0:0:0:1";
    public final ConcurrentHashMap<PeerID, CountDownLatch> pingMessageLockMap = new ConcurrentHashMap<PeerID, CountDownLatch>();

    protected VirtualMulticastSender vms = null;
    protected boolean disableMulticast = false;
    protected String virtualUriList;

    public GrizzlyNetworkManager() {

       LOG = GMSLogDomain.getLogger(GMSLogDomain.GMS_LOGGER);
       nomcastLogger = GMSLogDomain.getNoMCastLogger();
    }

    private boolean validMulticastAddress(String multicastAddr) {
        InetAddress validateMulticastAddress = null;
        try {
            validateMulticastAddress = InetAddress.getByName(multicastAddress);
        } catch (UnknownHostException e) { }

        return validateMulticastAddress != null && validateMulticastAddress.isMulticastAddress();
    }

    @SuppressWarnings( "unchecked" )
    public void configure( final Map properties ) {
        Logger shoalLogger = getLogger();
        host = Utility.getStringProperty( BIND_INTERFACE_ADDRESS.toString(), null, properties );
        tcpStartPort = Utility.getIntProperty( TCPSTARTPORT.toString(), 9090, properties );
        tcpEndPort = Utility.getIntProperty( TCPENDPORT.toString(), 9200, properties );

        // allow grizzly to select port from port range. Grizzly will keep hold of port,
        // preventing other gms clients running at same time from picking same port.
        // tcpPort = NetworkUtility.getAvailableTCPPort( host, tcpStartPort, tcpEndPort );

        multicastPort = Utility.getIntProperty( MULTICASTPORT.toString(), 9090, properties );
        final String DEFAULT_MULTICAST_ADDRESS = NetworkUtility.getPreferIpv6Addresses() ?
                                           DEFAULT_IPv6_MULTICAST_ADDRESS : DEFAULT_IPv4_MULTICAST_ADDRESS;
        multicastAddress = Utility.getStringProperty( MULTICASTADDRESS.toString(), DEFAULT_MULTICAST_ADDRESS, properties );
        if (!validMulticastAddress(multicastAddress)) {
            shoalLogger.log(Level.SEVERE, "grizzlynetmgr.invalidmcastaddr",
                    new Object[]{multicastAddress, DEFAULT_MULTICAST_ADDRESS});
            multicastAddress = DEFAULT_MULTICAST_ADDRESS;
        }
        if (host != null) {
            try {
                InetAddress inetAddr = null;
                NetworkInterface ni = null;
                inetAddr = NetworkUtility.resolveBindInterfaceName(host);
                if (ni == null) {
                    ni = NetworkInterface.getByInetAddress(inetAddr);
                }
                if (ni != null) {
                    networkInterfaceName = ni.getName();
                }
                if (inetAddr != null) {
                    host = inetAddr.getHostAddress();
                    properties.put(BIND_INTERFACE_ADDRESS.toString(), host);
                }
            } catch (SocketException ex) {
                shoalLogger.log(Level.WARNING, "grizzlynetmgr.invalidbindaddr", new Object[]{ex.getLocalizedMessage()});
            }
        }
        failTcpTimeout = Utility.getLongProperty( FAILURE_DETECTION_TCP_RETRANSMIT_TIMEOUT.toString(), 10 * 1000, properties );

        highWaterMark = Utility.getIntProperty( HIGH_WATER_MARK.toString(), 1024, properties );
        numberToReclaim = Utility.getIntProperty( NUMBER_TO_RECLAIM.toString(), 10, properties );
        maxParallelSendConnections = Utility.getIntProperty( MAX_PARALLEL.toString(), 15, properties );

        startTimeoutMillis = Utility.getLongProperty( START_TIMEOUT.toString(), 15 * 1000, properties );
        sendWriteTimeoutMillis = Utility.getLongProperty( WRITE_TIMEOUT.toString(), 10 * 1000, properties );
        multicastPacketSize = Utility.getIntProperty( MULTICAST_PACKET_SIZE.toString(), 64 * 1024, properties );
        multicastTimeToLive = Utility.getIntProperty(MULTICAST_TIME_TO_LIVE.toString(),
                                      GMSConstants.DEFAULT_MULTICAST_TIME_TO_LIVE, properties);
        writeSelectorPoolSize = Utility.getIntProperty( MAX_WRITE_SELECTOR_POOL_SIZE.toString(), 30, properties );
        virtualUriList = Utility.getStringProperty(DISCOVERY_URI_LIST.toString(), null, properties);
        if (virtualUriList != null) {
            nomcastLogger.log(Level.CONFIG, "mgmt.disableUDPmulticast", new Object[]{DISCOVERY_URI_LIST.toString(), virtualUriList});
            disableMulticast = true;
        }
        if (shoalLogger.isLoggable(Level.CONFIG)) {
            String multicastTTLresults = multicastTimeToLive == GMSConstants.DEFAULT_MULTICAST_TIME_TO_LIVE ?
                    " default" : Integer.toString(multicastTimeToLive);
            StringBuilder buf = new StringBuilder(256);
            buf.append("\n");
            buf.append(this.getClass().getSimpleName());
            buf.append(" Configuration\n");
            buf.append("BIND_INTERFACE_ADDRESS:").append(host).append("  NetworkInterfaceName:").append(networkInterfaceName).append('\n');
            buf.append("TCPSTARTPORT..TCPENDPORT:").append(tcpStartPort).append("..").append(tcpEndPort).append('\n');
            buf.append("MULTICAST_ADDRESS:MULTICAST_PORT:").append(multicastAddress).append(':').append(multicastPort)
                     .append(" MULTICAST_PACKET_SIZE:").append(multicastPacketSize)
                     .append(" MULTICAST_TIME_TO_LIVE:").append(multicastTTLresults).append('\n');
            buf.append("FAILURE_DETECT_TCP_RETRANSMIT_TIMEOUT(ms):").append(failTcpTimeout).append('\n');
            buf.append(" MAX_PARALLEL:").append(maxParallelSendConnections).append('\n');
            buf.append("START_TIMEOUT(ms):").append(startTimeoutMillis).append(" WRITE_TIMEOUT(ms):").append(sendWriteTimeoutMillis).append('\n');
            buf.append("MAX_WRITE_SELECTOR_POOL_SIZE:").append(writeSelectorPoolSize).append('\n');
            shoalLogger.log(Level.CONFIG, buf.toString());
        }
    }

    @Override
    @SuppressWarnings( "unchecked" )
    public synchronized void initialize( final String groupName, final String instanceName, final Map properties ) throws IOException {
        super.initialize(groupName, instanceName, properties);
    }


    @Override
    @SuppressWarnings( "unchecked" )
    public synchronized void start() throws IOException {
        // TBD: consider if code can be share here or not between grizzly 1.9 and 2.0 transport containers.
    }


    @Override
    public void stop() throws IOException {
        super.stop();
    }

    @Override
    public void beforeDispatchingMessage( MessageEvent messageEvent, Map piggyback ) {
    }

    @Override
    public void afterDispatchingMessage( MessageEvent messageEvent, Map piggyback ) {
    }

    @Override
    @SuppressWarnings( "unchecked" )
    public void addRemotePeer( PeerID peerID ) {
        if( peerID == null )
            return;
        if( peerID.equals( localPeerID ) )
            return; // lookback
        String instanceName = peerID.getInstanceName();
        if( instanceName != null && peerID.getUniqueID() instanceof GrizzlyPeerID ) {
//            PeerID<GrizzlyPeerID> previous = peerIDMap.get(instanceName);
//            if (previous != null) {
//                if (previous.getUniqueID().getTcpPort() != ((GrizzlyPeerID) peerID.getUniqueID()).tcpPort) {
//                    LOG.log(Level.WARNING, "addRemotePeer: assertion failure: no mapping should have existed for member:"
//                            + instanceName + " existingID=" + previous + " adding peerid=" + peerID, new Exception("stack trace"));
//                }
//            }
            PeerID<GrizzlyPeerID> previous = peerIDMap.put( instanceName, peerID );
            if (previous == null) {
                if (nomcastLogger.isLoggable(Level.FINE)) {
                    nomcastLogger.log(Level.FINE, "addRemotePeer: " + instanceName + " peerId:" + peerID, new Exception("stack trace"));
                }
            }
        }
        addToVMS(peerID);
    }

    public void removeRemotePeer(String instanceName) {
    }

    @Override
    public boolean send( final PeerID peerID, final Message message ) throws IOException {
        if( !running )
            throw new IOException( "network manager is not running" );
        MessageSender sender = tcpSender;
        if( sender == null )
            throw new IOException( "message sender is not initialized" );
        return sender.send( peerID, message );
    }

    @Override
    public boolean broadcast( final Message message ) throws IOException {
        if( !running )
            throw new IOException( "network manager is not running" );
        MulticastMessageSender sender = multicastSender;
        if( sender == null )
            throw new IOException( "multicast message sender is not initialized" );
        return sender.broadcast( message );
    }

    @Override
    public PeerID getPeerID( final String instanceName ) {
        PeerID peerID = null;
        if( instanceName != null )
            peerID = peerIDMap.get( instanceName );
        if( peerID == null ) {
            peerID = PeerID.NULL_PEER_ID;
            if (this.instanceName.equals(instanceName)) {
                LOG.log(Level.FINE, "grizzly.netmgr.localPeerId.null", new Object[]{instanceName});
                LOG.log(Level.FINE, "stack trace", new Exception("stack trace"));
            }
            if (LOG.isLoggable(Level.FINE)) {
                LOG.log(Level.FINE, "getPeerID({0}) returning null peerIDMap={1}",
                        new Object[]{instanceName, peerIDMap});
            }
        }
        return peerID;
    }

    @Override
    public void removePeerID( final PeerID peerID ) {
        if( peerID == null )
            return;
        String instanceName = peerID.getInstanceName();
        if( instanceName == null )
            return;
        Level debugLevel = Level.FINE;
        if (LOG.isLoggable(debugLevel)) {
            LOG.log(debugLevel, "removePeerID peerid=" + peerID, new Exception("stack trace"));
        }
        peerIDMap.remove( instanceName );
        removeRemotePeer( instanceName );
        removeFromVMS(peerID);
    }

    @Override
    public boolean isConnected( final PeerID peerID ) {
        boolean isConnected = false;
        if( peerID != null ) {
            try {
                send( peerID, new MessageImpl( Message.TYPE_PING_MESSAGE ) );
                CountDownLatch latch = new CountDownLatch( 1 );
                CountDownLatch oldLatch = pingMessageLockMap.putIfAbsent( peerID, latch );
                if( oldLatch != null )
                    latch = oldLatch;
                try {
                    isConnected = latch.await( failTcpTimeout, TimeUnit.MILLISECONDS );
                } catch( InterruptedException e ) {
                }
            } catch( Throwable ie ) {
                if( LOG.isLoggable( Level.FINE ) )
                    LOG.log( Level.FINE, "isConnected( " + peerID + " ) = " + isConnected, ie );
                return isConnected;
            } finally {
                pingMessageLockMap.remove( peerID );
            }
            return isConnected;
        } else {
            return isConnected;
        }
    }

    public CountDownLatch getPingMessageLock( PeerID peerID ) {
        if( peerID != null )
            return pingMessageLockMap.get( peerID );
        else
            return null;
    }

    @Override
    public MessageSender getMessageSender( int transport ) {
        if( running ) {
            MessageSender sender;
            switch( transport ) {
                case TCP_TRANSPORT:
                    sender = tcpSender;
                    break;
                case UDP_TRANSPORT:
                    sender = udpSender;
                    break;
                default:
                    sender = tcpSender;
                    break;
            }
            return sender;
        } else {
            return null;
        }
    }

    @Override
    public MulticastMessageSender getMulticastMessageSender() {
        if( running )
            return multicastSender;
        else
            return null;
    }

    protected abstract Logger getGrizzlyLogger();

    protected List<PeerID> getVirtualPeerIDList(String groupDiscoveryUriList) {
        if (groupDiscoveryUriList == null) {
            return null;
        }
        if (nomcastLogger.isLoggable(Level.FINE)) {
            nomcastLogger.log(Level.FINE, "DISCOVERY_URI_LIST = {0}", groupDiscoveryUriList);
        }
        List<PeerID> virtualPeerIdList = new ArrayList<PeerID>();
        //if this object has multiple addresses that are comma separated
        if (groupDiscoveryUriList.indexOf(",") > 0) {
            String addresses[] = groupDiscoveryUriList.split(",");
            if (addresses.length > 0) {
                List<String> virtualUriStringList = Arrays.asList(addresses);
                for (String uriString : virtualUriStringList) {
                    try {
                        PeerID peerID = getPeerIDFromURI(uriString);
                        if (peerID != null) {
                            virtualPeerIdList.add(peerID);
                            if (nomcastLogger.isLoggable(Level.FINE)) {
                                nomcastLogger.log(Level.FINE,
                                        "DISCOVERY_URI = {0}, Converted PeerID = {1}",
                                        new Object[]{uriString, peerID});
                            }
                        }
                    } catch (URISyntaxException use) {
                        if (LOG.isLoggable(Level.CONFIG)) {
                            LOG.log(Level.CONFIG,
                                    "failed to parse the DISCOVERY_URI_LIST item ("
                                    + uriString + ")", use);
                        }
                    }
                }
            }
        } else {
            //this object has only one address in it, so add it to the list
            try {
                PeerID peerID = getPeerIDFromURI(groupDiscoveryUriList);
                if (peerID != null) {
                    virtualPeerIdList.add(peerID);
                    if (nomcastLogger.isLoggable(Level.FINE)) {
                        nomcastLogger.log(Level.FINE,
                                "DISCOVERY_URI = {0}, Converted PeerID = {1}",
                                new Object[]{groupDiscoveryUriList, peerID});
                    }
                }
            } catch (URISyntaxException use) {
                if (nomcastLogger.isLoggable(Level.CONFIG)) {
                    nomcastLogger.log(Level.CONFIG, "failed to parse the DISCOVERY_URI_LIST item(" + groupDiscoveryUriList + ")", use);
                }
            }
        }
        return virtualPeerIdList;
    }

    protected PeerID<GrizzlyPeerID> getPeerIDFromURI(String uri) throws URISyntaxException {
        if( uri == null ) {
            return null;
        }
        uri = uri.trim();
        if (uri.isEmpty()) {
            /*
             * In the case of user-managed clusters, we permit
             * an empty (but not-null) discovery list. This implies that
             * we are the first member in the group and we are not
             * using multicast. Only tcp scheme currently supported.
             */
            try {
                String host = InetAddress.getLocalHost().getHostAddress();

                // specifying null multicast address and -1 multicast port
                GrizzlyPeerID gpID = new GrizzlyPeerID(host,
                    tcpStartPort, null, -1);
                
                return new PeerID<GrizzlyPeerID>(gpID,
                    localPeerID.getGroupName(),
                    // the instance name is not meaningless in this case
                    UNKNOWN + host);
            } catch (UnknownHostException ignored) {
                // will see null hostname in output
            }
        }
        URI discoveryUri = new URI(uri);

        /*
         * If someone specifies a URI value as a simple hostname
         * or IP address without any other information, this creates
         * a relative URI and URI.getHost() returns null. Prepending
         * the value with // creates an absolute URI.
         *
         * This allows users to specify either "myhost" or "tcp://myhost"
         * for better usability. This would make a good JDK certification
         * test question.
         */
        if (!discoveryUri.isAbsolute()) {
            if (nomcastLogger.isLoggable(Level.FINE)) {
                nomcastLogger.log(Level.FINE, String.format(
                    "'%s' is a relative uri. Will use '//%s' instead.",
                    uri, uri
                ));
            }
            discoveryUri = new URI("//" + uri);
        }
        int port = discoveryUri.getPort();
        if (port == -1) {
            port = tcpStartPort;
        }
        return new PeerID<GrizzlyPeerID>( new GrizzlyPeerID( discoveryUri.getHost(),
                                                             port,
                                                             multicastAddress,
                                                             multicastPort ),
                                          localPeerID.getGroupName(),
                                          // the instance name is not meaningless in this case
                                          UNKNOWN + discoveryUri.getHost() +"_" + port);
    }

    protected boolean isLeavingMessage(MessageEvent msgEvent) {
        Message msg = msgEvent.getMessage();
        if(msg.getType() == Message.TYPE_HEALTH_MONITOR_MESSAGE) {

            // do not add remote peer when health message is STOPPING or DEAD.
            // these messages are coming in AFTER the cache has been cleared already
            // of the leaving instance.
            HealthMessage hmsg = (HealthMessage)msg.getMessageElement(HealthMonitor.HEALTHM);
            if (hmsg != null) {
                HealthMessage.Entry entry = hmsg.getEntries().get(0);

                // confirm state is a LEAVING state.
                if (entry.isState(HealthMonitor.STOPPED) || entry.isState(HealthMonitor.CLUSTERSTOPPING) ||
                    entry.isState(HealthMonitor.DEAD) || entry.isState(HealthMonitor.PEERSTOPPING)) {
                    return true;
                }
            }
        }
        return false;
    }

    protected void addToVMS(PeerID peerID) {
        if (vms != null) {
            Set<PeerID> virtualPeerIdSet = vms.getVirtualPeerIDSet();
            boolean result = virtualPeerIdSet.add(peerID);
            if (result && nomcastLogger.isLoggable(Level.FINE)) {
                nomcastLogger.log(Level.FINE, "addRemotePeer: virtualPeerIDSet added:" + peerID + " set size=" + virtualPeerIdSet.size() +
                        " virtualPeerIdSet=" + virtualPeerIdSet /* , new Exception("stack trace")*/);
            }
        }
    }

    protected void removeFromVMS(PeerID peerID) {
        if (vms != null) {
            Set<PeerID> virtualPeerIdSet = vms.getVirtualPeerIDSet();
            boolean result = virtualPeerIdSet.remove(peerID);
            if (result && nomcastLogger.isLoggable(Level.FINE)) {
                nomcastLogger.fine("removeRemotePeer: virtualPeerIDSet removed:" + peerID + " set size=" + virtualPeerIdSet.size() +
                    " virtualPeerIdSet=" + virtualPeerIdSet);
            }
        }
    }
}
