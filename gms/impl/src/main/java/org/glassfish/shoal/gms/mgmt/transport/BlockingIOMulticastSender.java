/*
 * Copyright (c) 1997, 2018 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020 Payara Services Ltd.
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

package org.glassfish.shoal.gms.mgmt.transport;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.shoal.gms.api.core.GMSConstants;
import org.glassfish.shoal.gms.base.PeerID;
import org.glassfish.shoal.gms.logging.GMSLogDomain;

/**
 * This class is a default {@link MulticastMessageSender}'s implementation and extends
 * {@link AbstractMulticastMessageSender}
 *
 * This uses <code>MulticastSocket</code> which is based on Blocking I/O
 *
 * @author Bongjae Chang
 */
public class BlockingIOMulticastSender extends AbstractMulticastMessageSender implements Runnable {

    private static final Logger LOG = GMSLogDomain.getLogger(GMSLogDomain.GMS_LOGGER);
    private static final Logger monitorLog = GMSLogDomain.getMonitorLogger();
    private static final Logger mcastLog = GMSLogDomain.getMcastLogger();

    private final InetSocketAddress localSocketAddress;
    private final InetAddress multicastAddress;
    private final int multicastPort;
    private final InetSocketAddress multicastSocketAddress;
    private final Executor executor;
    private final NetworkManager networkManager;

    private NetworkInterface anInterface;
    private int multicastPacketSize;
    private MulticastSocket multicastSocket;
    private Thread multicastThread;

    private volatile boolean running;
    private CountDownLatch endGate = new CountDownLatch(1);
    private long shutdownTimeout = 5 * 1000; // ms

    private static final String DEFAULT_MULTICAST_ADDRESS = "230.30.1.1";
    private static final int DEFAULT_MULTICAST_PACKET_SIZE = 16384;

    private int multicastTimeToLive = GMSConstants.DEFAULT_MULTICAST_TIME_TO_LIVE;

    private final ThreadPoolExecutor threadPoolExecutor;
    private long maxExecutorQueueSize = 0;
    private long rejectedExecution = 0;
    private final boolean monitoringEnabled;

