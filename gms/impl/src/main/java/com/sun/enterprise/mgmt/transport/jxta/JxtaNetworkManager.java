/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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

package com.sun.enterprise.mgmt.transport.jxta;

import static com.sun.enterprise.mgmt.ConfigConstants.*;
import static com.sun.enterprise.mgmt.transport.jxta.JxtaConfigConstants.*;
import static com.sun.enterprise.mgmt.transport.jxta.JxtaUtil.*;

import com.sun.enterprise.mgmt.transport.Message;
import com.sun.enterprise.mgmt.transport.MessageEvent;
import com.sun.enterprise.mgmt.transport.MessageSender;
import com.sun.enterprise.mgmt.transport.MulticastMessageSender;
import com.sun.enterprise.mgmt.transport.AbstractNetworkManager;
import com.sun.enterprise.ee.cms.impl.base.PeerID;
import com.sun.enterprise.ee.cms.impl.base.Utility;
import net.jxta.document.AdvertisementFactory;
import net.jxta.document.MimeMediaType;
import net.jxta.document.XMLDocument;
import net.jxta.document.StructuredDocumentFactory;
import net.jxta.exception.PeerGroupException;
import net.jxta.id.IDFactory;
import net.jxta.impl.endpoint.mcast.McastTransport;
import net.jxta.impl.endpoint.router.RouteControl;
import net.jxta.impl.endpoint.router.EndpointRouter;
import net.jxta.impl.peergroup.StdPeerGroupParamAdv;
import net.jxta.peergroup.NetPeerGroupFactory;
import net.jxta.peergroup.PeerGroup;
import net.jxta.peergroup.PeerGroupID;
import net.jxta.peergroup.WorldPeerGroupFactory;
import net.jxta.pipe.PipeID;
import net.jxta.pipe.PipeService;
import net.jxta.platform.NetworkConfigurator;
import net.jxta.protocol.ConfigParams;
import net.jxta.protocol.ModuleImplAdvertisement;
import net.jxta.protocol.PipeAdvertisement;
import net.jxta.protocol.RouteAdvertisement;
import net.jxta.rendezvous.RendezVousService;
import net.jxta.rendezvous.RendezvousEvent;
import net.jxta.rendezvous.RendezvousListener;
import net.jxta.endpoint.TextDocumentMessageElement;
import net.jxta.endpoint.MessageTransport;
import net.jxta.endpoint.MessageElement;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.io.Serializable;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * NetworkManager wraps the JXTA plaform lifecycle into a single object. Using the
 * instance name, it encodes a node Peer ID, and provides utilities to derive Peer ID's
 * from an instance name.  Given an instance name, this allows any node to
 * independently interpolate a Peer ID.
 * <p>
 * TODO:REVISIT FOR REFACTORING AND ADDED REQUIRMENTS.
 * TODO: WHEN SPECIFYING INSTANCENAME IN EACH METHOD, IS IT THE INTENTION THAT THE CONSUMING APP COULD POTENTIALLY
 * TODO: PROVIDE DIFFERENT INSTANCE NAMES AT DIFFERENT TIMES DURING A GIVEN LIFETIME OF THE APP? WHAT IMPACT WOULD THERE
 * TODO:BE IF WE REMOVE INSTANCENAME FROM THE PARAMETERS OF THESE METHODS AND BASE INSTANCE NAME FROM THE CONSTRUCTOR'S
 * TODO: CONSTRUCTION FROM PROPERTIES OBJECT?
 */
public class JxtaNetworkManager extends AbstractNetworkManager implements RendezvousListener {

    private static final Logger LOG = JxtaUtil.getLogger();
    private static MessageDigest digest;
    private PeerGroup netPeerGroup;
    private boolean started = false;
    private boolean stopped = false;
    private RendezVousService rendezvous;
    private String groupName = "defaultGroup";
    private String instanceName;
    private static final String PREFIX = "SHOAL";
    private final Object networkConnectLock = new Object();
    private static final Object digestLock = new Object();
    private static final File home = new File( System.getProperty( "JXTA_HOME", ".shoal" ) );
    static private File storeHome;
    private PipeID socketID;
    private static PipeID pipeID;
    private static WorldPeerGroupFactory wpgf;
    private PeerGroup worldPG = null;

    /**
     * JxtaSocket Pipe ID seed.
     */
    private static String SOCKETSEED = PREFIX + "socket";
    /**
     * JxtaBiDiPipe Pipe ID seed.
     */
    private static String PIPESEED = PREFIX + "BIDI";
    /**
     * Health Pipe ID seed.
     */
    private static String HEALTHSEED = PREFIX + "HEALTH";
    /**
     * Master Pipe ID seed.
     */
    private static String MASTERSEED = PREFIX + "MASTER";

