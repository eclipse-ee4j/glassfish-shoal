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

import static com.sun.enterprise.mgmt.transport.grizzly.GrizzlyConfigConstants.CORE_POOLSIZE;
import static com.sun.enterprise.mgmt.transport.grizzly.GrizzlyConfigConstants.DISCOVERY_URI_LIST;
import static com.sun.enterprise.mgmt.transport.grizzly.GrizzlyConfigConstants.KEEP_ALIVE_TIME;
import static com.sun.enterprise.mgmt.transport.grizzly.GrizzlyConfigConstants.MAX_POOLSIZE;
import static com.sun.enterprise.mgmt.transport.grizzly.GrizzlyConfigConstants.POOL_QUEUE_SIZE;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.PortRange;
import org.glassfish.grizzly.config.SSLConfigurator;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.nio.transport.TCPNIOConnectorHandler;
import org.glassfish.grizzly.nio.transport.TCPNIOServerConnection;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.ssl.SSLBaseFilter;
import org.glassfish.grizzly.ssl.SSLEngineConfigurator;
import org.glassfish.grizzly.ssl.SSLFilter;
import org.glassfish.grizzly.threadpool.ThreadPoolConfig;

import com.sun.enterprise.ee.cms.impl.base.GMSThreadFactory;
import com.sun.enterprise.ee.cms.impl.base.PeerID;
import com.sun.enterprise.ee.cms.impl.base.Utility;
import com.sun.enterprise.ee.cms.impl.common.GMSContext;
import com.sun.enterprise.ee.cms.impl.common.GMSContextFactory;
import com.sun.enterprise.ee.cms.impl.common.GMSMonitor;
import com.sun.enterprise.mgmt.transport.BlockingIOMulticastSender;
import com.sun.enterprise.mgmt.transport.MessageEvent;
import com.sun.enterprise.mgmt.transport.NetworkUtility;
import com.sun.enterprise.mgmt.transport.VirtualMulticastSender;
import com.sun.enterprise.mgmt.transport.grizzly.GrizzlyPeerID;
import com.sun.enterprise.mgmt.transport.grizzly.PingMessageListener;
import com.sun.enterprise.mgmt.transport.grizzly.PongMessageListener;

/**
 * @author Bongjae Chang
 */
public class GrizzlyNetworkManager2 extends com.sun.enterprise.mgmt.transport.grizzly.GrizzlyNetworkManager {
	public static final String MESSAGE_CONNECTION_TAG = "connection";
	private static final int SERVER_CONNECTION_BACKLOG = 4096;

	private InetAddress tcpListenerAddress = null;
	private int maxPoolSize;
	private int corePoolSize;
	private long keepAliveTime; // ms
	private int poolQueueSize;
	private String virtualUriList;
	private ExecutorService multicastSenderThreadPool = null;
	private TCPNIOTransport tcpNioTransport;
	private ConnectionCache tcpNioConnectionCache;
	private SSLEngineConfigurator clientSslEngineConfigurator;
	private SSLEngineConfigurator serverSslEngineConfigurator;
	private Boolean RENEGOTIATE_ON_CLIENTAUTHWANT;

	private final ConcurrentHashMap<String, Instance> instances = new ConcurrentHashMap<String, Instance>();

	public GrizzlyNetworkManager2() {
	}

	public void localConfigure(final Map properties) {
		maxPoolSize = Utility.getIntProperty(MAX_POOLSIZE.toString(), 50, properties);
		corePoolSize = Utility.getIntProperty(CORE_POOLSIZE.toString(), 20, properties);
		keepAliveTime = Utility.getLongProperty(KEEP_ALIVE_TIME.toString(), 60 * 1000, properties);
		poolQueueSize = Utility.getIntProperty(POOL_QUEUE_SIZE.toString(), 1024 * 4, properties);
		virtualUriList = Utility.getStringProperty(DISCOVERY_URI_LIST.toString(), null, properties);
		if (properties != null) {
			clientSslEngineConfigurator = (SSLEngineConfigurator) properties.get("CLIENT_SSLENGINECONFIGURATOR");
			if (clientSslEngineConfigurator != null) {
				logConfig("gms client ssl engine configurator", clientSslEngineConfigurator);
			}
			serverSslEngineConfigurator = (SSLEngineConfigurator) properties.get("SERVER_SSLENGINECONFIGURATOR");
			if (serverSslEngineConfigurator != null) {
				logConfig("gms server ssl engine configurator", serverSslEngineConfigurator);
			}
			RENEGOTIATE_ON_CLIENTAUTHWANT = Utility.getBooleanProperty("SSL_RENEGOTIATE_ON_CLIENTAUTHWANT", false, properties);
			if (clientSslEngineConfigurator != null) {
				getLogger().config("SSL RENEGOTIATION_ON_CLIENTAUTHWANT=" + RENEGOTIATE_ON_CLIENTAUTHWANT);
			}
		}
	}

