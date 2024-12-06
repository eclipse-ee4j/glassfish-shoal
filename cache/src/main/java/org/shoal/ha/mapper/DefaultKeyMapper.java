/*
 * Copyright (c) 1997, 2018 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024 Contributors to the Eclipse Foundation. All rights reserved.
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

package org.shoal.ha.mapper;

import java.util.Collection;
import java.util.TreeSet;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.ha.store.api.HashableKey;
import org.shoal.ha.cache.api.ShoalCacheLoggerConstants;
import org.shoal.ha.group.GroupMemberEventListener;

/**
 * @author Mahesh Kannan
 */
public class DefaultKeyMapper implements KeyMapper, GroupMemberEventListener {

    Logger _logger = Logger.getLogger(ShoalCacheLoggerConstants.CACHE_KEY_MAPPER);

    private String myName;

    private ReentrantReadWriteLock.ReadLock rLock;

    private ReentrantReadWriteLock.WriteLock wLock;

    private volatile String[] members = new String[0];

    private volatile String[] previuousAliveAndReadyMembers = new String[0];

    private volatile String[] replicaChoices = new String[0];

    private static final String _EMPTY_REPLICAS = "";

    /**
     * @deprecated use {@link #DefaultKeyMapper(String)} instead
     */
    @Deprecated(forRemoval = true)
    public DefaultKeyMapper(String myName, String groupName) {
        this(myName);
    }

    public DefaultKeyMapper(String myName) {
        this.myName = myName;
        ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();

        rLock = rwLock.readLock();
        wLock = rwLock.writeLock();

        _logger.log(Level.FINE, "DefaultKeyMapper created for: myName: " + myName);
    }

    protected ReentrantReadWriteLock.ReadLock getReadLock() {
        return rLock;
    }

    protected ReentrantReadWriteLock.WriteLock getWriteLock() {
        return wLock;
    }

    protected String[] getMembers() {
        return members;
    }

    @Override
    public String getMappedInstance(String groupName, Object key1) {
        int hc = key1.hashCode();
        if (key1 instanceof HashableKey) {
            HashableKey k = (HashableKey) key1;
            hc = k.getHashKey() == null ? hc : k.getHashKey().hashCode();
            if (_logger.isLoggable(Level.FINE)) {
                _logger.log(Level.FINE,
                        "DefaultKeyMapper.getMappedInstance got a HashableKey " + " key = " + key1 + "; key.hc = " + key1.hashCode() + "; key.getHashKey() = "
                                + ((HashableKey) key1).getHashKey() + "; key.getHashKey().hc = " + (k.getHashKey() == null ? "-" : hc) + "; Final hc = " + hc);
            }
        }

        try {
            rLock.lock();
            return members.length == 0 ? null : members[Math.abs(hc % members.length)];
        } finally {
            rLock.unlock();
        }
    }

    @Override
    public String getReplicaChoices(String groupName, Object key) {
        int hc = getHashCodeForKey(key);
        try {
            rLock.lock();
            return members.length == 0 ? _EMPTY_REPLICAS : replicaChoices[Math.abs(hc % members.length)];
        } finally {
            rLock.unlock();
        }
    }

    @Override
    public String[] getCurrentMembers() {
        return members;
    }
    /*
     * @Override public String[] getKeyMappingInfo(String groupName, Object key1) { int hc = key1.hashCode(); if (key1
     * instanceof HashableKey) { HashableKey k = (HashableKey) key1; hc = k.getHashKey() == null ? hc :
     * k.getHashKey().hashCode(); } hc = Math.abs(hc);
     *
     * try { rLock.lock(); return getKeyMappingInfo(members, hc); } finally { rLock.unlock(); } }
     *
     * protected String[] getKeyMappingInfo(String[] instances, int hc) { if (members.length == 0) { return _EMPTY_TARGETS;
     * } else if (members.length == 1) { return new String[] {members[0], null}; } else { int index = hc % members.length;
     * return new String[] {members[index], members[(index + 1) % members.length]}; } }
     */

    @Override
    public String[] findReplicaInstance(String groupName, Object key1, String keyMappingInfo) {
        if (keyMappingInfo != null) {
            return keyMappingInfo.split(":");
        } else {

            int hc = key1.hashCode();
            if (key1 instanceof HashableKey) {
                HashableKey k = (HashableKey) key1;
                hc = k.getHashKey() == null ? hc : k.getHashKey().hashCode();
            }

            try {
                rLock.lock();
                return previuousAliveAndReadyMembers.length == 0 ? new String[] { _EMPTY_REPLICAS }
                        : new String[] { previuousAliveAndReadyMembers[Math.abs(hc % previuousAliveAndReadyMembers.length)] };
            } finally {
                rLock.unlock();
            }
        }
    }

    @Override
    public void onViewChange(String memberName, Collection<String> readOnlyCurrentAliveAndReadyMembers, Collection<String> readOnlyPreviousAliveAndReadyMembers,
            boolean isJoinEvent) {
        try {
            wLock.lock();

            TreeSet<String> currentMemberSet = new TreeSet<String>();
            currentMemberSet.addAll(readOnlyCurrentAliveAndReadyMembers);
            currentMemberSet.remove(myName);
            members = currentMemberSet.toArray(new String[0]);

            int memSz = members.length;
            this.replicaChoices = new String[memSz];

            if (memSz == 0) {
                this.replicaChoices = new String[] { _EMPTY_REPLICAS };
            } else {
                for (int i = 0; i < memSz; i++) {
                    StringBuilder sb = new StringBuilder();
                    int index = i;
                    String delim = "";
                    int choiceLimit = 1;
                    for (int j = 0; j < memSz && choiceLimit-- > 0; j++) {
                        sb.append(delim).append(members[index++ % memSz]);
                        delim = ":";
                    }

                    replicaChoices[i] = sb.toString();
                }
            }

            TreeSet<String> previousView = new TreeSet<String>();
            previousView.addAll(readOnlyPreviousAliveAndReadyMembers);
            if (!isJoinEvent) {
                previousView.remove(memberName);
            }
            previuousAliveAndReadyMembers = previousView.toArray(new String[0]);

            if (_logger.isLoggable(Level.FINE)) {
                printMemberStates("onViewChange (isJoin: " + isJoinEvent + ")");
            }
        } finally {
            wLock.unlock();
        }
    }

    private int getHashCodeForKey(Object key1) {
        int hc = key1.hashCode();
        if (key1 instanceof HashableKey) {
            HashableKey k = (HashableKey) key1;
            hc = k.getHashKey() == null ? hc : k.getHashKey().hashCode();
        }

        return hc;
    }

    public void printMemberStates(String message) {
        StringBuilder sb = new StringBuilder("DefaultKeyMapper[" + myName + "]." + message + " currentView: ");
        String delim = "";
        for (String st : members) {
            sb.append(delim).append(st);
            delim = " : ";
        }
        sb.append("; previousView ");

        delim = "";
        for (String st : previuousAliveAndReadyMembers) {
            sb.append(delim).append(st);
            delim = " : ";
        }

        sb.append("\n");
        int memSz = members.length;
        for (int i = 0; i < memSz; i++) {
            sb.append("\tReplicaChoices[").append(members[i]).append("]: ").append(replicaChoices[i]);
            sb.append("\n");
        }
        _logger.log(Level.FINE, sb.toString());
    }

}