    private static String SESSIONQUERYSEED = PREFIX + "SESSIONQ";

    private static String APPSERVICESEED = "APPSERVICE";
    private String mcastAddress;
    private int mcastPort = 0;
    private static final int DEFAULT_MULTICAST_POOLSIZE = 300;
    private int multicastPoolsize = DEFAULT_MULTICAST_POOLSIZE;
    private List<String> rendezvousSeedURIs = new ArrayList<String>();
    private boolean isRendezvousSeed = false;
    private String tcpAddress;
    private Hashtable<String, PeerID> instanceToPeerIdMap = new Hashtable<String, PeerID>();

    private static final String ROUTEADV = "ROUTE";
    private RouteControl routeControl;
    private JxtaPipeManagerWrapper jxtaPipeManagerWrapper;
    private Map<net.jxta.peer.PeerID, RouteAdvertisement> routeCache = new ConcurrentHashMap<net.jxta.peer.PeerID, RouteAdvertisement>();
    private MessageTransport endpointRouter;
    private MessageElement routeAdvElement;
    private byte[] routeAdvBytes;
    private int multicastPacketSize;

    public JxtaNetworkManager() {
    }

    /**
     * NetworkManager provides a simple interface to configuring and managing the lifecycle
     * of the JXTA platform.  In addition, it provides logical addressing by means of name
     * encoded PeerID, and communication channels.  This allows the application to utilize simple
     * addressing.  Therefore it is key that these names are chosen carefully to avoid collision.
     *
     * @param groupName    Group Name, a logical group name that this peer is part of.
     * @param instanceName Instance Name, a logical name for this peer.
     * @param properties   a Properties object that would contain every configuration
     *                     element that the employing application wants to specify values for. The
     *                     keys in this object must correspond to the constants specified in the
     *                     JxtaConfigConstants enum.
     */
    public void initialize( final String groupName, final String instanceName, final Map properties ) throws IOException {
        super.initialize(groupName, instanceName, properties);
        configureJxtaLogging();
        this.groupName = groupName;
        this.instanceName = instanceName;

        socketID = getSocketID( instanceName );
        pipeID = getPipeID( instanceName );
        if( properties != null && !properties.isEmpty() ) {
            final String ma = (String)properties.get( MULTICASTADDRESS.toString() );
            if( ma != null ) {
                mcastAddress = ma;
            }
            final Object mp = properties.get( MULTICASTPORT.toString() );
            if( mp != null ) {
                if( mp instanceof String ) {
                    mcastPort = Integer.parseInt( (String)mp );
                } else if( mp instanceof Integer ) {
                    mcastPort = (Integer)mp;
                }
            }
            final Object virtualMulticastURIList = properties.get( VIRTUAL_MULTICAST_URI_LIST.toString() );
            if( virtualMulticastURIList != null ) {
                //if this object has multiple addresses that are comma separated
                if( ( (String)virtualMulticastURIList ).indexOf( "," ) > 0 ) {
                    String addresses[] = ( (String)virtualMulticastURIList ).split( "," );
                    if( addresses.length > 0 ) {
                        rendezvousSeedURIs = Arrays.asList( addresses );
                    }
                } else {
                    //this object has only one address in it, so add it to the list
                    rendezvousSeedURIs.add( ( (String)virtualMulticastURIList ) );
                }
                LOG.config("VIRTUAL_MULTICAST_URI_LIST=" + virtualMulticastURIList + " rendezvousSeedURIs.get(0)=" + rendezvousSeedURIs.get(0));
            }
            Object isVirtualMulticastNode = properties.get( IS_BOOTSTRAPPING_NODE.toString() );
            if( isVirtualMulticastNode != null ) {
                isRendezvousSeed = Boolean.parseBoolean( (String)isVirtualMulticastNode );
                LOG.config("IS_BOOTSTRAPPING_NODE (isRendezvousSeed) is set to " + isRendezvousSeed);
            }

            tcpAddress = (String)properties.get( BIND_INTERFACE_ADDRESS.toString() );

            multicastPoolsize = DEFAULT_MULTICAST_POOLSIZE;
            Object multicastPoolsizeString = properties.get( MULTICAST_POOLSIZE.toString() );

            // remove this code when app server is updated to set this property in <code>properties</code>
            // passed into this constructor.
            if( multicastPoolsizeString == null ) {
                multicastPoolsizeString = System.getProperty( "jxtaMulticastPoolsize" );
            }
            // end temporary short term configuration by system property

            if( multicastPoolsizeString != null ) {
                try {
                    multicastPoolsize = Integer.parseInt( (String)multicastPoolsizeString );
                } catch( NumberFormatException nfe ) {
                    LOG.warning( "Invalid value for Shoal property to configure jxta " + MULTICAST_POOLSIZE.toString() + "=" + (String)multicastPoolsizeString );
                }
                if( multicastPoolsize < DEFAULT_MULTICAST_POOLSIZE ) {
                    multicastPoolsize = DEFAULT_MULTICAST_POOLSIZE;
                }
            }
        }
        multicastPacketSize = Utility.getIntProperty( MULTICAST_PACKET_SIZE.toString(), 64 * 1024, properties );
        try {
            initWPGF( instanceName );
        } catch( PeerGroupException e ) {
            LOG.log( Level.SEVERE, e.getLocalizedMessage(), e );
        }
    }

