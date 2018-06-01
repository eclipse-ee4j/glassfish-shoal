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

package com.sun.enterprise.mgmt.transport.grizzly.grizzly1_9;

import com.sun.enterprise.ee.cms.impl.base.GMSThreadFactory;
import com.sun.enterprise.ee.cms.impl.base.PeerID;
import com.sun.enterprise.ee.cms.impl.base.Utility;
import com.sun.enterprise.ee.cms.impl.common.GMSContext;
import com.sun.enterprise.ee.cms.impl.common.GMSContextFactory;
import com.sun.enterprise.ee.cms.impl.common.GMSMonitor;
import com.sun.enterprise.mgmt.transport.*;
import com.sun.enterprise.mgmt.transport.grizzly.*;
import com.sun.grizzly.*;
import com.sun.grizzly.connectioncache.client.CacheableConnectorHandlerPool;
import com.sun.grizzly.connectioncache.spi.transport.ConnectionFinder;
import com.sun.grizzly.connectioncache.spi.transport.ContactInfo;
import com.sun.grizzly.util.GrizzlyExecutorService;
import com.sun.grizzly.util.LoggerUtils;
import com.sun.grizzly.util.SelectorFactory;
import com.sun.grizzly.util.ThreadPoolConfig;

import java.io.IOException;
import java.net.*;
import java.nio.channels.SelectionKey;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.sun.enterprise.mgmt.transport.grizzly.GrizzlyConfigConstants.*;

/**
 * @author Bongjae Chang
 */
public class GrizzlyNetworkManager1_9 extends com.sun.enterprise.mgmt.transport.grizzly.GrizzlyNetworkManager {

    public static final String MESSAGE_SELECTION_KEY_TAG = "selectionKey";

    private int maxPoolSize;
    private int corePoolSize;
    private long keepAliveTime; // ms
    private int poolQueueSize;

    private String virtualUriList;
    private GrizzlyExecutorService execService;
    private ExecutorService multicastSenderThreadPool = null;

    public final Controller controller = new Controller();
    public final Map<SelectionKey, String> selectionKeyMap = new ConcurrentHashMap<SelectionKey, String>();
    public TCPSelectorHandler tcpSelectorHandler = null;


    public GrizzlyNetworkManager1_9() {
    }

    public void localConfigure( final Map properties ) {
        maxPoolSize = Utility.getIntProperty( MAX_POOLSIZE.toString(), 50, properties );
        corePoolSize = Utility.getIntProperty( CORE_POOLSIZE.toString(), 20, properties );
        keepAliveTime = Utility.getLongProperty( KEEP_ALIVE_TIME.toString(), 60 * 1000, properties );
        poolQueueSize = Utility.getIntProperty( POOL_QUEUE_SIZE.toString(), 1024 * 4, properties );
        virtualUriList = Utility.getStringProperty( DISCOVERY_URI_LIST.toString(), null, properties );
    }

