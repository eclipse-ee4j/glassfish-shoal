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

package com.sun.enterprise.mgmt.transport.grizzly.grizzly2;

import java.io.IOException;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.WriteResult;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.utils.Futures;

import com.sun.enterprise.ee.cms.impl.base.PeerID;
import com.sun.enterprise.mgmt.transport.AbstractMessageSender;
import com.sun.enterprise.mgmt.transport.Message;
import com.sun.enterprise.mgmt.transport.MessageIOException;
import com.sun.enterprise.mgmt.transport.grizzly.GrizzlyNetworkManager;
import com.sun.enterprise.mgmt.transport.grizzly.GrizzlyPeerID;

/**
 * @author Bongjae Chang
 */
public class GrizzlyTCPMessageSender extends AbstractMessageSender {

    private final static Logger LOG = GrizzlyNetworkManager.getLogger();
    private final TCPNIOTransport tcpNioTransport;

    private final ConnectionCache connectionCache;
    private final long writeTimeoutMillis;

    public GrizzlyTCPMessageSender(final TCPNIOTransport tcpNioTransport, final ConnectionCache connectionCache, final PeerID<GrizzlyPeerID> localPeerID,
            final long writeTimeoutMillis) {
        this.tcpNioTransport = tcpNioTransport;
        this.localPeerID = localPeerID;
        this.connectionCache = connectionCache;
        this.writeTimeoutMillis = writeTimeoutMillis;
    }

    @Override
    protected boolean doSend(final PeerID peerID, final Message message) throws IOException {

        if (peerID == null) {
            throw new IOException("peer ID can not be null");
        }
        Serializable uniqueID = peerID.getUniqueID();
        SocketAddress remoteSocketAddress;
        if (uniqueID instanceof GrizzlyPeerID) {
            GrizzlyPeerID grizzlyPeerID = (GrizzlyPeerID) uniqueID;
            remoteSocketAddress = new InetSocketAddress(grizzlyPeerID.getHost(), grizzlyPeerID.getTcpPort());
        } else {
            throw new IOException("peer ID must be GrizzlyPeerID type");
        }

        return send(null, remoteSocketAddress, message, peerID);
    }

    @SuppressWarnings("unchecked")
    private boolean send(final SocketAddress localAddress, final SocketAddress remoteAddress, final Message message, final PeerID target) throws IOException {

        final int MAX_RESEND_ATTEMPTS = 4;
        if (tcpNioTransport == null) {
            throw new IOException("grizzly controller must be initialized");
        }
        if (remoteAddress == null) {
            throw new IOException("remote address can not be null");
        }
        if (message == null) {
            throw new IOException("message can not be null");
        }

        int attemptNo = 1;
        do {
            final Connection connection;

            try {
                connection = connectionCache.poll(localAddress, remoteAddress);
                if (connection == null) {
                    LOG.log(Level.WARNING,
                            "failed to get a connection from connectionCache in attempt# " + attemptNo + ". GrizzlyTCPMessageSender.send(localAddr="
                                    + localAddress + " , remoteAddr=" + remoteAddress + " sendTo=" + target + " msg=" + message + ". Retrying send",
                            new Exception("stack trace"));
                    // try again.
                    continue;
                }
            } catch (Throwable t) {
                // include local call stack.
                final IOException localIOE = new IOException("failed to connect to " + target.toString(), t);
                // AbstractNetworkManager.getLogger().log(Level.WARNING, "failed to connect to target " + target.toString(), localIOE);
                throw localIOE;
            }

            try {
                final FutureImpl<WriteResult> syncWriteFuture = Futures.createSafeFuture();
                connection.write(remoteAddress, message, Futures.toCompletionHandler(syncWriteFuture), null);
                syncWriteFuture.get(writeTimeoutMillis, TimeUnit.MILLISECONDS);

                connectionCache.offer(connection);
                return true;
            } catch (Exception e) {

                // following exception is getting thrown java.util.concurrent.ExecutionException with MessageIOException
                // as cause when calling synchWriteFuture.get. Unwrap the cause, check it and
                // get this to fail immediately if cause is a MessageIOException.
                Throwable cause = e.getCause();
                if (cause instanceof MessageIOException) {
                    try {
                        connection.close();
                    } catch (Throwable t) {
                    }
                    throw (MessageIOException) cause;
                }

                // TODO: Turn this back to FINE in future. Need to track these for the time being.
                if (LOG.isLoggable(Level.INFO)) {
                    LOG.log(Level.INFO, "exception writing message to connection. Retrying with another connection #" + attemptNo, e);
                }
                connection.close();
            }

            attemptNo++;
        } while (attemptNo <= MAX_RESEND_ATTEMPTS);

        return false;
    }
}