    /**
     * Returns a SHA1 hash of string.
     *
     * @param expression to hash
     *
     * @return a SHA1 hash of string
     */
    private static byte[] hash( final String expression ) {
        byte[] digArray;
        if( expression == null ) {
            throw new IllegalArgumentException( "Invalid null expression" );
        }
        synchronized( digestLock ) {
            if( digest == null ) {
                try {
                    digest = MessageDigest.getInstance( "SHA1" );
                } catch( NoSuchAlgorithmException ex ) {
                    LOG.log( Level.WARNING, ex.getLocalizedMessage() );
                }
            }
            digest.reset();
            try {
                digArray = digest.digest( expression.getBytes( "UTF-8" ) );
            } catch( UnsupportedEncodingException impossible ) {
                LOG.log( Level.WARNING, "digestEncoding unsupported:" + impossible.getLocalizedMessage() +
                                        ":returning digest with default encoding" );
                digArray = digest.digest( expression.getBytes() );
            }
        }
        return digArray;
    }

    /**
     * Given a instance name, it returns a name encoded PipeID to for binding JxtaBiDiPipes.
     *
     * @param instanceName instance name
     *
     * @return The pipeID value
     */
    public net.jxta.pipe.PipeID getPipeID( String instanceName ) {
        String seed = instanceName + PIPESEED;
        return IDFactory.newPipeID( PeerGroupID.defaultNetPeerGroupID, hash( seed.toLowerCase() ) );
    }

    /**
     * Given a instance name, it returns a name encoded PipeID to for binding JxtaSockets.
     *
     * @param instanceName instance name value
     *
     * @return The scoket PipeID value
     */
    public net.jxta.pipe.PipeID getSocketID( final String instanceName ) {
        final String seed = instanceName + SOCKETSEED;
        return IDFactory.newPipeID( PeerGroupID.defaultNetPeerGroupID, hash( seed.toLowerCase() ) );
    }

    /**
     * Given a instance name, it returns a name encoded PeerID to for binding to specific instance.
     *
     * @param instanceName instance name value
     *
     * @return The peerID value
     */
    public PeerID getPeerID( final String instanceName ) {
        PeerID id = instanceToPeerIdMap.get( instanceName );
        if( id == null ) {
            net.jxta.peer.PeerID jxtaPeerID = IDFactory.newPeerID( PeerGroupID.worldPeerGroupID, hash( PREFIX + instanceName.toUpperCase() ) );
            id = new PeerID<net.jxta.peer.PeerID>( jxtaPeerID, groupName, instanceName );
            instanceToPeerIdMap.put( instanceName, id );
        }
        return id;
    }

    public void removePeerID( final PeerID peerID ) {
        if( peerID == null )
            return;
        Serializable uniqueID = peerID.getUniqueID();
        if( uniqueID instanceof net.jxta.peer.PeerID )
            removeAllCaches( (net.jxta.peer.PeerID)uniqueID );
    }

    public boolean isConnected( final PeerID peerID ) {
        net.jxta.peer.PeerID jxtaPeerID = (net.jxta.peer.PeerID)peerID.getUniqueID();
        return getRouteControl().isConnected(jxtaPeerID);
    }

    public net.jxta.peer.PeerID getJxtaPeerID( final String instanceName ) {
        PeerID id = getPeerID( instanceName );
        if( id != null )
            return (net.jxta.peer.PeerID)id.getUniqueID();
        else
            return null;
    }

    /**
     * Given a instance name, it returns a name encoded PeerID to for binding to specific instance.
     *
     * @param groupName instance name value
     *
     * @return The peerID value
     */
    public PeerGroupID getPeerGroupID( final String groupName ) {
        if( mcastAddress == null && mcastPort <= 0 ) {
            return IDFactory.newPeerGroupID( PeerGroupID.defaultNetPeerGroupID,
                                             hash( PREFIX + groupName.toLowerCase() ) );
        } else {
            return IDFactory.newPeerGroupID( PeerGroupID.defaultNetPeerGroupID,
                                             hash( PREFIX + groupName.toLowerCase() + mcastAddress + mcastPort ) );
        }
    }

