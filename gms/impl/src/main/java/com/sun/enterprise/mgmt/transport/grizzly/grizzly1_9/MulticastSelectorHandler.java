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

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.concurrent.Callable;
import java.util.logging.Level;

import com.sun.enterprise.mgmt.transport.NetworkUtility;
import com.sun.grizzly.CallbackHandler;
import com.sun.grizzly.CallbackHandlerSelectionKeyAttachment;
import com.sun.grizzly.ConnectorHandler;
import com.sun.grizzly.ConnectorInstanceHandler;
import com.sun.grizzly.Context;
import com.sun.grizzly.ReusableUDPSelectorHandler;
import com.sun.grizzly.Role;
import com.sun.grizzly.TCPSelectorHandler;
import com.sun.grizzly.async.UDPAsyncQueueReader;
import com.sun.grizzly.async.UDPAsyncQueueWriter;
import com.sun.grizzly.util.Copyable;

/**
 * @author Bongjae Chang
 */
public class MulticastSelectorHandler extends ReusableUDPSelectorHandler {

    private volatile InetAddress multicastAddress;
    private volatile NetworkInterface anInterface;

    // todo membership key management
    // private MembershipKey membershipKey;
    private Object membershipKey;

    private final Method joinMethod;

    public MulticastSelectorHandler() {
        try {
            anInterface = NetworkUtility.getFirstNetworkInterface(NetworkUtility.getPreferIpv6Addresses());
        } catch (IOException e) {
            e.printStackTrace();
        }
        Method method = null;
        try {
            // JDK 1.7
            method = DatagramChannel.class.getMethod("join", InetAddress.class, NetworkInterface.class);
        } catch (Throwable t) {
            method = null;
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "this JDK doesn't support DatagramChannel#join()", t);
            }
        }
        joinMethod = method;
    }

    @Override
    public void copyTo(Copyable copy) {
        super.copyTo(copy);
        MulticastSelectorHandler copyHandler = (MulticastSelectorHandler) copy;
        copyHandler.anInterface = anInterface;
        copyHandler.membershipKey = membershipKey;
    }

    /**
     * Before invoking Selector.select(), make sure the ServerScoketChannel has been created. If true, then register all
     * SelectionKey to the Selector.
     */
    @Override
    public void preSelect(Context ctx) throws IOException {

        if (asyncQueueReader == null) {
            asyncQueueReader = new UDPAsyncQueueReader(this);
        }

        if (asyncQueueWriter == null) {
            asyncQueueWriter = new UDPAsyncQueueWriter(this);
        }

        if (selector == null) {
            initSelector(ctx);
        } else {
            processPendingOperations(ctx);
        }
    }

    @SuppressWarnings("unchecked")
    private void initSelector(Context ctx) throws IOException {
        try {
            isShutDown.set(false);

            connectorInstanceHandler = new ConnectorInstanceHandler.ConcurrentQueueDelegateCIH(getConnectorInstanceHandlerDelegate());

            datagramChannel = DatagramChannel.open();
            selector = Selector.open();
            if (role != Role.CLIENT) {
                datagramSocket = datagramChannel.socket();
                datagramSocket.setReuseAddress(reuseAddress);
                if (inet == null) {
                    datagramSocket.bind(new InetSocketAddress(getPort()));
                } else {
                    datagramSocket.bind(new InetSocketAddress(inet, getPort()));
                }

                datagramChannel.configureBlocking(false);
                datagramChannel.register(selector, SelectionKey.OP_READ);

                datagramSocket.setSoTimeout(serverTimeout);

                if (multicastAddress != null && joinMethod != null) {
                    try {
                        membershipKey = joinMethod.invoke(datagramChannel, multicastAddress, anInterface);
                        // membershipKey = datagramChannel.join( multicastAddress, anInterface );
                    } catch (Throwable t) {
                        if (logger.isLoggable(Level.FINE)) {
                            logger.log(Level.FINE, "Exception occured when tried to join datagram channel", t);
                        }
                    }
                }
            }
            ctx.getController().notifyReady();
        } catch (SocketException ex) {
            throw new BindException(ex.getMessage() + ": " + getPort());
        }
    }

    public void setMulticastAddress(String multicastAddress) throws UnknownHostException {
        if (multicastAddress != null) {
            this.multicastAddress = InetAddress.getByName(multicastAddress);
        }
    }

    public void setNetworkInterface(String networkInterfaceName) throws SocketException {
        if (networkInterfaceName != null) {
            NetworkInterface anInterface = NetworkInterface.getByName(networkInterfaceName);
            if (NetworkUtility.supportsMulticast(anInterface)) {
                this.anInterface = anInterface;
            }
        }
    }

    /**
     * Handle new OP_CONNECT ops.
     */
    @Override
    protected void onConnectOp(Context ctx, TCPSelectorHandler.ConnectChannelOperation connectChannelOp) throws IOException {
        DatagramChannel newDatagramChannel = (DatagramChannel) connectChannelOp.getChannel();
        SocketAddress remoteAddress = connectChannelOp.getRemoteAddress();
        CallbackHandler callbackHandler = connectChannelOp.getCallbackHandler();

        CallbackHandlerSelectionKeyAttachment attachment = new CallbackHandlerSelectionKeyAttachment(callbackHandler);

        SelectionKey key = newDatagramChannel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE, attachment);
        attachment.associateKey(key);

        try {
            InetAddress remoteInetAddress = InetAddress.getByName(((InetSocketAddress) remoteAddress).getHostName());
            if (remoteInetAddress.isMulticastAddress()) {
                if (role == Role.CLIENT && joinMethod != null) {
                    joinMethod.invoke(newDatagramChannel, remoteInetAddress, anInterface);
                    // newDatagramChannel.join( remoteInetAddress, anInterface );
                }
            } else {
                newDatagramChannel.connect(remoteAddress);
            }
        } catch (Throwable t) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "Exception occured when tried to join or connect datagram channel", t);
            }
        }

        onConnectInterest(key, ctx);
    }

    // --------------- ConnectorInstanceHandler -----------------------------
    @Override
    protected Callable<ConnectorHandler> getConnectorInstanceHandlerDelegate() {
        return new Callable<ConnectorHandler>() {
            public ConnectorHandler call() throws Exception {
                return new MulticastConnectorHandler();
            }
        };
    }
}
