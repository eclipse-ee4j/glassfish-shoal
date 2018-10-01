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

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.enterprise.ee.cms.core.GMSMember;
import com.sun.enterprise.ee.cms.core.RejoinSubevent;
import com.sun.enterprise.ee.cms.impl.base.CustomTagNames;
import com.sun.enterprise.ee.cms.impl.base.PeerID;
import com.sun.enterprise.ee.cms.impl.base.SystemAdvertisement;
import com.sun.enterprise.ee.cms.impl.base.Utility;
import com.sun.enterprise.ee.cms.impl.client.RejoinSubeventImpl;
import com.sun.enterprise.ee.cms.impl.common.GMSContext;
import com.sun.enterprise.ee.cms.impl.common.GMSContextFactory;
import com.sun.enterprise.ee.cms.logging.GMSLogDomain;

/**
 * Manages Cluster Views and notifies cluster view listeners when cluster view changes
 */
public class ClusterViewManager {
    private static final Logger LOG = GMSLogDomain.getLogger(GMSLogDomain.GMS_LOGGER);
    private TreeMap<PeerID, SystemAdvertisement> view = new TreeMap<PeerID, SystemAdvertisement>();
    private SystemAdvertisement advertisement = null;
    private SystemAdvertisement masterAdvertisement = null;
    private List<ClusterViewEventListener> cvListeners = new ArrayList<ClusterViewEventListener>();
    private long viewId = 0;
    private AtomicLong masterViewID = new AtomicLong(0);
    private ClusterManager manager;
    private ReentrantLock viewLock = new ReentrantLock();
    private GMSContext gmsCtxt = null;

    /**
     * Constructor for the ClusterViewManager object
     *
     * @param advertisement the advertisement of the instance associated with this object
     * @param manager the cluster manager
     * @param listeners <code>List</code> of <code>ClusterViewEventListener</code>
     */
    public ClusterViewManager(final SystemAdvertisement advertisement, final ClusterManager manager, final List<ClusterViewEventListener> listeners) {
        this.advertisement = advertisement;
        this.manager = manager;
        cvListeners.addAll(listeners);
        gmsCtxt = GMSContextFactory.getGMSContext(manager.getGroupName());
    }

    public void start() {
        // Self appointed as master and then discover so that we resolve to the right one
        setMaster(advertisement, true);
    }

    public void addClusterViewEventListener(final ClusterViewEventListener listener) {
        cvListeners.add(listener);
    }

    public void removeClusterViewEventListener(final ClusterViewEventListener listener) {
        cvListeners.remove(listener);
    }