	private void logConfig(String description, SSLEngineConfigurator engConfig) {
		if (engConfig != null && getLogger().isLoggable(Level.CONFIG)) {
			StringBuffer buf = new StringBuffer();
			buf.append(description).append(" SSLEngineConfigurator");
			if (engConfig != null && engConfig instanceof SSLConfigurator) {
				SSLConfigurator sslConfigurator = (SSLConfigurator) engConfig;
				if (sslConfigurator.getSslImplementation() != null) {
					buf.append(" [ssl impl=").append(sslConfigurator.getSslImplementation().getImplementationName()).append("]");
				}
			}
			buf.append(" [Enabled Protocols:");
			String[] protocols = engConfig.getEnabledProtocols();
			if (protocols != null) {
				for (String p : protocols) {
					buf.append(p).append(",");
				}
				buf.append("]");
			}
			buf.append(" Provider=").append(engConfig.getSslContext().getProvider().getName());
			getLogger().config(buf.toString());
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public synchronized void initialize(final String groupName, final String instanceName, final Map properties) throws IOException {
		super.initialize(groupName, instanceName, properties);
		this.instanceName = instanceName;
		this.groupName = groupName;
		getLogger().info("Grizzly 2.0 NetworkManager");
		configure(properties);
		localConfigure(properties);
		GMSContext ctx = GMSContextFactory.getGMSContext(groupName);
		if (ctx != null) {
			GMSMonitor monitor = ctx.getGMSMonitor();
			if (monitor != null) {
				monitor.setSendWriteTimeout(this.sendWriteTimeoutMillis);
			}
		}

		final TCPNIOTransportBuilder tcpTransportBuilder = TCPNIOTransportBuilder.newInstance();

		final ThreadPoolConfig threadPoolConfig = tcpTransportBuilder.getWorkerThreadPoolConfig();

		if (threadPoolConfig != null) {
			threadPoolConfig.setPoolName("GMS-GrizzlyControllerThreadPool-Group-" + groupName).setCorePoolSize(corePoolSize).setMaxPoolSize(maxPoolSize)
			        .setQueue(new ArrayBlockingQueue<Runnable>(poolQueueSize)).setQueueLimit(poolQueueSize)
			        .setKeepAliveTime(keepAliveTime, TimeUnit.MILLISECONDS).setPriority(Thread.NORM_PRIORITY);
		}

		final TCPNIOTransport transport = tcpTransportBuilder.build();

		final TCPNIOServerConnection serverConnection = transport.bind(host != null ? host : NetworkUtility.getAnyAddress().getHostAddress(),
		        new PortRange(tcpStartPort, tcpEndPort), SERVER_CONNECTION_BACKLOG);
		tcpListenerAddress = ((InetSocketAddress) serverConnection.getLocalAddress()).getAddress();
		tcpPort = ((InetSocketAddress) serverConnection.getLocalAddress()).getPort();

		final FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();
		filterChainBuilder.add(new TransportFilter());
		if (serverSslEngineConfigurator != null && clientSslEngineConfigurator != null) {
			getLogger().log(Level.CONFIG,
			        "Configuring SSL for point to point listener filter chain.  ServerSslEngineConfigurator=" + serverSslEngineConfigurator);
			filterChainBuilder.add(new SSLBaseFilter(serverSslEngineConfigurator, RENEGOTIATE_ON_CLIENTAUTHWANT));
		}
		filterChainBuilder.add(new MessageFilter());
		filterChainBuilder.add(new MessageDispatcherFilter(this));

		transport.setProcessor(filterChainBuilder.build());

		tcpNioTransport = transport;
		final FilterChainBuilder senderFilterChainBuilder = FilterChainBuilder.stateless().add(new TransportFilter());
		if (serverSslEngineConfigurator != null && clientSslEngineConfigurator != null) {
			getLogger().config("Configuring SSL for point to point sender filter chain clientSslEngineConfigurator=" + clientSslEngineConfigurator);
			senderFilterChainBuilder.add(new SSLFilter(clientSslEngineConfigurator, clientSslEngineConfigurator));
		}
		senderFilterChainBuilder.add(new CloseOnReadFilter()).add(new MessageFilter());

		final TCPNIOConnectorHandler senderConnectorHandler = TCPNIOConnectorHandler.builder(transport).processor(senderFilterChainBuilder.build()).build();

		tcpNioConnectionCache = new ConnectionCache(senderConnectorHandler, highWaterMark, maxParallelSendConnections, numberToReclaim);
	}

	@Override
	@SuppressWarnings("unchecked")
	public synchronized void start() throws IOException {
		if (running) {
			return;
		}
		super.start();

		final long transportStartTime = System.currentTimeMillis();

		tcpNioTransport.start();

		final long durationInMillis = System.currentTimeMillis() - transportStartTime;

		getLogger().log(Level.CONFIG, "Grizzly controller listening on {0}:{1}. Transport started in {2} ms",
		        new Object[] { tcpListenerAddress, Integer.toString(tcpPort), durationInMillis });

		if (localPeerID == null) {
			String uniqueHost = host;
			if (uniqueHost == null) {
				// prefer IPv4
				InetAddress firstInetAddress = NetworkUtility.getFirstInetAddress();
				if (firstInetAddress != null) {
					uniqueHost = firstInetAddress.getHostAddress();
				}
			}
			if (uniqueHost == null) {
				throw new IOException("can not find an unique host");
			}
			localPeerID = new PeerID<GrizzlyPeerID>(new GrizzlyPeerID(uniqueHost, tcpPort, multicastAddress, multicastPort), groupName, instanceName);
			peerIDMap.put(instanceName, localPeerID);
			if (LOG.isLoggable(Level.FINE)) {
				LOG.log(Level.FINE, "local peer id = {0}", localPeerID);
			}
		}

		tcpSender = new GrizzlyTCPMessageSender(tcpNioTransport, tcpNioConnectionCache, localPeerID, sendWriteTimeoutMillis);
		udpSender = null;

		List<PeerID> virtualPeerIdList = getVirtualPeerIDList(virtualUriList);
		if (virtualPeerIdList != null && !virtualPeerIdList.isEmpty()) {
			// Comment out this UDP receive thread pool until unicast UDP is implemented.
//            final boolean FAIRNESS = true;
//            ThreadFactory tf = new GMSThreadFactory("GMS-mcastSenderThreadPool-thread");
//
//            multicastSenderThreadPool = new ThreadPoolExecutor(
//                    10, 10, 60 * 1000, TimeUnit.MILLISECONDS,
//                    new ArrayBlockingQueue<Runnable>(1024, FAIRNESS), tf);
			vms = new VirtualMulticastSender(this, virtualPeerIdList);
			multicastSender = vms;
		} else {
//            if( GrizzlyUtil.isSupportNIOMulticast() ) {
//                multicastSender = udpConnectorWrapper;
//            } else {
			final boolean FAIRNESS = true;
			ThreadFactory tf = new GMSThreadFactory("GMS-McastMsgProcessor-Group-" + groupName + "-thread");
			multicastSenderThreadPool = new ThreadPoolExecutor(10, 10, 60 * 1000, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(1024, FAIRNESS), tf);
			multicastSender = new BlockingIOMulticastSender(host, multicastAddress, multicastPort, networkInterfaceName, multicastPacketSize, localPeerID,
			        multicastSenderThreadPool, multicastTimeToLive, this);
//            }
		}
		if (tcpSender != null) {
			tcpSender.start();
		}
		if (udpSender != null) {
			udpSender.start();
		}
		if (multicastSender != null) {
			multicastSender.start();
		}
		addMessageListener(new PingMessageListener());
		addMessageListener(new PongMessageListener());
		running = true;

	}

	@Override
	public synchronized void stop() throws IOException {
		if (!running) {
			return;
		}
		running = false;
		super.stop();
		if (tcpSender != null) {
			tcpSender.stop();
		}
		if (udpSender != null) {
			udpSender.stop();
		}
		if (multicastSender != null) {
			multicastSender.stop();
		}
		if (multicastSenderThreadPool != null) {
			multicastSenderThreadPool.shutdown();
		}
		peerIDMap.clear();
//        selectionKeyMap.clear();
		pingMessageLockMap.clear();
//        controller.stop();
		tcpNioConnectionCache.close();
		tcpNioTransport.stop();
//        execService.shutdown();
	}

	@Override
	public void beforeDispatchingMessage(final MessageEvent messageEvent, final Map piggyback) {

		if (messageEvent == null) {
			return;
		}

		Connection connection = null;
		if (piggyback != null) {
			connection = (Connection) piggyback.get(MESSAGE_CONNECTION_TAG);
		}
		if (!isLeavingMessage(messageEvent)) {
			addRemotePeer(messageEvent.getSourcePeerID(), connection);
		}
	}

	@SuppressWarnings("unchecked")
	public void addRemotePeer(final PeerID peerID, final Connection connection) {
		if (peerID == null) {
			return;
		}
		if (peerID.equals(localPeerID)) {
			return; // lookback
		}

		final String peerInstanceName = peerID.getInstanceName();
		if (peerInstanceName != null && peerID.getUniqueID() instanceof GrizzlyPeerID) {
			final PeerID<GrizzlyPeerID> previous = peerIDMap.put(peerInstanceName, peerID);
			if (previous == null) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.log(Level.FINE, "addRemotePeer: {0} peerId:{1}", new Object[] { peerInstanceName, peerID });
				}
			}

			if (connection != null) {
				obtainInstance(peerInstanceName).register(connection);
			}
		}
		addToVMS(peerID);
	}