    public BlockingIOMulticastSender(String host, String multicastAddress, int multicastPort, String networkInterfaceName, int multicastPacketSize,
            PeerID localPeerID, Executor executor, int multicastTimeToLive, NetworkManager networkManager) throws IOException {
        this.localSocketAddress = host == null ? null : new InetSocketAddress(host, multicastPort);
        this.multicastPort = multicastPort;
        if (multicastAddress == null) {
            multicastAddress = DEFAULT_MULTICAST_ADDRESS;
        }
        this.multicastAddress = InetAddress.getByName(multicastAddress);
        this.multicastSocketAddress = new InetSocketAddress(multicastAddress, multicastPort);
        if (networkInterfaceName != null) {
            NetworkInterface anInterface = NetworkInterface.getByName(networkInterfaceName);
            if (NetworkUtility.supportsMulticast(anInterface) && anInterface.isUp()) {
                this.anInterface = anInterface;
            }
        }
        if (multicastPacketSize < DEFAULT_MULTICAST_PACKET_SIZE) {
            this.multicastPacketSize = DEFAULT_MULTICAST_PACKET_SIZE;
        } else {
            this.multicastPacketSize = multicastPacketSize;
        }
        this.localPeerID = localPeerID;
        this.executor = executor;
        this.threadPoolExecutor = executor instanceof ThreadPoolExecutor ? (ThreadPoolExecutor) executor : null;
        this.networkManager = networkManager;
        this.multicastTimeToLive = multicastTimeToLive;

        // DO NOT CHECK NEXT lines. DEBUG ONLY
        // if (monitorLog.getLevel() == Level.INFO) {
        // monitorLog.setLevel(Level.FINE);
        // }
        // END DO NOT CHECK IN

        this.monitoringEnabled = monitorLog.isLoggable(Level.FINE);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void start() throws IOException {
        if (running) {
            return;
        }
        super.start();
        multicastSocket = new MulticastSocket(multicastPort);

        // fix for GLASSFISH-16103. setNetworkInterface was not working on a IPv6-only system that had an non running
        // network interface for etho.
        if (anInterface != null && localSocketAddress != null) {
            try {
                multicastSocket.setInterface(localSocketAddress.getAddress());
            } catch (Exception e) {
                LOG.log(Level.WARNING, "mgmt.blockingiomulticast.setinterfacefailed",
                        new Object[] { localSocketAddress.getAddress(), multicastSocket.getInterface() });
            }
        }

        // enable loopback.
        multicastSocket.setLoopbackMode(false);

        // enforce a minimum for time to live UNLESS it is explicitly set via GMS configuration property.
        if (multicastTimeToLive > 0) {

            // explicitly configured. no minimum for this case.
            try {
                multicastSocket.setTimeToLive(this.multicastTimeToLive);
                LOG.config("set via property: MulticastSocket.getTimeToLive()=" + multicastSocket.getTimeToLive());
            } catch (IOException ioe) {
                LOG.log(Level.WARNING, "blockingiomcast.fail.set.timetolive", new Object[] { this.multicastTimeToLive });
            } catch (IllegalArgumentException iae) {
                LOG.log(Level.WARNING, "blockingiomcast.fail.set.timetolive", new Object[] { this.multicastTimeToLive });
            }
        } else {

            // get default value and if less than MIN, then increase time to live to GMS specified MIN.
            int timeToLive = multicastSocket.getTimeToLive();
            if (timeToLive < GMSConstants.MINIMUM_MULTICAST_TIME_TO_LIVE) {
                try {
                    multicastTimeToLive = GMSConstants.MINIMUM_MULTICAST_TIME_TO_LIVE;
                    multicastSocket.setTimeToLive(this.multicastTimeToLive);
                    LOG.config("Set via default minimum: MulticastSocket.getTimeToLive()=" + multicastSocket.getTimeToLive());
                } catch (IOException ioe) {
                    LOG.log(Level.WARNING, "blockingiomcast.fail.set.timetolive", new Object[] { this.multicastTimeToLive });
                }
            }
        }
        running = true;

        // todo need to use a thread factory?
        multicastThread = new Thread(this, "IP Multicast Listener for " + multicastSocketAddress);
        multicastThread.setDaemon(true);
        multicastThread.start();

        LOG.config("MulticastSocket configuration: local socket address: " + multicastSocket.getLocalSocketAddress() + " network interface: "
                + multicastSocket.getNetworkInterface() + " multicast address:" + multicastAddress + " timeToLive=" + multicastSocket.getTimeToLive());
        multicastSocket.joinGroup(multicastAddress);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void stop() throws IOException {
        if (!running) {
            return;
        }
        running = false;
        super.stop();
        if (multicastSocket != null) {
            try {
                multicastSocket.leaveGroup(multicastAddress);
            } catch (IOException e) {
            }
            multicastSocket.close();
        }
        printStats(Level.INFO);

        boolean finished = false;
        try {
            finished = endGate.await(shutdownTimeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
        }
        // interrupt thread that is waiting on a receive datagram.
        if (multicastThread != null) {
            multicastThread.interrupt();
        }
    }

    private void printStats(Level level) {
        if (threadPoolExecutor != null) {
            try {
                StringBuilder sb = new StringBuilder();
                sb.append("BlockingIOMulicastSender monitoring stats: ");
                sb.append("received: ").append(threadPoolExecutor.getCompletedTaskCount()).append(" core poolsize:")
                        .append(threadPoolExecutor.getCorePoolSize());
                sb.append(" largest pool size:").append(threadPoolExecutor.getLargestPoolSize()).append(" task count:")
                        .append(threadPoolExecutor.getTaskCount());
                sb.append(" max queue size:").append(maxExecutorQueueSize).append(" rejected execution:").append(rejectedExecution);
                monitorLog.log(level, sb.toString());
            } catch (Throwable t) {
            }
        }
    }

    public void run() {
        try {
            while (running) {
                byte[] buffer = new byte[multicastPacketSize];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                try {
                    multicastSocket.receive(packet);
                    if (!running) {
                        return;
                    }
                    Runnable processor = new MessageProcessTask(packet);
                    if (executor != null) {
                        executor.execute(processor);
                        if (threadPoolExecutor != null) {
                            int qsize = threadPoolExecutor.getQueue().size();
                            if (qsize > maxExecutorQueueSize) {
                                maxExecutorQueueSize = qsize;
                            }
//                            if (monitoringEnabled && ((threadPoolExecutor.getCompletedTaskCount()) % 2560) == 0) {
//                                printStats(Level.FINER);
//                            }
                        }
                    } else {
                        processor.run();
                    }
                } catch (InterruptedIOException iie) {
                    Thread.interrupted();
                } catch (IOException e) {
                    if (!running) {
                        break;
                    }
                    LOG.log(Level.SEVERE, "blockingiomcast.mcastreceivefailure", new Object[] { e.getLocalizedMessage() });
                    break;
                } catch (Throwable t) {
                    LOG.log(Level.SEVERE, "blockingiomcast.mcastreceivefailure", new Object[] { t.getLocalizedMessage() });
                    if (t instanceof RejectedExecutionException) {
                        rejectedExecution++;
                    }
                }
            }
        } catch (Throwable t) {
            LOG.log(Level.SEVERE, "blockingiomcast.receiveprocess.uncaughtthrowable",
                    new Object[] { t.getLocalizedMessage(), Thread.currentThread().getName() });
            LOG.log(Level.SEVERE, "stack trace", t);
        } finally {
            multicastThread = null;
            endGate.countDown();
            LOG.log(Level.INFO, "mgmt.blockingiomulticast.threadcomplete", new Object[] { Thread.currentThread().getName() });
        }
    }

    /**
     * {@inheritDoc}
     */
    protected boolean doBroadcast(final Message message) throws IOException {
        if (!running || multicastSocket == null) {
            throw new IOException("multicast server is not running");
        }
        if (message == null) {
            throw new IOException("message is null");
        }
        byte[] messageBytes = message.getPlainBytes();
        int numBytesInPacket = messageBytes.length;
        if ((numBytesInPacket > multicastPacketSize)) {
            LOG.log(Level.WARNING, "blockingiomcast.exceedsmaxsize", new Object[] { numBytesInPacket, multicastPacketSize });
        }
        DatagramPacket packet;
        if (localSocketAddress != null) {
            if (multicastSocketAddress == null) {
                throw new IOException("multicast address can not be null");
            }
            packet = new DatagramPacket(messageBytes, numBytesInPacket, multicastSocketAddress);
        } else {
            if (multicastAddress == null) {
                throw new IOException("multicast address can not be null");
            }
            packet = new DatagramPacket(messageBytes, numBytesInPacket, multicastAddress, multicastPort);
        }

        if (multicastSocket != null) {
            multicastSocket.send(packet);
            return true;
        } else {
            return false;
        }
    }

    private class MessageProcessTask implements Runnable {

        private final DatagramPacket packet;

        public MessageProcessTask(DatagramPacket packet) {
            this.packet = packet;
        }

        public void run() {
            Message message = null;
            if (packet == null) {
                return;
            }
            try {
                message = new MessageImpl();
                byte[] byteMessage = packet.getData();
                try {
                    int messageLen = message.parseHeader(byteMessage, 0);
                    message.parseMessage(byteMessage, MessageImpl.HEADER_LENGTH, messageLen);
                } catch (IllegalArgumentException iae) {
                    if (LOG.isLoggable(Level.WARNING)) {
                        LOG.log(Level.WARNING, "blockingiomcast.damaged", iae);
                    }
                    return;
                } catch (MessageIOException mie) {
                    if (LOG.isLoggable(Level.WARNING)) {
                        LOG.log(Level.WARNING, "blockingiomcast.damaged", mie);
                    }
                    return;
                }
                if (networkManager != null) {
                    networkManager.receiveMessage(message, null);
                    if (message.getType() != MessageImpl.TYPE_HEALTH_MONITOR_MESSAGE) {
                        if (mcastLog.isLoggable(Level.FINER)) {
                            mcastLog.log(Level.FINER, "BlockingIOMulticastSender.receiveMessage processed multicast message " + message.toString());
                        }
                    }
                }
            } catch (Throwable t) {
                if (LOG.isLoggable(Level.WARNING)) {
                    String msgOutput = "";
                    try {
                        if (message != null) {
                            msgOutput = message.toString();
                        }
                    } catch (Throwable tt) {
                        LOG.log(Level.WARNING, "blockingiomcast.failprocessing", new Object[] { msgOutput });
                        LOG.log(Level.WARNING, "stack trace", tt);
                    }
                }
            }
        }
    }
}