    /**
     * adds a system advertisement
     *
     * @param advertisement system advertisement to add
     */
    boolean add(final SystemAdvertisement advertisement) {
        boolean result = false;
        lockLog("add()");
        viewLock.lock();
        try {
            if (!view.containsKey(advertisement.getID())) {
                if (LOG.isLoggable(Level.FINER)) {
                    LOG.log(Level.FINER, new StringBuffer().append("Adding ").append(advertisement.getName()).append("   ")
                            .append(advertisement.getID().toString()).toString());
                }
                manager.getNetworkManager().addRemotePeer(advertisement.getID());
                view.put(advertisement.getID(), advertisement);
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.log(Level.FINE, "add " + advertisement.getName() + " newViewSize=" + view.size());
                }
                result = true;
                if (LOG.isLoggable(Level.FINER)) {
                    LOG.log(Level.FINER, MessageFormat.format("Cluster view now contains {0} entries", getViewSize()));
                }
            } else {
                // if view does contain the same sys adv but the start time is different from what
                // was already in the view
                // then add the new sys adv
                SystemAdvertisement existingAdv = view.get(advertisement.getID());
                if (manager.getMasterNode().confirmInstanceHasRestarted(existingAdv, advertisement)) {
                    if (LOG.isLoggable(Level.FINE)) {
                        LOG.fine("ClusterViewManager .add() : Instance " + advertisement.getName() + " has restarted. Adding it to the view.");
                    }
                    // reminder: peerID can be same but have different tcpport.
                    // so this is important to remove old and add the new one.
                    manager.getNetworkManager().removePeerID(existingAdv.getID());
                    manager.getNetworkManager().addRemotePeer(advertisement.getID());
                    view.put(advertisement.getID(), advertisement);

                    // this can't return NO_SUCH_TIME -- we wouldn't have
                    // entered this 'if' block
                    RejoinSubeventImpl rsi = new RejoinSubeventImpl(Utility.getStartTime(existingAdv));
                    RejoinSubevent previous = gmsCtxt.getInstanceRejoins().put(existingAdv.getName(), rsi);
                    if (previous != null && LOG.isLoggable(Level.INFO)) {
                        // todo: test this
                        String[] params = { existingAdv.getName(), previous.toString() };
                        LOG.log(Level.INFO, "rejoin.subevent.replaced", params);
                    }
                    if (LOG.isLoggable(Level.FINE)) {
                        LOG.fine(String.format("Added rejoin subevent for '%s' to context map", existingAdv.getName()));
                    }
                    result = true;
                }
            }
        } finally {
            viewLock.unlock();
        }
        if (result) {
            boolean added = manager.getHealthMonitor().addHealthEntryIfMissing(advertisement);
            if (added) {
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.log(Level.FINE, "ensured ClusterViewManager and HealthMonitor both aware of member: " + advertisement.getName());
                }
            }
        }
        return result;
    }

    /**
     * Set the master instance
     *
     * @param advertisement Master system adverisement
     * @param notify if true, notifies registered listeners
     * @return true if there is master's change, false otherwise
     */
    boolean setMaster(final SystemAdvertisement advertisement, boolean notify) {
        if (advertisement.equals(masterAdvertisement)) {
            return false;
        } else {
            masterAdvertisement = advertisement;
            lockLog("setMaster()");
            viewLock.lock();
            try {
                view.put(masterAdvertisement.getID(), masterAdvertisement);
            } finally {
                viewLock.unlock();
            }
            if (notify) {
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.log(Level.FINE, "setMaster master:" + advertisement.getName());
                }
                notifyListeners(new ClusterViewEvent(ClusterViewEvents.MASTER_CHANGE_EVENT, advertisement));
            }
            if (LOG.isLoggable(Level.FINE)) {
                if (advertisement.getID().equals(this.advertisement.getID())) {
                    LOG.log(Level.FINE, "Setting MasterNode Role");
                } else {
                    LOG.log(Level.FINE, new StringBuffer().append("Setting Master Node :").append(advertisement.getName()).append(' ')
                            .append(advertisement.getID()).toString());
                }
            }
            return true;
        }
    }

    /**
     * Set the master instance with new view
     *
     * @param newView list of advertisements
     * @param advertisement Master system adverisement
     * @return true if there is master's change, false otherwise
     */
    boolean setMaster(final List<SystemAdvertisement> newView, final SystemAdvertisement advertisement) {
        if (advertisement.equals(masterAdvertisement)) {
            return false;
        }
        lockLog("setMaster()");
        viewLock.lock();
        try {
            if (newView != null) {
                addToView(newView);
            }
            setMaster(advertisement, true);
        } finally {
            viewLock.unlock();
        }
        return true;
    }

    /**
     * Gets the master advertisement
     *
     * @return SystemAdvertisement Master system adverisement
     */
    public SystemAdvertisement getMaster() {
        return masterAdvertisement;
    }

    /**
     * Retrieves a system advertisement from a the table
     *
     * @param id instance id
     * @return Returns the SystemAdvertisement associated with id
     */
    public SystemAdvertisement get(final PeerID id) {
        final SystemAdvertisement adv;
        lockLog("get()");
        viewLock.lock();
        try {
            adv = view.get(id);
        } finally {
            viewLock.unlock();
        }
        return adv;
    }

    /**
     * removes an entry from the table. This is only called when a failure occurs.
     *
     * @param advertisement Instance advertisement
     * @return SystemAdvertisement removed or null if not in view.
     */
    SystemAdvertisement remove(final SystemAdvertisement advertisement) {
        SystemAdvertisement removed = null;
        final PeerID id = advertisement.getID();
        lockLog("remove()");
        viewLock.lock();
        try {
            removed = view.remove(id);
        } finally {
            viewLock.unlock();
        }
        if (removed != null) {
            if (LOG.isLoggable(Level.FINE)) {
                LOG.log(Level.FINE, "Removed " + removed.getName() + "   " + id);
            }
            manager.getNetworkManager().removePeerID(id);
        } else {
            if (LOG.isLoggable(Level.FINE)) {
                LOG.log(Level.FINE, "Skipping removal of " + id + " Not in view");
            }
        }
        return removed;
    }

    public boolean containsKey(final PeerID id) {
        return containsKey(id, false);
    }

    public boolean containsKey(final PeerID id, boolean debug) {
        final boolean contains;
        viewLock.lock();
        try {
            contains = view.containsKey(id);
            if (debug && !contains) {
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.log(Level.FINE,
                            "ClusterViewManager.containsKey(peerid=" + id + ") is false.\ngroup: " + manager.getGroupName() + " view=" + dumpView());
                }
            }
        } finally {
            viewLock.unlock();
        }

        return contains;
    }

    /**
     * Resets the view
     */
    void reset() {
        viewLock.lock();
        try {
            view.clear();
            view.put(advertisement.getID(), advertisement);
        } finally {
            viewLock.unlock();
        }
    }

    /**
     * Returns a sorted localView
     *
     * @return The localView List
     */
    @SuppressWarnings("unchecked")
    public ClusterView getLocalView() {
        final TreeMap<PeerID, SystemAdvertisement> temp;
        long localViewId = 0;
        long masterViewId = 0;
        lockLog("getLocalView()");
        viewLock.lock();
        try {
            temp = (TreeMap<PeerID, SystemAdvertisement>) view.clone();
            localViewId = viewId++;
            masterViewId = getMasterViewID();
        } finally {
            viewLock.unlock();
        }
        if (LOG.isLoggable(Level.FINEST)) {
            LOG.log(Level.FINEST, "returning new ClusterView with view size:" + view.size());
        }
        return new ClusterView(temp, localViewId, masterViewId);
    }

    /**
     * Gets the viewSize attribute of the ClusterViewManager object
     *
     * @return The viewSize
     */
    public int getViewSize() {
        int size;
        lockLog("getViewSize()");
        viewLock.lock();
        try {
            size = view.size();
        } finally {
            viewLock.unlock();
        }
        return size;
    }

    /**
     * Returns the top node on the list
     *
     * @return the top node on the list
     */
    SystemAdvertisement getMasterCandidate() {
        SystemAdvertisement seniorMember = null;
        lockLog("getMasterCandidate()");
        viewLock.lock();
        try {
            /*
             * no longer take first instance in sorted order.
             */
            /*
             * final PeerID id = view.firstKey(); adv = view.get(id);
             */

            // make senior member the Master candidate.
            // adapting to scenarios where cluster membership is not static, but a dynamic evolving
            // group of members. (i.e. VM in Cloud complete and new member may replace in future, not a restart
            // of previous member.)
            long seniorMemberStartTime = Long.MAX_VALUE;
            for (Map.Entry<PeerID, SystemAdvertisement> e : view.entrySet()) {
                SystemAdvertisement i = e.getValue();
                if (seniorMember == null) {
                    seniorMember = i;
                    try {
                        seniorMemberStartTime = Long.parseLong(i.getCustomTagValue(CustomTagNames.START_TIME.toString()));
                    } catch (NoSuchFieldException ignore) {
                    }
                } else {
                    long iCurrentStartTime = Long.MAX_VALUE;
                    try {
                        iCurrentStartTime = Long.parseLong(i.getCustomTagValue(CustomTagNames.START_TIME.toString()));
                    } catch (NoSuchFieldException ignore) {
                    }
                    if (iCurrentStartTime < seniorMemberStartTime) {
                        seniorMember = i;
                        seniorMemberStartTime = iCurrentStartTime;
                    }
                }
            }

        } finally {
            viewLock.unlock();
        }

        if (seniorMember == null) {
            LOG.warning("getMasterCandidate: no master candidate selected, nominating self");
            seniorMember = manager.getSystemAdvertisement();
        }

        // TODO: change this log level to FINE before FINAL release.
        if (LOG.isLoggable(Level.INFO)) {
            LOG.log(Level.INFO, new StringBuffer().append("Returning Master Candidate Node :").append(seniorMember.getName()).append(' ')
                    .append(seniorMember.getID()).toString());
        }
        return seniorMember;
    }

    /**
     * Determines whether this node is the Master
     *
     * @return true if this node is the master node
     */
    public boolean isMaster() {
        return masterAdvertisement != null && masterAdvertisement.getID().equals(advertisement.getID());
    }

    /**
     * Determines whether this node is at the top of the list
     *
     * @return true if this node is a the top of the list, false otherwise
     */
    public boolean isFirst() {
        final PeerID id = view.firstKey();
        return advertisement.getID().equals(id);
    }

    /**
     * the index of id this view, or -1 if this view does not contain this element.
     *
     * @param id id
     * @return the index of id this view, or -1 if this view does not contain this element.
     */
    public int indexOf(final PeerID id) {
        if (id == null) {
            return -1;
        }
        int index = 0;
        PeerID key;
        for (PeerID peerID : view.keySet()) {
            key = peerID;
            if (key.equals(id)) {
                return index;
            }
            index++;
        }
        return -1;
    }

    public PeerID getID(final String name) {
        return manager.getID(name);
    }

    /**
     * Adds a list of advertisements to the view
     *
     * @param newView list of advertisements
     * @param cvEvent the cluster event
     * @param authoritative whether the view is authoritative or not
     */
    void addToView(final List<SystemAdvertisement> newView, final boolean authoritative, final ClusterViewEvent cvEvent) {
        // TODO: need to review the use cases of the callers of method
        if (cvEvent == null) {
            return;
        }

        if (authoritative) {
            ClusterViewEvents event = cvEvent.getEvent();
            boolean changed = addToView(newView);
            if (changed || event != ClusterViewEvents.ADD_EVENT) {
                // only if there are changes that we notify
                notifyListeners(cvEvent);
            } else {
                GMSMember member = Utility.getGMSMember(cvEvent.getAdvertisement());
                LOG.log(Level.INFO, "mgmt.clusterviewmanager.skipnotify", new Object[] { cvEvent.getEvent(), member.getMemberToken(), member.getGroupName() });
            }
        }
    }

    /**
     * Adds a list of advertisements to the view
     *
     * @param newView list of advertisements
     * @return true if there are changes, false otherwise
     */
    private boolean addToView(final List<SystemAdvertisement> newView) {
        boolean changed = false;
        lockLog("addToView() - reset and add newView");
        if (LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE, "addToView newViewSize=" + newView.size());
        }
        viewLock.lock();
        reset();
        try {
            if (!newView.contains(manager.getSystemAdvertisement())) {
                view.put(manager.getSystemAdvertisement().getID(), manager.getSystemAdvertisement());
            }
            for (SystemAdvertisement elem : newView) {
                if (LOG.isLoggable(Level.FINER)) {
                    LOG.log(Level.FINER, new StringBuffer().append("Adding ").append(elem.getID()).append(" to view").toString());
                }
                // verify that each member in new view was in old view; otherwise, set change to TRUE.
                if (!changed && !view.containsKey(elem.getID())) {
                    changed = true;
                }
                // Always add the wire version of the adv
                manager.getNetworkManager().addRemotePeer(elem.getID());
                view.put(elem.getID(), elem);
            }
        } finally {
            viewLock.unlock();
        }
        return changed;
    }

    void notifyListeners(final ClusterViewEvent event) {
        if (LOG.isLoggable(Level.FINER)) {
            LOG.log(Level.FINER, MessageFormat.format("Notifying the {0} to listeners, peer in event is {1}", event.getEvent().toString(),
                    event.getAdvertisement().getName()));
        }
        ClusterView cv = getLocalView();
        for (ClusterViewEventListener elem : cvListeners) {
            elem.clusterViewEvent(event, cv);
        }
    }

    public void setInDoubtPeerState(final SystemAdvertisement adv) {
        if (adv == null) {
            throw new IllegalArgumentException("SystemAdvertisment may not be null");
        }
        notifyListeners(new ClusterViewEvent(ClusterViewEvents.IN_DOUBT_EVENT, adv));
    }

    public void setPeerStoppingState(final SystemAdvertisement adv) {
        if (adv == null) {
            throw new IllegalArgumentException("SystemAdvertisment may not be null");
        }
        notifyListeners(new ClusterViewEvent(ClusterViewEvents.PEER_STOP_EVENT, adv));
    }

    public void setClusterStoppingState(final SystemAdvertisement adv) {
        if (adv == null) {
            throw new IllegalArgumentException("SystemAdvertisment may not be null");
        }
        notifyListeners(new ClusterViewEvent(ClusterViewEvents.CLUSTER_STOP_EVENT, adv));
    }

    public void setPeerNoLongerInDoubtState(final SystemAdvertisement adv) {
        if (adv == null) {
            throw new IllegalArgumentException("SystemAdvertisment may not be null");
        }
        notifyListeners(new ClusterViewEvent(ClusterViewEvents.NO_LONGER_INDOUBT_EVENT, adv));
    }

    public long getMasterViewID() {
        return masterViewID.get();
    }

    public void setMasterViewID(long masterViewID) {
        this.masterViewID.set(masterViewID);
    }

    private void lockLog(String method) {
        if (LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE,
                    MessageFormat.format("{0} viewLock Hold count :{1}, lock queue count:{2}", method, viewLock.getHoldCount(), viewLock.getQueueLength()));
        }
    }

    public void setPeerReadyState(SystemAdvertisement adv) {
        if (adv == null) {
            throw new IllegalArgumentException("SystemAdvertisment may not be null");
        }
        notifyListeners(new ClusterViewEvent(ClusterViewEvents.JOINED_AND_READY_EVENT, adv));

    }

    String dumpView() {
        StringBuffer sb = new StringBuffer();
        viewLock.lock();
        try {
            sb.append("clusterviewmanager snapshot: group:" + manager.getGroupName() + " current view id=" + this.viewId + " \n");
            int counter = 0;
            for (Map.Entry<PeerID, SystemAdvertisement> current : view.entrySet()) {
                PeerID peerid = current.getKey();
                SystemAdvertisement sa = current.getValue();
                sb.append(++counter).append(". ");
                sb.append(peerid.getInstanceName());
                sb.append(" ");
                sb.append(peerid.getUniqueID().toString());
                sb.append('\n');

            }
        } finally {
            viewLock.unlock();
        }
        return sb.toString();
    }
}