    @SuppressWarnings( "unchecked" )
    public synchronized void initialize( final String groupName, final String instanceName, final Map properties ) throws IOException {
        super.initialize(groupName, instanceName, properties);
        this.instanceName = instanceName;
        this.groupName = groupName;
        configure(properties);
        localConfigure(properties);
        System.out.println("Grizzly 1.9 NetworkManager");

        GMSContext ctx = GMSContextFactory.getGMSContext(groupName);
        if (ctx != null)  {
            GMSMonitor monitor = ctx.getGMSMonitor();
            if (monitor != null) {
                monitor.setSendWriteTimeout(this.sendWriteTimeoutMillis);
            }
        }

        // moved setting of localPeerId.

        InetAddress localInetAddress = null;
        if( host != null ) {
            localInetAddress = InetAddress.getByName( host );
        }
        ThreadPoolConfig threadPoolConfig = new ThreadPoolConfig("GMS-GrizzlyControllerThreadPool-Group-" + groupName,
                corePoolSize,
                maxPoolSize,
                new ArrayBlockingQueue<Runnable>( poolQueueSize ),
                poolQueueSize,
                keepAliveTime,
                TimeUnit.MILLISECONDS,
                null,
                Thread.NORM_PRIORITY, //priority = 5
                null);

        execService = GrizzlyExecutorService.createInstance(threadPoolConfig);
        controller.setThreadPool( execService );

        final CacheableConnectorHandlerPool cacheableHandlerPool =
                new CacheableConnectorHandlerPool( controller, highWaterMark,
                numberToReclaim, maxParallelSendConnections,
                new ConnectionFinder<ConnectorHandler>() {

            @Override
            public ConnectorHandler find(ContactInfo<ConnectorHandler> cinfo,
                    Collection<ConnectorHandler> idleConnections,
                    Collection<ConnectorHandler> busyConnections)
                    throws IOException {

                if (!idleConnections.isEmpty()) {
                    return null;
                }

                return cinfo.createConnection();
            }
        });
        
        controller.setConnectorHandlerPool( cacheableHandlerPool );

        tcpSelectorHandler = new ReusableTCPSelectorHandler();
        tcpSelectorHandler.setPortRange(new PortRange(this.tcpStartPort, this.tcpEndPort));
        tcpSelectorHandler.setSelectionKeyHandler( new GrizzlyCacheableSelectionKeyHandler( highWaterMark, numberToReclaim, this ) );
        tcpSelectorHandler.setInet( localInetAddress );

        controller.addSelectorHandler( tcpSelectorHandler );


        if( GrizzlyUtil.isSupportNIOMulticast() ) {
            MulticastSelectorHandler multicastSelectorHandler = new MulticastSelectorHandler();
            multicastSelectorHandler.setPort( multicastPort );
            multicastSelectorHandler.setSelectionKeyHandler( new GrizzlyCacheableSelectionKeyHandler( highWaterMark, numberToReclaim, this ) );
            multicastSelectorHandler.setMulticastAddress( multicastAddress );
            multicastSelectorHandler.setNetworkInterface( networkInterfaceName );
            multicastSelectorHandler.setInet( localInetAddress );
            controller.addSelectorHandler( multicastSelectorHandler );                      
        }

        ProtocolChainInstanceHandler pciHandler = new DefaultProtocolChainInstanceHandler() {
            @Override
            public ProtocolChain poll() {
                ProtocolChain protocolChain = protocolChains.poll();
                if( protocolChain == null ) {
                    protocolChain = new DefaultProtocolChain();
                    protocolChain.addFilter(
                            GrizzlyMessageProtocolParser.createParserProtocolFilter(null));
                    protocolChain.addFilter(new GrizzlyMessageDispatcherFilter(
                            GrizzlyNetworkManager1_9.this));
                }
                return protocolChain;
            }
        };
        controller.setProtocolChainInstanceHandler( pciHandler );
        SelectorFactory.setMaxSelectors( writeSelectorPoolSize );
    }

    private final CountDownLatch controllerGate = new CountDownLatch( 1 );
    private boolean controllerGateIsReady = false;
    private Throwable controllerGateStartupException = null;


