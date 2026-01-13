/*
 * Copyright (c) 2011, 2018 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.shoal.gms.mgmt.transport.grizzly.grizzly2;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.SocketConnectorHandler;
import org.glassfish.grizzly.utils.Exceptions;

/**
 * Connection cache implementation.
 *
 * @author Alexey Stashok
 */
public class ConnectionCache {
    private final SocketConnectorHandler socketConnectorHandler;

    private final int highWaterMark;
    private final int maxParallelConnections;
    private final int numberToReclaim;

    private final AtomicBoolean isClosed = new AtomicBoolean();

    private final AtomicInteger totalCachedConnectionsCount = new AtomicInteger();

    private final ConcurrentHashMap<SocketAddress, CacheRecord> cache = new ConcurrentHashMap<SocketAddress, CacheRecord>();

    // Connect timeout 5 seconds
    static private final long connectTimeoutMillis = 5000;

    private final Connection.CloseListener removeCachedConnectionOnCloseListener = new RemoveCachedConnectionOnCloseListener();

    public ConnectionCache(SocketConnectorHandler socketConnectorHandler, int highWaterMark, int maxParallelConnections, int numberToReclaim) {
        this.socketConnectorHandler = socketConnectorHandler;

        this.highWaterMark = highWaterMark;
        this.maxParallelConnections = maxParallelConnections;
        this.numberToReclaim = numberToReclaim;
    }

    public Connection poll(final SocketAddress localAddress, final SocketAddress remoteAddress) throws IOException {

        final CacheRecord cacheRecord = obtainCacheRecord(remoteAddress);

        if (isClosed.get()) {
            // remove cache entry associated with the remoteAddress (only if we have the actual value)
            cache.remove(remoteAddress, cacheRecord);
            closeCacheRecord(cacheRecord);
            throw new IOException("ConnectionCache is closed");
        }

        // take connection from cache
        Connection connection = cacheRecord.connections.poll();
        if (connection != null) {
            // if we have one - just return it
            connection.removeCloseListener(removeCachedConnectionOnCloseListener);
            cacheRecord.idleConnectionsCount.decrementAndGet();
            return connection;
        }

        final Future<Connection> connectFuture = socketConnectorHandler.connect(remoteAddress, localAddress);

        try {
            connection = connectFuture.get(connectTimeoutMillis, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            throw Exceptions.makeIOException(e);
        }

        return connection;
    }

    public void offer(final Connection connection) {
        final SocketAddress remoteAddress = (SocketAddress) connection.getPeerAddress();

        final CacheRecord cacheRecord = obtainCacheRecord(remoteAddress);

        final int totalConnectionsN = totalCachedConnectionsCount.incrementAndGet();
        final int parallelConnectionN = cacheRecord.idleConnectionsCount.incrementAndGet();

        if (totalConnectionsN > highWaterMark || parallelConnectionN > maxParallelConnections) {
            totalCachedConnectionsCount.decrementAndGet();
            cacheRecord.idleConnectionsCount.decrementAndGet();
        }

        connection.addCloseListener(removeCachedConnectionOnCloseListener);

        cacheRecord.connections.offer(connection);

        if (isClosed.get()) {
            // remove cache entry associated with the remoteAddress (only if we have the actual value)
            cache.remove(remoteAddress, cacheRecord);
            closeCacheRecord(cacheRecord);
        }
    }

    public void close() {
        if (!isClosed.getAndSet(true)) {
            for (SocketAddress key : cache.keySet()) {
                final CacheRecord cacheRecord = cache.remove(key);
                closeCacheRecord(cacheRecord);
            }
        }
    }

    private void closeCacheRecord(final CacheRecord cacheRecord) {
        if (cacheRecord == null) {
            return;
        }
        Connection connection;
        while ((connection = cacheRecord.connections.poll()) != null) {
            cacheRecord.idleConnectionsCount.decrementAndGet();
            connection.close();
        }
    }

    private CacheRecord obtainCacheRecord(final SocketAddress remoteAddress) {
        CacheRecord cacheRecord = cache.get(remoteAddress);
        if (cacheRecord == null) {
            // make sure we added CacheRecord corresponding to the remoteAddress
            final CacheRecord newCacheRecord = new CacheRecord();
            cacheRecord = cache.putIfAbsent(remoteAddress, newCacheRecord);
            if (cacheRecord == null) {
                cacheRecord = newCacheRecord;
            }
        }

        return cacheRecord;
    }

    private static final class CacheRecord {
        final AtomicInteger idleConnectionsCount = new AtomicInteger();

        final Queue<Connection> connections = new LinkedTransferQueue<Connection>();

    }

    private final class RemoveCachedConnectionOnCloseListener implements Connection.CloseListener {

        @Override
        public void onClosed(Connection connection, Connection.CloseType type) throws IOException {
            final SocketAddress remoteAddress = (SocketAddress) connection.getPeerAddress();
            final CacheRecord cacheRecord = cache.get(remoteAddress);
            if (cacheRecord != null && cacheRecord.connections.remove(connection)) {
                cacheRecord.idleConnectionsCount.decrementAndGet();
            }
        }

    }
}