    /**
     * Returns the HealthMonitor PipeID, used for health monitoring purposes.
     *
     * @return The HealthPipe Pipe ID
     */
    public PipeID getHealthPipeID() {
        return IDFactory.newPipeID( getInfraPeerGroupID(), hash( HEALTHSEED.toLowerCase() ) );
    }

    /**
     * Returns the MasterNode protocol PipeID. used for dynamic organization of nodes.
     *
     * @return The MasterPipe PipeID
     */
    public PipeID getMasterPipeID() {
        return IDFactory.newPipeID( getInfraPeerGroupID(), hash( MASTERSEED.toLowerCase() ) );
    }

    /**
     * Returns the SessionQeuryPipe ID. Used to query for a session replication
     *
     * @return The SessionQuery PipeID
     */
    public PipeID getSessionQueryPipeID() {
        return IDFactory.newPipeID( getInfraPeerGroupID(), hash( SESSIONQUERYSEED.toLowerCase() ) );
    }

    /**
     * Returns the Pipe ID that will be used for application layer to
     * send and receive messages.
     *
     * @return The Application Service Pipe ID
     */
    public PipeID getAppServicePipeID() {
        return IDFactory.newPipeID( getInfraPeerGroupID(), hash( APPSERVICESEED.toLowerCase() ) );
    }

    /**
     * Simple utility to create a pipe advertisement
     *
     * @param instanceName pipe name
     *
     * @return PipeAdvertisement of type Unicast, and of name instanceName
     */
    private PipeAdvertisement getTemplatePipeAdvertisement( final String instanceName ) {
        final PipeAdvertisement advertisement = (PipeAdvertisement)
                AdvertisementFactory.newAdvertisement( PipeAdvertisement.getAdvertisementType() );
        advertisement.setType( PipeService.UnicastType );
        advertisement.setName( instanceName );
        return advertisement;
    }

    /**
     * Creates a JxtaSocket pipe advertisement with a SHA1 encoded instance name pipe ID.
     *
     * @param instanceName instance name
     *
     * @return a JxtaSocket Pipe Advertisement
     */
    public PipeAdvertisement getSocketAdvertisement( final String instanceName ) {
        final PipeAdvertisement advertisement = getTemplatePipeAdvertisement( instanceName );
        advertisement.setPipeID( getSocketID( instanceName ) );
        return advertisement;
    }

    /**
     * Creates a JxtaBiDiPipe pipe advertisement with a SHA1 encoded instance name pipe ID.
     *
     * @param instanceName instance name
     *
     * @return PipeAdvertisement a JxtaBiDiPipe Pipe Advertisement
     */
    public PipeAdvertisement getPipeAdvertisement( final String instanceName ) {
        final PipeAdvertisement advertisement = getTemplatePipeAdvertisement( instanceName );
        advertisement.setPipeID( getPipeID( instanceName ) );
        return advertisement;
    }

    /**
     * Contructs and returns the Infrastructure PeerGroupID for the ClusterManager.
     * This ensures scoping and isolation of ClusterManager deployments.
     *
     * @return The infraPeerGroupID PeerGroupID
     */
    public PeerGroupID getInfraPeerGroupID() {
        return getPeerGroupID( groupName );
    }

    /**
     * Creates and starts the JXTA NetPeerGroup using a platform configuration
     * template. This class also registers a listener for rendezvous events
     *
     * @throws IOException if an io error occurs during creation of the cache directory
     */
    @Override
    public synchronized void start() throws IOException {
        if( started ) {
            return;
        }
        super.start();
        try {
            startDomain();
        } catch( PeerGroupException e ) {
            //throw new IOException( e ); // JDK 1.6
            throw new IOException( e.getMessage() );
        }
    }

    /**
     * Removes the JXTA CacheManager persistent store.
     *
     * @param rootDir cache directory
     */
    private static void clearCache( final File rootDir ) {
        try {
            if( rootDir.exists() ) {
                if( LOG.isLoggable( Level.FINER ) ) {
                    LOG.finer( "clearCache(" + rootDir + ")" );
                }
                // remove it along with it's content
                File[] list = rootDir.listFiles();
                for( File aList : list ) {
                    if( aList.isDirectory() ) {
                        clearCache( aList );
                    } else {
                        boolean value = aList.delete();
                        if( !value && LOG.isLoggable( Level.FINE ) ) {
                            LOG.fine( "failed to deleted cache file " + aList );
                        }
                    }
                }
            } else {
                if( LOG.isLoggable( Level.FINER ) ) {
                    LOG.finer( "clearCache(" + rootDir + ") on non-exsistent directory" );
                }
            }
            rootDir.delete();
        } catch( Throwable t ) {
            LOG.log( Level.WARNING, "Unable to clear " + rootDir.toString(), t );
        }
    }

