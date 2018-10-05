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

package org.shoal.ha.cache.impl.store;

import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.shoal.ha.cache.api.DataStoreContext;
import org.shoal.ha.cache.api.DataStoreException;
import org.shoal.ha.cache.api.IdleEntryDetector;
import org.shoal.ha.cache.api.ShoalCacheLoggerConstants;

/**
 * @author Mahesh Kannan
 */
public class ReplicaStore<K, V> {

    private static final Logger _logger = Logger.getLogger(ShoalCacheLoggerConstants.CACHE_COMMAND);

    private DataStoreContext<K, V> ctx;

    private ConcurrentHashMap<K, DataStoreEntry<K, V>> map = new ConcurrentHashMap<K, DataStoreEntry<K, V>>();

    private AtomicInteger replicaEntries = new AtomicInteger(0);

    private IdleEntryDetector<K, V> idleEntryDetector;

    private AtomicBoolean expiredEntryRemovalInProgress = new AtomicBoolean(false);

    public ReplicaStore(DataStoreContext<K, V> ctx) {
        this.ctx = ctx;
    }

    public void setIdleEntryDetector(IdleEntryDetector<K, V> idleEntryDetector) {
        this.idleEntryDetector = idleEntryDetector;
    }

    // This is called during loadRequest. We do not want LoadRequests
    // to call getOrCreateEntry()
    public DataStoreEntry<K, V> getEntry(K k) {
        return map.get(k);

    }

    public DataStoreEntry<K, V> getOrCreateEntry(K k) {
        DataStoreEntry<K, V> entry = map.get(k);
        if (entry == null) {
            entry = new DataStoreEntry<K, V>();
            entry.setKey(k);
            DataStoreEntry<K, V> tEntry = map.putIfAbsent(k, entry);
            if (tEntry != null) {
                entry = tEntry;
            } else {
                replicaEntries.incrementAndGet();
            }
        }

        return entry;
    }

    public V getV(K k, ClassLoader cl) throws DataStoreException {

        V result = null;
        DataStoreEntry<K, V> entry = map.get(k);
        synchronized (entry) {
            result = ctx.getDataStoreEntryUpdater().getV(entry);
        }

        return result;

    }

    public void remove(K k) {
        DataStoreEntry<K, V> dse = map.remove(k);
        if (dse != null) {
            synchronized (dse) {
                dse.markAsRemoved("Removed");
            }

            replicaEntries.decrementAndGet();
        }
    }

    public int size() {
        return map.size();
    }

    public int removeExpired() {
        int result = 0;
        ctx.getDataStoreMBean().incrementRemoveExpiredCallCount();
        if (expiredEntryRemovalInProgress.compareAndSet(false, true)) {
            try {
                if (idleEntryDetector != null) {
                    long now = System.currentTimeMillis();
                    Iterator<DataStoreEntry<K, V>> iterator = map.values().iterator();
                    while (iterator.hasNext()) {
                        DataStoreEntry<K, V> entry = iterator.next();
                        synchronized (entry) {
                            if (idleEntryDetector.isIdle(entry, now)) {
                                entry.markAsRemoved("Idle");
                                _logger.log(Level.FINE, "ReplicaStore removing (idle) key: " + entry.getKey());
                                iterator.remove();
                                result++;
                            }
                        }
                    }
                } else {
                    // System.out.println("ReplicaStore.removeExpired idleEntryDetector is EMPTY");
                }
            } finally {
                expiredEntryRemovalInProgress.set(false);
            }

            ctx.getDataStoreMBean().incrementRemoveExpiredEntriesCount(result);
        } else {
            _logger.log(Level.FINEST, "ReplicaStore.removeExpired(). Skipping since there is already another thread running");
        }

        return result;
    }

    public Collection<K> keys() {
        return map.keySet();
    }

    public Collection<DataStoreEntry<K, V>> values() {
        return map.values();
    }

}
