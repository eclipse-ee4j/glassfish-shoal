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

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.text.MessageFormat;

import com.sun.enterprise.ee.cms.impl.base.SystemAdvertisement;
import com.sun.enterprise.ee.cms.impl.base.PeerID;
import com.sun.enterprise.ee.cms.logging.GMSLogDomain;

/**
 * ClusterView is a snapshot of the current membership of the group. The ClusterView is created anew each time a
 * membership change occurs in the group. ClusterView is managed by ClusterViewManager.
 */
public class ClusterView {
	private static final Logger LOG = GMSLogDomain.getLogger(GMSLogDomain.GMS_LOGGER);

	private final TreeMap<PeerID, SystemAdvertisement> view;
	private final long viewId;
	public final long masterViewId;
	private ReentrantLock viewLock = new ReentrantLock(true);

	/**
	 * Constructs the ClusterView object with a given TreeMap containing the system advertisements of members in the group.
	 * The membership is sorted based on the PeerIds.
	 *
	 * @param advertisements the Map of advertisements ordered by Peer ID sort
	 * @param viewId View ID
	 * @param masterViewId MasterView ID
	 */
	ClusterView(final TreeMap<PeerID, SystemAdvertisement> advertisements, final long viewId, final long masterViewId) {
		view = new TreeMap<PeerID, SystemAdvertisement>(advertisements);
		this.viewId = viewId;
		this.masterViewId = masterViewId;
	}

	/**
	 * Constructs the ClusterView object with a given TreeMap containing the system advertisements of members in the group.
	 * The membership is sorted based on the PeerIds.
	 *
	 * @param advertisement this nodes system advertisement
	 */
	ClusterView(SystemAdvertisement advertisement) {
		view = new TreeMap<PeerID, SystemAdvertisement>();
		lockLog("constructor()");
		viewLock.lock();
		try {
			view.put(advertisement.getID(), advertisement);
		} finally {
			viewLock.unlock();
		}
		this.viewId = 0;
		this.masterViewId = 0;
	}

	/**
	 * Retrieves a system advertisement from a the table.
	 *
	 * @param id instance id
	 * @return Returns the SystemAdvertisement associated with id
	 */
	public SystemAdvertisement get(final PeerID id) {
		return view.get(id);
	}

	/**
	 * Adds a system advertisement to the view.
	 *
	 * @param adv adds a system advertisement to the view
	 */
	public void add(final SystemAdvertisement adv) {
		lockLog("add()");
		viewLock.lock();
		try {
			view.put(adv.getID(), adv);
		} finally {
			viewLock.unlock();
		}
	}

	public boolean containsKey(final PeerID id) {
		boolean hasKey = false;
		lockLog("containsKey()");
		viewLock.lock();
		try {
			hasKey = view.containsKey(id);
		} finally {
			viewLock.unlock();
		}
		return hasKey;
	}

	/**
	 * Returns a sorted list containing the System Advertisements of peers in PeerId sorted order
	 *
	 * @return List - list of system advertisements
	 */
	public List<SystemAdvertisement> getView() {
		List<SystemAdvertisement> viewCopy;
		lockLog("getView()");
		viewLock.lock();
		try {
			viewCopy = new ArrayList<SystemAdvertisement>(view.values());
		} finally {
			viewLock.unlock();
		}
		return viewCopy;
	}

	/**
	 * Returns the list of peer names in the peer id sorted order from the cluster view
	 *
	 * @return List
	 */
	public List<String> getPeerNamesInView() {
		List<String> peerNamesList = new ArrayList<String>();
		lockLog("getPeerNamesInView()");
		viewLock.lock();
		try {
			for (PeerID peerID : view.keySet()) {
				peerNamesList.add(peerID.toString());
			}
		} finally {
			viewLock.unlock();
		}
		return peerNamesList;
	}

	/**
	 * Gets the viewSize attribute of the ClusterView object
	 *
	 * @return The viewSize
	 */
	public int getSize() {
		int size = 0;
		lockLog("getSize()");
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
	public SystemAdvertisement getMasterCandidate() {
		lockLog("getMasterCandidate()");
		viewLock.lock();
		try {
			final PeerID id = view.firstKey();
			final SystemAdvertisement adv = view.get(id);
			if (LOG.isLoggable(Level.FINE)) {
				LOG.log(Level.FINE,
				        new StringBuffer().append("Returning Master Candidate Node :").append(adv.getName()).append(' ').append(adv.getID()).toString());
			}
			return adv;
		} finally {
			viewLock.unlock();
		}
	}

	/**
	 * Determines whether this node is at the top of the list
	 *
	 * @param advertisement the advertisement to test
	 * @return true if this node is a the top of the list, false otherwise
	 */
	public boolean isFirst(SystemAdvertisement advertisement) {
		final PeerID id = view.firstKey();
		return advertisement.getID().equals(id);
	}

	/**
	 * Returns the cluster view ID
	 *
	 * @return the view id
	 */
	public long getClusterViewId() {
		return viewId;
	}

	/**
	 * Removes all of the elements from this set (optional operation).
	 */
	public void clear() {
		lockLog("clear()");
		viewLock.lock();
		try {
			view.clear();
		} finally {
			viewLock.unlock();
		}
	}

	private void lockLog(String method) {
		if (LOG.isLoggable(Level.FINE)) {
			LOG.log(Level.FINE,
			        MessageFormat.format("{0} viewLock Hold count :{1}, lock queue count:{2}", method, viewLock.getHoldCount(), viewLock.getQueueLength()));
		}
	}
}
