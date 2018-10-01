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
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.enterprise.ee.cms.impl.base.PeerID;
import com.sun.enterprise.mgmt.transport.AbstractMessageSender;
import com.sun.enterprise.mgmt.transport.AbstractNetworkManager;
import com.sun.enterprise.mgmt.transport.Message;
import com.sun.enterprise.mgmt.transport.MessageIOException;
import com.sun.enterprise.mgmt.transport.grizzly.GrizzlyNetworkManager;
import com.sun.enterprise.mgmt.transport.grizzly.GrizzlyPeerID;
import com.sun.grizzly.AbstractConnectorHandler;
import com.sun.grizzly.CallbackHandler;
import com.sun.grizzly.ConnectorHandler;
import com.sun.grizzly.Context;
import com.sun.grizzly.Controller;
import com.sun.grizzly.IOEvent;
import com.sun.grizzly.connectioncache.client.CacheableConnectorHandler;
import com.sun.grizzly.util.OutputWriter;

/**
 * @author Bongjae Chang
 */
public class GrizzlyTCPConnectorWrapper extends AbstractMessageSender {

	private final Logger LOG = GrizzlyNetworkManager.getLogger();
	private final Controller controller;
	private final long writeTimeout; // ms
	private final InetSocketAddress localSocketAddress; // todo not used

	public GrizzlyTCPConnectorWrapper(Controller controller, long writeTimeout, String host, int port, PeerID<GrizzlyPeerID> localPeerID) {
		this.controller = controller;
		this.writeTimeout = writeTimeout;
		if (host != null) {
			this.localSocketAddress = new InetSocketAddress(host, port);
		} else {
			this.localSocketAddress = null;
		}
		this.localPeerID = localPeerID;
	}

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

		return send(remoteSocketAddress, null, message, peerID);
	}

	@SuppressWarnings("unchecked")
	private boolean send(SocketAddress remoteAddress, SocketAddress localAddress, Message message, PeerID target) throws IOException {
		final int MAX_RESEND_ATTEMPTS = 4;
		if (controller == null) {
			throw new IOException("grizzly controller must be initialized");
		}
		if (remoteAddress == null) {
			throw new IOException("remote address can not be null");
		}
		if (message == null) {
			throw new IOException("message can not be null");
		}
		ConnectorHandler connectorHandler = null;
		try {
			long startGetConnectorHandler = System.currentTimeMillis();
			connectorHandler = controller.acquireConnectorHandler(Controller.Protocol.TCP);
			long durationGetConnectorHandler = System.currentTimeMillis() - startGetConnectorHandler;
			if (durationGetConnectorHandler > 1000) {
				AbstractNetworkManager.getLogger().log(Level.WARNING, "grizzlytcpconnectorwrapper.wait.for.getconnector", durationGetConnectorHandler);
			}
			int attemptNo = 1;
			do {
				try {
					connectorHandler.connect(remoteAddress, localAddress, new CloseControlCallbackHandler(connectorHandler));
				} catch (Throwable t) {

					// close connectorHandler.
					try {
						connectorHandler.close();
					} catch (Throwable tt) {
						// ignore
					}

					// include local call stack.
					IOException localIOE = new IOException("failed to connect to " + target.toString(), t);
					// AbstractNetworkManager.getLogger().log(Level.WARNING, "failed to connect to target " + target.toString(), localIOE);
					throw localIOE;
				}
				try {
					OutputWriter.flushChannel(connectorHandler.getUnderlyingChannel(), message.getPlainByteBuffer(), writeTimeout);
					connectorHandler.close();
					break;
				} catch (MessageIOException mioe) {
					// thrown when message size is too big.
					forceClose(connectorHandler);
					throw mioe;
				} catch (Exception e) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.log(Level.FINE, "exception during the flushChannel call. Retrying with another connection #" + attemptNo, e);
					}

					forceClose(connectorHandler);
				}

				attemptNo++;
			} while (attemptNo <= MAX_RESEND_ATTEMPTS);
		} finally {
			controller.releaseConnectorHandler(connectorHandler);
		}

		return true;
	}

	private void forceClose(ConnectorHandler connectorHandler) throws IOException {
		if (connectorHandler instanceof CacheableConnectorHandler) {
			((CacheableConnectorHandler) connectorHandler).forceClose();
		}
	}

	private static final class CloseControlCallbackHandler implements CallbackHandler<Context> {

		private final ConnectorHandler connectorHandler;

		public CloseControlCallbackHandler(ConnectorHandler connectorHandler) {
			this.connectorHandler = connectorHandler;
		}

		@Override
		public void onConnect(IOEvent<Context> ioEvent) {
			SelectionKey key = ioEvent.attachment().getSelectionKey();
			if (connectorHandler instanceof AbstractConnectorHandler) {
				((AbstractConnectorHandler) connectorHandler).setUnderlyingChannel(key.channel());
			}

			try {
				connectorHandler.finishConnect(key);
				ioEvent.attachment().getSelectorHandler().register(key, SelectionKey.OP_READ);
			} catch (IOException ex) {
				Controller.logger().severe(ex.getMessage());
			}
		}

		@Override
		public void onRead(IOEvent<Context> ioEvent) {
			// We don't expect any read, so if any data comes - we suppose it's "close" notification
			final Context context = ioEvent.attachment();
			final SelectionKey selectionKey = context.getSelectionKey();
			// close the channel
			context.getSelectorHandler().addPendingKeyCancel(selectionKey);
		}

		@Override
		public void onWrite(IOEvent<Context> ioe) {
		}

	}
}