    /**
     * Stops the NetworkManager and the JXTA platform.
     */
    @Override
    public synchronized void stop() {
        if( stopped && !started ) {
            return;
        }
        try {
            super.stop();
            jxtaPipeManagerWrapper.stop();
            rendezvous.removeListener( this );
            netPeerGroup.stopApp();
            netPeerGroup.unref();
            netPeerGroup = null;
            // don't unref world peer group.
            instanceToPeerIdMap.clear();
            clearAllCaches();
            JxtaNetworkManagerRegistry.remove( groupName );
        } catch( Throwable th ) {
            LOG.log( Level.FINEST, th.getLocalizedMessage() );
        }
        stopped = true;
        started = false;
    }

    public boolean send( final PeerID peerID, final Message message ) throws IOException {
        if( !isStarted() )
            throw new IOException( "network manager is not running" );
        if( message == null )
            throw new IOException( "message is null" );
        MessageSender sender = jxtaPipeManagerWrapper;
        if( sender == null )
            throw new IOException( "message sender is not initialized" );
        return sender.send( peerID, message );
    }

    public boolean broadcast( final Message message ) throws IOException {
        if( !isStarted() )
            throw new IOException( "network manager is not running" );
        if( message == null )
            throw new IOException( "message is null" );
        MulticastMessageSender sender = jxtaPipeManagerWrapper;
        if( sender == null )
            throw new IOException( "multicast message sender is not initialized" );
        return sender.broadcast( message );
    }

    protected void beforeDispatchingMessage( MessageEvent messageEvent, Map piggyback ) {
        if( messageEvent == null )
            return;
        processRoute( messageEvent.getSourcePeerID(), messageEvent.getMessage() );
    }

    protected void afterDispatchingMessage( MessageEvent messageEvent, Map piggyback ) {
    }

    /**
     * Returns the netPeerGroup instance for this Cluster.
     *
     * @return The netPeerGroup value
     */
    public PeerGroup getNetPeerGroup() {
        return netPeerGroup;
    }

    /**
     * Blocks until a connection to rendezvous node occurs. Returns immediately
     * if a connection is already established.  This is useful to ensure the widest
     * network exposure.
     *
     * @param timeout timeout in milliseconds
     *
     * @return connection state
     */
    public boolean waitForRendezvousConnection( long timeout ) {
        if( 0 == timeout ) {
            timeout = Long.MAX_VALUE;
        }

        long timeoutAt = System.currentTimeMillis() + timeout;

        if( timeoutAt <= 0 ) {
            // handle overflow.
            timeoutAt = Long.MAX_VALUE;
        }

        while( started && !stopped && !rendezvous.isConnectedToRendezVous() && !rendezvous.isRendezVous() ) {
            LOG.fine( "rendezvous.isRendezVous() = " + rendezvous.isRendezVous() +
                      "rendezvous.isConnectedToRendezVous() = " + rendezvous.isConnectedToRendezVous() );
            try {
                long waitFor = timeoutAt - System.currentTimeMillis();

                if( waitFor > 0 ) {
                    synchronized( networkConnectLock ) {
                        networkConnectLock.wait( timeout );
                    }
                } else {
                    // all done with waiting.
                    break;
                }
            } catch( InterruptedException e ) {
                Thread.interrupted();
                break;
            }
        }
        LOG.fine( "outside while loop -> rendezvous.isRendezVous() = " + rendezvous.isRendezVous() +
                  "rendezvous.isConnectedToRendezVous() = " + rendezvous.isConnectedToRendezVous() );
        return rendezvous.isConnectedToRendezVous() || rendezvous.isRendezVous();
    }

    /**
     * {@inheritDoc}
     */
    public void rendezvousEvent( RendezvousEvent event ) {
        if( event.getType() == RendezvousEvent.RDVCONNECT || event.getType() == RendezvousEvent.RDVRECONNECT
            || event.getType() == RendezvousEvent.BECAMERDV ) {
            synchronized( networkConnectLock ) {
                networkConnectLock.notifyAll();
            }
        }
    }

    /**
     * Gets this instance's PeerID
     *
     * @return The peerID value
     */
    public net.jxta.peer.PeerID getPeerID() {
        if( stopped && !started ) {
            return null;
        }
        return netPeerGroup.getPeerID();
    }

    /**
     * Determines whether the <code>NetworkManager</code> has started.
     *
     * @return The running value
     */
    boolean isStarted() {
        return !stopped && started;
    }

    /**
     * Returns this instance name encoded SocketID.
     *
     * @return The socketID value
     */
    PipeID getSocketID() {
        return socketID;
    }