    @Override
    @SuppressWarnings( "unchecked" )
    public synchronized void start() throws IOException {
        if( running )
            return;
        super.start();

        ControllerStateListener controllerStateListener = new ControllerStateListener() {

            public void onStarted() {
            }

            public void onReady() {
                if( LOG.isLoggable( Level.FINER ) )
                    LOG.log( Level.FINER, "GrizzlyNetworkManager1_9 is ready" );
                controllerGateIsReady = true;
                controllerGate.countDown();
            }

            public void onStopped() {
                controllerGate.countDown();
            }

            @Override
            public void onException(Throwable e) {
                if (controllerGate.getCount() > 0) {
                    getLogger().log(Level.SEVERE, "Exception during " +
                            "starting the controller", e);
                    controllerGate.countDown();
                    controllerGateStartupException = e;
                } else {
                    getLogger().log(Level.SEVERE, "Exception during " +
                            "controller processing", e);
                }
            }
        };
        controller.addStateListener( controllerStateListener );
        new Thread( controller ).start();
        long controllerStartTime = System.currentTimeMillis();
        try {
            controllerGate.await( startTimeoutMillis, TimeUnit.MILLISECONDS );
        } catch( InterruptedException e ) {
            e.printStackTrace();
        }
        long durationInMillis = System.currentTimeMillis() - controllerStartTime;

        // do not continue if controller did not start.
        if (!controller.isStarted() || !controllerGateIsReady) {
            if (controllerGateStartupException != null) {
                throw new IllegalStateException("Grizzly Controller was not started and ready after " + durationInMillis + " ms",
                        controllerGateStartupException);
            } else {
                throw new IllegalStateException("Grizzly Controller was not started and ready after " + durationInMillis + " ms");

            }
        } else if (controllerGateIsReady) {
            // todo: make this FINE in future.
            getLogger().config("Grizzly controller listening on " + tcpSelectorHandler.getInet() + ":" + tcpSelectorHandler.getPort() + ". Controller started in " + durationInMillis + " ms");
        }

        tcpPort = tcpSelectorHandler.getPort();

        if (localPeerID == null) {
            String uniqueHost = host;
            if (uniqueHost == null) {

                InetAddress firstInetAddress = NetworkUtility.getFirstInetAddress();
                if (firstInetAddress != null) {
                    uniqueHost = firstInetAddress.getHostAddress();
                }
            }
            if (uniqueHost == null)
                throw new IOException("can not find an unique host");
            localPeerID = new PeerID<GrizzlyPeerID>(new GrizzlyPeerID(uniqueHost, tcpPort, multicastAddress, multicastPort), groupName, instanceName);
            peerIDMap.put(instanceName, localPeerID);
            if (LOG.isLoggable(Level.FINE))
                LOG.log(Level.FINE, "local peer id = " + localPeerID);
        }
        tcpSender = new GrizzlyTCPConnectorWrapper(controller,
                sendWriteTimeoutMillis, host, tcpPort, localPeerID );
        GrizzlyUDPConnectorWrapper udpConnectorWrapper = new GrizzlyUDPConnectorWrapper( controller,
                                                                                         sendWriteTimeoutMillis,
                                                                                         host,
                                                                                         multicastPort,
                                                                                         multicastAddress,
                                                                                         localPeerID );
        udpSender = udpConnectorWrapper;
        List<PeerID> virtualPeerIdList = getVirtualPeerIDList( virtualUriList );
        if( virtualPeerIdList != null && !virtualPeerIdList.isEmpty() ) {
            // Comment out this thread pool until UDP unicast is implemented.
            //final boolean FAIRNESS = true;
//            ThreadFactory tf = new GMSThreadFactory("GMS-mcastSenderThreadPool-thread");
//
//            multicastSenderThreadPool = new ThreadPoolExecutor( 10, 10, 60 * 1000, TimeUnit.MILLISECONDS,
//                                                                new ArrayBlockingQueue<Runnable>( 1024, FAIRNESS ), tf);
            vms = new VirtualMulticastSender(this, virtualPeerIdList);
            multicastSender = vms;
        } else {
            if( GrizzlyUtil.isSupportNIOMulticast() ) {
                multicastSender = udpConnectorWrapper;
            } else {
                final boolean FAIRNESS = true;
                ThreadFactory tf = new GMSThreadFactory("GMS-McastMsgProcessor-Group-" + groupName + "-thread");
                multicastSenderThreadPool = new ThreadPoolExecutor( 10, 10, 60 * 1000, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>( 1024, FAIRNESS ), tf );
                multicastSender = new BlockingIOMulticastSender( host,
                                                                 multicastAddress,
                                                                 multicastPort,
                                                                 networkInterfaceName,
                                                                 multicastPacketSize,
                                                                 localPeerID,
                                                                 multicastSenderThreadPool,
                                                                 multicastTimeToLive,
                                                                 this );
            }
        }
        if( tcpSender != null )
            tcpSender.start();
        if( udpSender != null )
            udpSender.start();
        if( multicastSender != null )
            multicastSender.start();
        addMessageListener( new PingMessageListener() );
        addMessageListener( new PongMessageListener() );
        running = true;
        }

