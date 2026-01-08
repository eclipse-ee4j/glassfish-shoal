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

package org.shoal.ha.cache.store;

import java.util.Comparator;
import java.util.TreeSet;

import org.shoal.ha.cache.store.backing.commands.AbstractSaveCommand;

/**
 * @author Mahesh Kannan
 */
public class DataStoreEntry<K, V> {

    public static final long MIN_VERSION = -8;

    private K key;

    private V v;

    private String replicaInstanceName;

    private TreeSet<AbstractSaveCommand<K, V>> pendingUpdates;

    private boolean removed;

    private long lastAccessedAt;

    private long maxIdleTime;

    private long version = MIN_VERSION; // some negative number that is small enough to allow updates/saves to succeed

    private byte[] rawV;

    private boolean isReplicaNode = true;

    public DataStoreEntry() {

    }

    public void setKey(K key) {
        this.key = key;
    }

    public K getKey() {
        return key;
    }

    /* package */ V getV() {
        return v;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public void setV(V state) {
        this.v = state;
    }

    public byte[] getRawV() {
        return rawV;
    }

    public void setRawV(byte[] rawV) {
        this.rawV = rawV;
        this.v = null;
    }

    public String getReplicaInstanceName() {
        return replicaInstanceName;
    }

    public String setReplicaInstanceName(String replicaInstanceName) {
        String oldValue = this.replicaInstanceName;
        this.replicaInstanceName = replicaInstanceName;
        this.removed = false; // Because we just saved the data in a replica
        return oldValue == null ? null : oldValue.equals(replicaInstanceName) ? null : oldValue;
    }

    public TreeSet<AbstractSaveCommand<K, V>> getPendingUpdates() {
        return pendingUpdates;
    }

    public void clearPendingUpdates() {
        if (pendingUpdates != null) {
            pendingUpdates.clear();
        }
    }

    public void addPendingUpdate(AbstractSaveCommand<K, V> cmd) {
        if (pendingUpdates == null) {
            pendingUpdates = new TreeSet<AbstractSaveCommand<K, V>>(new Comparator<AbstractSaveCommand<K, V>>() {
                @Override
                public int compare(AbstractSaveCommand<K, V> cmd1, AbstractSaveCommand<K, V> cmd2) {
                    return (int) (cmd1.getVersion() - cmd2.getVersion());
                }
            });
        }
        this.pendingUpdates.add(cmd);
    }

    public boolean isRemoved() {
        return removed;
    }

    public void markAsRemoved(String reason) {
        this.removed = true;
        v = null;
        pendingUpdates = null;
    }

    public long getLastAccessedAt() {
        return lastAccessedAt;
    }

    public void setLastAccessedAt(long lastAccessedAt) {
        this.lastAccessedAt = lastAccessedAt;
    }

    public long getVersion() {
        return version;
    }

    public long incrementAndGetVersion() {
        return ++version;
    }

    public long getMaxIdleTime() {
        return maxIdleTime;
    }

    public void setMaxIdleTime(long maxIdleTime) {
        this.maxIdleTime = maxIdleTime;
    }

    public boolean isReplicaNode() {
        return isReplicaNode;
    }

    public void setIsReplicaNode(boolean replicaNode) {
        isReplicaNode = replicaNode;
    }
}