    /**
     * Returns this instance name encoded PipeID.
     *
     * @return The pipeID value
     */
    public PipeID getPipeID() {
        return pipeID;
    }

    /**
     * Returns this instance name.
     *
     * @return The instance name value
     */
    public String getInstanceName() {
        return instanceName;
    }

    /**
     * Returns the home directory. An instance home directory is used to persists an instance configuration and cache.
     *
     * @return The instance name value
     */
    public File getHome() {
        return home;
    }


    /**
     * Configure and start the World Peer Group Factory
     *
     * @param instanceName The name of the peer.
     *
     * @throws PeerGroupException Thrown for errors creating the world peer group.
     */
    private void initWPGF( String instanceName ) throws PeerGroupException {
        synchronized( JxtaNetworkManager.class ) {
            if( null == wpgf ) {
                storeHome = new File( home, instanceName );
                if( LOG.isLoggable( Level.CONFIG ) ) {
                    LOG.config( "initWPGF storeHome=" + storeHome );
                }
                clearCache( storeHome );
                NetworkConfigurator worldGroupConfig;
                if( isRendezvousSeed && rendezvousSeedURIs.size() > 0 ) {
                    worldGroupConfig = new NetworkConfigurator( NetworkConfigurator.RDV_NODE + NetworkConfigurator.RELAY_NODE, storeHome.toURI() );
                    //TODO: Need to figure out this process's seed addr from the list so that the right port can be bound to
                    //For now, we only pick the first seed URI's port and let the other members be non-seeds even if defined in the list.
                    String myPort = rendezvousSeedURIs.get( 0 );
                    LOG.fine( "myPort is " + myPort );
                    myPort = myPort.substring( myPort.lastIndexOf( ":" ) + 1, myPort.length() );
                    LOG.fine( "myPort is " + myPort );
                    //TODO: Add a check for port availability and consequent actions
                    worldGroupConfig.setTcpPort( Integer.parseInt( myPort ) );
                    worldGroupConfig.setTcpStartPort( Integer.parseInt( myPort ) );
                    worldGroupConfig.setTcpEndPort( Integer.parseInt( myPort ) );
                } else {
                    worldGroupConfig = NetworkConfigurator.newAdHocConfiguration( storeHome.toURI() );
                    worldGroupConfig.setTcpStartPort( 9701 );
                    worldGroupConfig.setTcpEndPort( 9999 );
                }
                worldGroupConfig.setName( instanceName );
                worldGroupConfig.setPeerID( getJxtaPeerID( instanceName ) );
                // Disable multicast because we will be using a separate multicast in each group.
                worldGroupConfig.setUseMulticast( false );
                if (tcpAddress != null && !tcpAddress.equals("")) {
                    try {
                        InetAddress usingInterface = InetAddress.getByName(tcpAddress);
                    } catch (UnknownHostException failed) {
                        // Following log message is workaround that jxta warning log messages are being suppressed by default.
                        LOG.warning("GMS bind-interface-address property set to unknown host " + tcpAddress + ", using default of AnyAddress");
                        // Just set the invalid tcp address into jxta layer.  Jxta layer will perform same check as above and ultimately default the value.
                        worldGroupConfig.setTcpInterfaceAddress(tcpAddress);
                    }
                    worldGroupConfig.setTcpInterfaceAddress(tcpAddress);
                }
                ConfigParams config = worldGroupConfig.getPlatformConfig();
                // Instantiate the world peer group factory.
                wpgf = new WorldPeerGroupFactory( config, storeHome.toURI() );
            }
        }
    }

