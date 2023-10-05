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

import net.jxta.protocol.PipeAdvertisement;
import net.jxta.protocol.RouteAdvertisement;
import net.jxta.pipe.PipeService;
import net.jxta.pipe.InputPipe;
import net.jxta.pipe.OutputPipe;
import net.jxta.pipe.PipeMsgListener;
import net.jxta.peer.PeerID;
import net.jxta.impl.pipe.BlockingWireOutputPipe;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Map;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class manages Jxta's InputPipe and OutputPipe and sends Jxta's message through JxtaUtility
 *
 * This stores and caches Jxta's OutputPipe according to Jxta's PeerID
 *
 * @author Bongjae Chang
 */
public class JxtaPipeManager {

    private static final Logger LOG = JxtaUtil.getLogger();

    private final JxtaNetworkManager networkManager;

    private final PipeService pipeService;
    private final PipeAdvertisement pipeAdv;
    private final PipeMsgListener pipeMsgListener;
    private InputPipe inputPipe;
    private OutputPipe outputPipe;
    private Map<net.jxta.peer.PeerID, OutputPipe> pipeCache = new ConcurrentHashMap<net.jxta.peer.PeerID, OutputPipe>();

    // Time duration in milliseconds to wait for a successful pipe resolution
    private final long pipeResolutionTimeout = 100; // ms

    public JxtaPipeManager( JxtaNetworkManager networkManager, PipeService pipeService, PipeAdvertisement pipeAdv, PipeMsgListener pipeMsgListener ) {
        this.networkManager = networkManager;
        this.pipeService = pipeService;
        this.pipeAdv = pipeAdv;
        this.pipeMsgListener = pipeMsgListener;
    }

    public void start() {
        try {
            outputPipe = pipeService.createOutputPipe( pipeAdv, pipeResolutionTimeout );
        } catch( IOException io ) {
            LOG.log( Level.FINE, "Failed to create master outputPipe", io );
        }
        try {
            inputPipe = pipeService.createInputPipe( pipeAdv, pipeMsgListener );
        } catch( IOException ioe ) {
            LOG.log( Level.SEVERE, "Failed to create service input pipe: " + ioe );
        }
    }

    public void stop() {
        if( outputPipe != null )
            outputPipe.close();
        if( inputPipe != null )
            inputPipe.close();
        pipeCache.clear();
    }

    /**
     * in the event of a failure or planned shutdown, remove the
     * pipe from the pipeCache
     *
     * @param peerid peerID
     */
    public void removePipeFromCache( net.jxta.peer.PeerID peerid ) {
        pipeCache.remove( peerid );
    }

    public void clearPipeCache() {
        pipeCache.clear();
    }

    public boolean send( net.jxta.peer.PeerID peerid, net.jxta.endpoint.Message message ) throws IOException {
        OutputPipe output = pipeCache.get( peerid );
        RouteAdvertisement route = null;
        final int MAX_RETRIES = 2;
        IOException lastOne = null;
        if( output != null && output.isClosed() )
            output = null;
        for( int createOutputPipeAttempts = 0; output == null && createOutputPipeAttempts < MAX_RETRIES; createOutputPipeAttempts++ )
        {
            route = networkManager.getCachedRoute( peerid );
            if( route != null ) {
                try {
                    output = new BlockingWireOutputPipe( networkManager.getNetPeerGroup(), pipeAdv, peerid, route );
                } catch( Exception ioe ) {
                    lastOne = ioe;
                }
            }
            if( output == null ) {
                // Unicast datagram
                // create a op pipe to the destination peer
                try {
                    output = pipeService.createOutputPipe( pipeAdv, Collections.singleton( peerid ), 1 );
                    if( LOG.isLoggable( Level.FINE ) && output != null ) {
                        LOG.fine( "ClusterManager.send : adding output to cache without route creation : " + peerid );
                    }
                } catch( IOException ioe ) {
                    lastOne = ioe;
                }
            }
        }
        if( output != null ) {
            pipeCache.put( peerid, output );
            return JxtaUtil.send( output, message );
        } else {
            LOG.log( Level.WARNING, "ClusterManager.send : sending of message " + message + " failed. Unable to create an OutputPipe for " + peerid +
                                    " route = " + route, lastOne );
            return false;
        }
    }

    public boolean broadcast( net.jxta.endpoint.Message message ) throws IOException {
        return JxtaUtil.send( outputPipe, message );
    }
}
