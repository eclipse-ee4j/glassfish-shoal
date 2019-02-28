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

package com.sun.enterprise.mgmt;

import java.io.IOException;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.enterprise.ee.cms.impl.base.PeerID;
import com.sun.enterprise.ee.cms.logging.GMSLogDomain;
import com.sun.enterprise.mgmt.transport.Message;
import com.sun.enterprise.mgmt.transport.MulticastMessageSender;

public class ReliableMulticast {
    private static final Logger logger = GMSLogDomain.getMcastLogger();
    private static final Logger monitorLogger = GMSLogDomain.getMonitorLogger();

    private final long DEFAULT_EXPIRE_DURATION_MS;
    private final long DEFAULT_EXPIRE_REAPING_FREQUENCY;

    private MulticastMessageSender sender = null;
    private final Timer time;
    private final ConcurrentHashMap<Long, ReliableBroadcast> sendHistory = new ConcurrentHashMap<Long, ReliableBroadcast>();
    private ClusterManager manager = null;

    private static class ReliableBroadcast {
        final private Message msg;
        final private long startTime;
        final private long expirationTime_ms;
        private int resends;

        public ReliableBroadcast(Message msg, long expireDuration_ms) {
            this.msg = msg;
            this.startTime = System.currentTimeMillis();
            this.expirationTime_ms = startTime + expireDuration_ms;
            this.resends = 0;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expirationTime_ms;
        }
    }

    // added for junit testing verification of expiration.
    public int sendHistorySize() {
        return sendHistory.size();
    }

    void add(Message msg, long expireDuration_ms) {
        long seqId = MasterNode.getMasterViewSequenceID(msg);
        if (seqId != -1) {
            ReliableBroadcast rb = new ReliableBroadcast(msg, expireDuration_ms);
            sendHistory.put(seqId, rb);
            if (logger.isLoggable(Level.FINER)) {
                logger.finer("ReliableBroadcast.add msg[" + clusterViewEventMsgToString(msg) + "]");
            }
        }
    }

    static private String clusterViewEventMsgToString(Message msg) {
        StringBuffer sb = new StringBuffer(40);
        try {
            long seqId = MasterNode.getMasterViewSequenceID(msg);
            Object element = msg.getMessageElement(MasterNode.VIEW_CHANGE_EVENT);
            ClusterViewEvents type = null;
            String cveType = null;
            String memberName = null;
            PeerID peerId = null;
            if (element != null && element instanceof ClusterViewEvent) {
                ClusterViewEvent cve = (ClusterViewEvent) element;
                type = cve.getEvent();
                memberName = cve.getAdvertisement().getName();
                peerId = cve.getAdvertisement().getID();
                cveType = type.toString();
                sb.append("broadcast seq id:").append(seqId).append(" viewChangeEvent:").append(cveType).append(" member:").append(memberName)
                        .append(" peerId:" + peerId);
            }
        } catch (Error e) {
            e.printStackTrace();
        }
        return sb.toString();
    }

    public void processExpired() {
        int numExpired = 0;
        Set<ConcurrentHashMap.Entry<Long, ReliableBroadcast>> entrySet = sendHistory.entrySet();
        for (ConcurrentHashMap.Entry<Long, ReliableBroadcast> entry : entrySet) {
            ReliableBroadcast rb = entry.getValue();
            if (rb.isExpired()) {
                numExpired++;
                entrySet.remove(entry);
                if (rb.resends > 0) {
                    if (logger.isLoggable(Level.FINER)) {
                        logger.log(Level.FINER, "expire resent msg with masterViewSeqID=" + entry.getKey() + " resent:" + rb.resends);
                    }
                }
            }
        }

        if (numExpired > 0 && logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "processExpired: expired " + numExpired + " masterViewSeqID messages");
        }
    }

    // TODO: possible optimization: consider only resending certain ClusterViewEvents.
    // given the late arrival of the event, its view will almost always be stale. especially add_events.
    public boolean resend(PeerID to, Long seqId) throws IOException {
        boolean result = false;
        ReliableBroadcast rb = sendHistory.get(seqId);
        if (rb != null) {
            Message msg = rb.msg;
            msg.addMessageElement("RESEND", Boolean.TRUE);
            result = manager.getNetworkManager().send(to, rb.msg);
            rb.resends++;
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "mgmt.reliable.mcast.resend",
                        new Object[] { seqId, to.getInstanceName(), to.getGroupName(), rb.resends, clusterViewEventMsgToString(msg) });
            }
        } else if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "mgmt.reliable.mcast.resend.failed", new Object[] { seqId, to.getInstanceName(), to.getGroupName() });
        }
        return result;
    }

    public ReliableMulticast(ClusterManager manager) {
        DEFAULT_EXPIRE_DURATION_MS = 12 * 1000; // 12 seconds.
        DEFAULT_EXPIRE_REAPING_FREQUENCY = DEFAULT_EXPIRE_DURATION_MS + (DEFAULT_EXPIRE_DURATION_MS / 2);
        this.manager = manager;
        this.sender = manager.getNetworkManager().getMulticastMessageSender();
        TimerTask reaper = new Reaper(this);
        time = new Timer();
        time.schedule(reaper, DEFAULT_EXPIRE_REAPING_FREQUENCY, DEFAULT_EXPIRE_REAPING_FREQUENCY);
    }

    // junit testing.
    public ReliableMulticast(long expire_duration_ms) {
        DEFAULT_EXPIRE_DURATION_MS = expire_duration_ms;
        DEFAULT_EXPIRE_REAPING_FREQUENCY = DEFAULT_EXPIRE_DURATION_MS + (DEFAULT_EXPIRE_DURATION_MS / 2);
        TimerTask reaper = new Reaper(this);
        time = new Timer();
        time.schedule(reaper, DEFAULT_EXPIRE_REAPING_FREQUENCY, DEFAULT_EXPIRE_REAPING_FREQUENCY);
    }

    public void stop() {
        time.cancel();
    }

    public boolean broadcast(Message msg) throws IOException {
        boolean result = false;

        if (sender == null) {
            throw new IOException("multicast sender is null");
        }
        result = sender.broadcast(msg);
        if (result) {
            add(msg, DEFAULT_EXPIRE_DURATION_MS);
        }
        return result;
    }

    static class Reaper extends TimerTask {
        final private ReliableMulticast rb;

        public Reaper(ReliableMulticast rb) {
            this.rb = rb;
        }

        public void run() {
            rb.processExpired();
        }
    }
}