    /**
     * Configure and start a separate top-level JXTA domain.
     *
     * @return the net peergroup
     *
     * @throws PeerGroupException Thrown for errors creating the domain.
     * @throws java.io.IOException Thrown for erros creating jxta pipe manager. 
     */
    @SuppressWarnings( "unchecked" )
    private PeerGroup startDomain() throws PeerGroupException, IOException {
        // Configure the peer name
        final NetworkConfigurator config;
        if( isRendezvousSeed && rendezvousSeedURIs.size() > 0 ) {
            config = new NetworkConfigurator( NetworkConfigurator.RDV_NODE + NetworkConfigurator.RELAY_NODE, storeHome.toURI() );
            //TODO: Need to figure out this process's seed addr from the list so that the right port can be bound to
            //For now, we only pick the first seed URI's port and let the other members be non-seeds even if defined in the list.
            String myPort = rendezvousSeedURIs.get( 0 );
            LOG.fine( "myPort is " + myPort );
            myPort = myPort.substring( myPort.lastIndexOf( ":" ) + 1, myPort.length() );
            LOG.fine( "myPort is " + myPort );
            //TODO: Add a check for port availability and consequent actions
            config.setTcpPort( Integer.parseInt( myPort ) );
            config.setTcpStartPort( Integer.parseInt( myPort ) );
            config.setTcpEndPort( Integer.parseInt( myPort ) );
        } else {
            config = new NetworkConfigurator( NetworkConfigurator.EDGE_NODE, storeHome.toURI() );
            config.setTcpStartPort( 9701 );
            config.setTcpEndPort( 9999 );
        }

        config.setPeerID( getJxtaPeerID( instanceName ) );
        config.setName( instanceName );
        //config.setPrincipal(instanceName);
        config.setDescription( "Created by Jxta Cluster Management NetworkManager" );
        config.setInfrastructureID( getInfraPeerGroupID() );
        config.setInfrastructureName( groupName );

        LOG.fine( "Rendezvous seed?:" + isRendezvousSeed );
        if( !rendezvousSeedURIs.isEmpty() ) {
            LOG.fine( "Setting Rendezvous seeding uri's to network configurator:" + rendezvousSeedURIs );
            config.setRendezvousSeeds( new HashSet<String>( rendezvousSeedURIs ) );
            //limit it to configured rendezvous at this point
            config.setUseOnlyRendezvousSeeds( true );

            LOG.fine( "Setting Relay seeding uri's to network configurator:" + rendezvousSeedURIs );
            config.setRelaySeedingURIs( new HashSet<String>( rendezvousSeedURIs ) );
            //limit it to configured rendezvous at this point
            config.setUseOnlyRelaySeeds( true );
        }

        config.setUseMulticast( true );
        config.setMulticastSize( multicastPacketSize );
        config.setInfrastructureDescriptionStr( groupName + " Infrastructure Group Name" );
        if( mcastAddress != null ) {
            config.setMulticastAddress( mcastAddress );
        }
        if( mcastPort > 0 ) {
            config.setMulticastPort( mcastPort );
        }
        // TODO: Not supported as of 2023, 3.0.2-SNAPSHOT
//        if( multicastPoolsize != 0 ) {
//            config.setMulticastPoolSize( multicastPoolsize );
//            if( LOG.isLoggable( Level.CONFIG ) ) {
//                LOG.config( "set jxta Multicast Poolsize to " + config.getMulticastPoolSize() );
//            }
//        }

        //if a machine has multiple network interfaces,
        //specify which interface the group communication should start on
        if( tcpAddress != null && !tcpAddress.equals( "" ) ) {
            config.setTcpInterfaceAddress( tcpAddress );
            config.setMulticastAddress(tcpAddress);
        }
        if( LOG.isLoggable( Level.CONFIG ) ) {
            LOG.config( "node config adv = " + config.getPlatformConfig().toString() );
        }

        PeerGroup worldPG = getWorldPeerGroup();
        ModuleImplAdvertisement npgImplAdv;
        try {
            npgImplAdv = worldPG.getAllPurposePeerGroupImplAdvertisement();
            npgImplAdv.setModuleSpecID( PeerGroup.allPurposePeerGroupSpecID );
            StdPeerGroupParamAdv params = new StdPeerGroupParamAdv( npgImplAdv.getParam() );
            params.addProto( McastTransport.MCAST_TRANSPORT_CLASSID, McastTransport.MCAST_TRANSPORT_SPECID );
            npgImplAdv.setParam( (XMLDocument)params.getDocument( MimeMediaType.XMLUTF8 ) );
        } catch( Exception failed ) {
            throw new PeerGroupException( "Could not construct domain ModuleImplAdvertisement", failed );
        }

        ConfigParams cfg = config.getPlatformConfig();
        // Configure the domain
        NetPeerGroupFactory factory = new NetPeerGroupFactory( worldPG, cfg, npgImplAdv );
        netPeerGroup = factory.getInterface();
        localPeerID = new PeerID<net.jxta.peer.PeerID>( netPeerGroup.getPeerID(), groupName, instanceName );
        rendezvous = netPeerGroup.getRendezVousService();
        rendezvous.addListener( this );

        //used to ensure up to date routes are used
        endpointRouter = ( netPeerGroup.getEndpointService() ).getMessageTransport( "jxta" );
        if( endpointRouter != null ) {
            routeControl = (RouteControl)endpointRouter.transportControl( EndpointRouter.GET_ROUTE_CONTROL, null );
            RouteAdvertisement route = routeControl.getMyLocalRoute();
            if( route != null ) {
                routeAdvElement = new TextDocumentMessageElement( ROUTEADV,
                                                                  (XMLDocument)route.getDocument( MimeMediaType.XMLUTF8 ), null );
                if( routeAdvElement != null )
                    routeAdvBytes = routeAdvElement.getBytes( false );
            }
        }
        if( routeAdvElement == null ) {
            LOG.warning( "MasterNode constructor: bad constraints endpointRouter= " + endpointRouter +
                         " routeControl=" + routeControl + " routeAdvElement=" + routeAdvElement );
        } else if( LOG.isLoggable( Level.FINER ) ) {
            LOG.finer( "MasterNode() routeAdvElement=" + routeAdvElement );
        }
        jxtaPipeManagerWrapper = new JxtaPipeManagerWrapper( this, localPeerID );
        jxtaPipeManagerWrapper.start();

        JxtaNetworkManagerRegistry.add(groupName, this);

        stopped = false;
        started = true;
        if( !rendezvousSeedURIs.isEmpty() ) {
            waitForRendezvousConnection( 30000 );
        }
        LOG.fine( "Connected to the bootstrapping node?: " + ( rendezvous.isConnectedToRendezVous() || rendezvous.isRendezVous() ) );
        return netPeerGroup;
    }