	@Override
	public void removeRemotePeer(final String instanceName) {
		final Instance instance = instances.remove(instanceName);
		if (instance != null) {
			instance.close();
		}
	}

	@Override
	protected Logger getGrizzlyLogger() {
		return Grizzly.logger(GrizzlyNetworkManager2.class);
	}

	private Instance obtainInstance(final String instance) {
		Instance instanceObj = instances.get(instance);
		if (instanceObj == null) {
			final Instance newInstance = new Instance();
			instanceObj = instances.putIfAbsent(instance, newInstance);
			if (instanceObj == null) {
				instanceObj = newInstance;
			}
		}

		return instanceObj;
	}

	/**
	 * Filter, which is used by Senders, which don't expect any input data. So we close {@link Connection} if any data
	 * comes.
	 */
	static class CloseOnReadFilter extends BaseFilter {

		@Override
		public NextAction handleRead(final FilterChainContext ctx) throws IOException {

			ctx.getConnection().close();

			return ctx.getStopAction();
		}
	}

	/**
	 * Class represents instance and associated connections
	 */
	static class Instance {
		final AtomicBoolean isClosed = new AtomicBoolean();

		final ConcurrentHashMap<Connection, Long> connections = new ConcurrentHashMap<Connection, Long>();

		final Connection.CloseListener closeListener = new CloseListener();

		void register(final Connection connection) {
			if (connections.putIfAbsent(connection, System.currentTimeMillis()) == null) {
				connection.addCloseListener(closeListener);

				if (isClosed.get()) {
					connection.close();
				}
			}

		}

		void close() {
			if (!isClosed.getAndSet(true)) {
				for (Iterator<Connection> it = connections.keySet().iterator(); it.hasNext();) {
					final Connection connection = it.next();
					it.remove();
					connection.close();
				}

			}
		}

		private class CloseListener implements Connection.CloseListener {

			@Override
			public void onClosed(Connection connection, Connection.CloseType type) throws IOException {
				connections.remove(connection);
			}
		}
	}
}
