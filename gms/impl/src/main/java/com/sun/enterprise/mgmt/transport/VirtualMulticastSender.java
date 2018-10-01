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

package com.sun.enterprise.mgmt.transport;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.enterprise.ee.cms.impl.base.PeerID;
import com.sun.enterprise.ee.cms.logging.GMSLogDomain;

/**
 * This class extends {@link BlockingIOMulticastSender} for supporting the circumstance that cluster members are located
 * beyond one subnet or multicast traffic is disabled
 *
 * <code>virtualPeerIdList</code> should contain <code>PeerID</code>s of cluster members which are located beyond one
 * subnet. So, this {@link MulticastMessageSender} will broadcast a message to endpoints which
 * <code>virtualPeerIdList</code> includes as well as one subnet on TCP protocol.
 *
 * @author Bongjae Chang
 */
public class VirtualMulticastSender extends AbstractMulticastMessageSender {

    // private static final Logger LOG = GMSLogDomain.getLogger( GMSLogDomain.GMS_LOGGER );
    private static final Logger LOG = GMSLogDomain.getNoMCastLogger();

    final Set<PeerID> virtualPeerIdList = new CopyOnWriteArraySet<PeerID>();
    final NetworkManager networkManager;
    final Map<PeerID, Long> lastReportedSendFailure = new ConcurrentHashMap<PeerID, Long>();
    static final long LAST_REPORTED_FAILURE_DURATION_MS = 10000; // 10 seconds between reporting failed send.
    final long DISCOVERY_PERIOD_COMPLETED_TIME;
    boolean discoveryCleanupPending = true;

    public VirtualMulticastSender(NetworkManager networkManager, List<PeerID> initialPeerIds) throws IOException {
        this.networkManager = networkManager;
        if (initialPeerIds != null && !initialPeerIds.isEmpty()) {
            this.virtualPeerIdList.addAll(initialPeerIds);
        }
        DISCOVERY_PERIOD_COMPLETED_TIME = System.currentTimeMillis() + 10000;
    }

    public Set<PeerID> getVirtualPeerIDSet() {
        return virtualPeerIdList;
    }

    @Override
    public synchronized void start() throws IOException {
        // ADDED EXPLICITLY SO SUPERCLASS start() is not called to start multicast listener.
        // we are not supporting hybrid solution of some multicast and some non-multicast at this time,
        // it is all multicast or all virtual multicast.

    }

    @Override
    public synchronized void stop() throws IOException {
        // did not call super.start(), no need to call super.stop().
        // super.stop();
        virtualPeerIdList.clear();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean doBroadcast(final Message message) throws IOException {
        if (LOG.isLoggable(Level.FINER)) {
            LOG.entering(this.getClass().getSimpleName(), "doBroadcast", new Object[] { message });
            LOG.finer("VirtualMulticastSender.doBroadcast() virtualPeerIdList = " + virtualPeerIdList);
        }
        boolean result = true;

        // TODO: Removed combining multicast with TCP virtual multicast.
//        if( !super.doBroadcast( message ) )
//            result = false;
        // send the message to virtual server on TCP
        MessageSender tcpSender = networkManager.getMessageSender(ShoalMessageSender.TCP_TRANSPORT);

        if (discoveryCleanupPending) {
            if (DISCOVERY_PERIOD_COMPLETED_TIME - System.currentTimeMillis() < 0) {
                discoveryCleanupPending = false;
                removeUnknownInstances();
            }
        }
        for (PeerID peerID : virtualPeerIdList) {
            try {
                if (LOG.isLoggable(Level.FINEST)) {
                    LOG.log(Level.FINEST, "VirtualMulticastSender.doBroadcast prepare to send msg to peerID " + peerID);
                }
                if (!tcpSender.send(peerID, message)) {
                    if (LOG.isLoggable(Level.FINE)) {
                        LOG.log(Level.FINE, "VirtualMulticastSender.doBroadcast failed to send msg to peerID " + peerID);
                    }
                    result = false;
                } else {
                    if (LOG.isLoggable(Level.FINEST)) {
                        LOG.log(Level.FINEST, "VirtualMulticastSender.doBroadcast succeded to send msg to peerID " + peerID);
                    }
                }

            } catch (IOException ie) {
                Long lastFail = lastReportedSendFailure.get(peerID);
                long currentTime = System.currentTimeMillis();
                if (lastFail == null || ((lastFail - currentTime) > LAST_REPORTED_FAILURE_DURATION_MS)) {
                    if (LOG.isLoggable(Level.FINE)) {
                        LOG.log(Level.FINE, "failed to send message to a virtual multicast endpoint[" + peerID + "] message=[" + message + "]", ie);
                    }
                    lastReportedSendFailure.put(peerID, currentTime);
                }
                purge();
            }
        }
        return result;
    }

    /**
     * Remove all PeerID entries added via DISCOVERY_URI_LIST. These entries have "Unknown_" at start for instance name.
     *
     * If there are not removed, then we send to those DISCOVERY_URI_LIST instances multiple times.
     */
    public void removeUnknownInstances() {
        boolean removed = false;
        LinkedList<PeerID> unknownList = new LinkedList<PeerID>();
        Iterator<PeerID> iter = virtualPeerIdList.iterator();
        while (iter.hasNext()) {
            PeerID id = iter.next();
            if (id.getInstanceName().startsWith("Unknown_")) {
                removed = true;
                unknownList.add(id);
            }
        }
        if (removed) {
            virtualPeerIdList.removeAll(unknownList);
        }
        if (removed && LOG.isLoggable(Level.FINE)) {
            LOG.fine("Removed the following DISCOVERY seeded unknown instance names from virtualPeerIDList" + unknownList + " virtualPeerIDset="
                    + virtualPeerIdList);
        }
    }

    private void purge() {
        Iterator<Map.Entry<PeerID, Long>> iter = lastReportedSendFailure.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<PeerID, Long> e = iter.next();
            if (e.getValue() - System.currentTimeMillis() > LAST_REPORTED_FAILURE_DURATION_MS) {
                iter.remove();
            }
        }
    }
}