    synchronized private PeerGroup getWorldPeerGroup() {
        if( worldPG == null ) {
            worldPG = wpgf.getInterface();
        }
        return worldPG;
    }

    /**
     * Caches a route for an instance
     *
     * @param route the route advertisement
     */
    public void cacheRoute( RouteAdvertisement route ) {
        routeCache.put( route.getDestPeerID(), route );
    }

    private void clearAllCaches() {
        routeCache.clear();
        if( jxtaPipeManagerWrapper != null )
            jxtaPipeManagerWrapper.clearPipeCache();
    }

    private void removeAllCaches( net.jxta.peer.PeerID peerid ) {
        if( peerid == null )
            return;
        routeCache.remove( peerid );
        if( jxtaPipeManagerWrapper != null )
            jxtaPipeManagerWrapper.removePipeFromCache( peerid );
    }

    /**
     * returns the cached route if any, null otherwise
     *
     * @param peerid the instance id
     *
     * @return the cached route if any, null otherwise
     */
    public RouteAdvertisement getCachedRoute( net.jxta.peer.PeerID peerid ) {
        return routeCache.get( peerid );
    }

    void addRoute( Message msg ) {
        if( routeAdvElement != null && routeControl != null ) {
            msg.addMessageElement( ROUTEADV, routeAdvBytes == null ? routeAdvElement.getBytes( false ) : routeAdvBytes );
        } else {
            if( LOG.isLoggable( Level.FINE ) ) {
                LOG.fine( "addRoute(): Did not add route to msg " + msg + " routeAdvElement=" + routeAdvElement +
                          " routeControl=" + routeControl );
            }
        }
    }

    private RouteControl getRouteControl() {
        if( routeControl == null ) {
            routeControl = (RouteControl)endpointRouter.transportControl( EndpointRouter.GET_ROUTE_CONTROL, null );
        }
        return routeControl;
    }

    private void processRoute( PeerID peerID, Message message ) {
        if( peerID == null || message == null )
            return;
        if( message.getType() != Message.TYPE_HEALTH_MONITOR_MESSAGE &&
            message.getType() != Message.TYPE_MASTER_NODE_MESSAGE )
            return;
        if( peerID.equals( localPeerID ) )
            return; // lookback
        if( !( peerID.getUniqueID() instanceof net.jxta.peer.PeerID ) )
            return;
        LOG.log( Level.FINER, "Inside processRoute..." );

        try {
            byte[] routeAdvBytes = (byte[])message.getMessageElement( ROUTEADV );
            if( routeAdvBytes != null ) {
                XMLDocument asDoc = (XMLDocument)StructuredDocumentFactory.newStructuredDocument(
                        routeAdvElement.getMimeType(), new ByteArrayInputStream( routeAdvBytes ) );
                final RouteAdvertisement route = (RouteAdvertisement)
                        AdvertisementFactory.newAdvertisement( asDoc );
                cacheRoute( route );
                if( routeControl != null ) {
                    routeControl.addRoute( route );
                }
                if( LOG.isLoggable( Level.FINER ) ) {
                    LOG.finer( "cached following route from msg " + message + " route=" + route );
                }
            }
        } catch( IOException ie ) {
            if( LOG.isLoggable( Level.WARNING ) )
                LOG.log( Level.WARNING, "failed to execute processRoute()", ie );
        }
    }

    public MessageSender getMessageSender( int transport ) {
        if( isStarted() )
            return jxtaPipeManagerWrapper;
        else
            return null;
    }

    public MulticastMessageSender getMulticastMessageSender() {
        if( isStarted() )
            return jxtaPipeManagerWrapper;
        else
            return null;
    }

    public void addRemotePeer(PeerID id) {
        // noop.  not needed for this transport.
    }
}

