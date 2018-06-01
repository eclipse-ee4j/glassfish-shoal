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

import com.sun.enterprise.mgmt.transport.MessageIOException;
import com.sun.grizzly.ConnectorHandler;
import com.sun.grizzly.Controller;
import com.sun.grizzly.util.OutputWriter;
import com.sun.enterprise.ee.cms.impl.base.PeerID;
import com.sun.enterprise.mgmt.transport.Message;
import com.sun.enterprise.mgmt.transport.NetworkUtility;
import com.sun.enterprise.mgmt.transport.AbstractMultiMessageSender;
import com.sun.enterprise.mgmt.transport.grizzly.GrizzlyPeerID;

import java.io.IOException;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.InetAddress;
import java.nio.channels.DatagramChannel;
import java.util.List;

/**
 * @author Bongjae Chang
 */
public class GrizzlyUDPConnectorWrapper extends AbstractMultiMessageSender {

    private final Controller controller;
    private final long writeTimeout; // ms
    private final InetSocketAddress localSocketAddress;
    private final InetSocketAddress multicastSocketAddress;

    private static final String DEFAULT_MULTICAST_ADDRESS = "230.30.1.1";

    public GrizzlyUDPConnectorWrapper( Controller controller,
                                       long writeTimeout,
                                       String host,
                                       int port,
                                       String multicastAddress,
                                       PeerID<GrizzlyPeerID> localPeerID ) {
        this.controller = controller;
        this.writeTimeout = writeTimeout;
        this.localSocketAddress = host == null ? new InetSocketAddress(port) : new InetSocketAddress( host, port );
        this.multicastSocketAddress = new InetSocketAddress( multicastAddress == null ? DEFAULT_MULTICAST_ADDRESS : multicastAddress, port );
        this.localPeerID = localPeerID;
    }

    protected boolean doSend( final PeerID peerID, final Message message ) throws IOException {
        if( peerID == null )
            throw new IOException( "peer ID can not be null" );
        Serializable uniqueID = peerID.getUniqueID();
        SocketAddress remoteSocketAddress;
        if( uniqueID instanceof GrizzlyPeerID ) {
            GrizzlyPeerID grizzlyPeerID = (GrizzlyPeerID)uniqueID;
            remoteSocketAddress = new InetSocketAddress( grizzlyPeerID.getHost(), grizzlyPeerID.getMulticastPort() );
        } else {
            throw new IOException( "peer ID must be GrizzlyPeerID type" );
        }
        try {
            return send( remoteSocketAddress, null, message );
        } catch( IOException ie ) {
            // once retry
            return send( remoteSocketAddress, null, message );
        }
    }

    protected boolean doBroadcast( final Message message ) throws IOException {
        if( multicastSocketAddress == null )
            throw new IOException( "multicast address can not be null" );
        try {
            return send( multicastSocketAddress, localSocketAddress, message );
        } catch (MessageIOException mioe) {
            throw mioe;
        } catch( IOException ie ) {
            // once retry
            return send( multicastSocketAddress, localSocketAddress, message );
        }
    }

    private boolean send( SocketAddress remoteAddress, SocketAddress localAddress, Message message ) throws IOException {
        if( controller == null )
            throw new IOException( "grizzly controller must be initialized" );
        if( remoteAddress == null )
            throw new IOException( "remote address can not be null" );
        if( message == null )
            throw new IOException( "message can not be null" );
        ConnectorHandler connectorHandler = null;
        try {
            connectorHandler = controller.acquireConnectorHandler( Controller.Protocol.UDP );
            connectorHandler.connect( remoteAddress, localAddress );
            OutputWriter.flushChannel( (DatagramChannel)connectorHandler.getUnderlyingChannel(),
                                       remoteAddress,
                                       message.getPlainByteBuffer(),
                                       writeTimeout );
        } finally {
            if( connectorHandler != null ) {
                try {
                    connectorHandler.close();
                } catch( IOException e ) {}
                controller.releaseConnectorHandler( connectorHandler );
            }
        }
        return true;
    }
}