    @SuppressWarnings( "unchecked" )
    public void addRemotePeer( PeerID peerID, SelectionKey selectionKey ) {
        if( peerID == null )
            return;
        if( peerID.equals( localPeerID ) )
            return; // lookback
        String instanceName = peerID.getInstanceName();
        if( instanceName != null && peerID.getUniqueID() instanceof GrizzlyPeerID ) {
//            PeerID<GrizzlyPeerID> previous = peerIDMap.get(instanceName);
//            if (previous != null) {
//                if (previous.getUniqueID().getTcpPort() != ((GrizzlyPeerID) peerID.getUniqueID()).tcpPort) {
//                    LOG.log(Level.WARNING, "addRemotePeer(selectionKey): assertion failure: no mapping should have existed for member:"
//                            + instanceName + " existingID=" + previous + " adding peerid=" + peerID, new Exception("stack trace"));
//                }
//            }
            PeerID<GrizzlyPeerID> previous = peerIDMap.put( instanceName, peerID );
            if (previous == null) {
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine("addRemotePeer: " + instanceName + " peerId:" + peerID);
                }
            }
            if( selectionKey != null )
                selectionKeyMap.put( selectionKey, instanceName );
        }
        addToVMS(peerID);
    }

    @Override
    public void removeRemotePeer(String instanceName) {
        for (Map.Entry<SelectionKey, String> entry : selectionKeyMap.entrySet()) {
            if (entry.getValue().equals(instanceName)) {
                if (getLogger().isLoggable(Level.FINE)) {
                    getLogger().log(Level.FINE, "remove selection key for instance name: " + entry.getValue() + " selectionKey:" + entry.getKey());
                }
                tcpSelectorHandler.getSelectionKeyHandler().cancel(entry.getKey());
                selectionKeyMap.remove(entry.getKey());
            }
        }
    }

    public void removeRemotePeer( SelectionKey selectionKey ) {
        if(selectionKey == null) {
            return;
        }
        selectionKeyMap.remove(selectionKey);

        // Bug Fix. DO NOT REMOVE member name to peerid mapping when selection key is being removed.
        // THIS HAPPENS TOO FREQUENTLY.  Only remove this mapping when member fails or planned shutdown.\
        // This method was getting called by GrizzlyCacheableSelectionKeyHandler.cancel(SelectionKey).

        // use following line instead of remove call above if uncommenting the rest
//        String instanceName = selectionKeyMap.remove( selectionKey );
//      if( instanceName != null ) {
//          Level level = Level.FINEST;
//          if (LOG.isLoggable(level)) {
//              LOG.log(level, "removeRemotePeer selectionKey=" + selectionKey + " instanceName=" + instanceName,
//                      new Exception("stack trace"));
//          }
//          peerIDMap.remove( instanceName );
//      }
    }

    @Override
    public List<PeerID> getVirtualPeerIDList( String virtualUriList ) {
        if( virtualUriList == null )
            return null;
        LOG.config( "DISCOVERY_URI_LIST = " + virtualUriList );
        List<PeerID> virtualPeerIdList = new ArrayList<PeerID>();
        //if this object has multiple addresses that are comma separated
        if( virtualUriList.indexOf( "," ) > 0 ) {
            String addresses[] = virtualUriList.split( "," );
            if( addresses.length > 0 ) {
                List<String> virtualUriStringList = Arrays.asList( addresses );
                for( String uriString : virtualUriStringList ) {
                    try {
                        PeerID peerID = getPeerIDFromURI( uriString );
                        if( peerID != null ) {
                            virtualPeerIdList.add( peerID );
                            LOG.config( "VIRTUAL_MULTICAST_URI = " + uriString + ", Converted PeerID = " + peerID );
                        }
                    } catch( URISyntaxException use ) {
                        if( LOG.isLoggable( Level.CONFIG ) )
                            LOG.log( Level.CONFIG, "failed to parse the virtual multicast uri(" + uriString + ")", use );
                    }
                }
            }
        } else {
            //this object has only one address in it, so add it to the list
            try {
                PeerID peerID = getPeerIDFromURI( virtualUriList );
                if( peerID != null ) {
                    virtualPeerIdList.add( peerID );
                    LOG.config( "VIRTUAL_MULTICAST_URI = " + virtualUriList + ", Converted PeerID = " + peerID );
                }
            } catch( URISyntaxException use ) {
                if( LOG.isLoggable( Level.CONFIG ) )
                    LOG.log( Level.CONFIG, "failed to parse the virtual multicast uri(" + virtualUriList + ")", use );
            }
        }
        return virtualPeerIdList;
    }

    @Override
    public synchronized void stop() throws IOException {
        if( !running )
            return;
        running = false;
        super.stop();
        if( tcpSender != null )
            tcpSender.stop();
        if( udpSender != null )
            udpSender.stop();
        if( multicastSender != null )
            multicastSender.stop();
        if( multicastSenderThreadPool != null ) {
            multicastSenderThreadPool.shutdown();
        }
        peerIDMap.clear();
        selectionKeyMap.clear();
        pingMessageLockMap.clear();
        controller.stop();
        execService.shutdown();
    }

    public void beforeDispatchingMessage( MessageEvent messageEvent, Map piggyback ) {
        if( messageEvent == null )
            return;
        SelectionKey selectionKey = null;
        if( piggyback != null ) {
            Object value = piggyback.get( MESSAGE_SELECTION_KEY_TAG );
            if( value instanceof SelectionKey )
                selectionKey = (SelectionKey)value;
        }
        if (! isLeavingMessage(messageEvent)) {
            addRemotePeer(messageEvent.getSourcePeerID(), selectionKey);
        }
    }

    public void afterDispatchingMessage( MessageEvent messageEvent, Map piggyback ) {
    }

    @Override
    protected Logger getGrizzlyLogger() {
        return LoggerUtils.getLogger();
    }
}
