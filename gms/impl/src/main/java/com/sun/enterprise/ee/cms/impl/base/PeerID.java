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

package com.sun.enterprise.ee.cms.impl.base;

import java.io.Serializable;

/**
 * This class is representative of the identifier of a member
 *
 * <code>uniqueID</code> is used in order to identify a unique member. According to a kind of transport layers,
 * <code>uniqueID</code> type will be determined.
 *
 * @author Bongjae Chang
 */
public class PeerID<T extends Serializable> implements Serializable, Comparable<PeerID> {

	static final long serialVersionUID = 2618091647571033721L;

	public static final PeerID<Serializable> NULL_PEER_ID = new PeerID<Serializable>(null, null, null);

	private final T uniqueID;
	private final String groupName;
	private final String instanceName;

	public PeerID(T uniqueID, String groupName, String instanceName) {
		this.uniqueID = uniqueID;
		this.groupName = groupName;
		this.instanceName = instanceName;
	}

	public T getUniqueID() {
		return uniqueID;
	}

	public String getGroupName() {
		return groupName;
	}

	public String getInstanceName() {
		return instanceName;
	}

	public boolean equals(Object other) {
		if (this == other) {
			// check if this and other are both same Peerid. Works if both are NULL_PEER_ID.
			return true;
		} else if (other instanceof PeerID) {
			boolean equal = true;
			PeerID otherPeerID = (PeerID) other;
			if (uniqueID != null && uniqueID.equals(otherPeerID.getUniqueID())) {
				if (groupName != null) {
					equal = groupName.equals(otherPeerID.getGroupName());
				}
				if (!equal) {
					return false;
				}
				if (instanceName != null) {
					equal = instanceName.equals(otherPeerID.getInstanceName());
				}
				if (!equal) {
					return false;
				}
			} else {
				return false;
			}
			return true;
		} else {
			return false;
		}
	}

	public int hashCode() {
		int result = 17;
		if (uniqueID != null) {
			result = 37 * result + uniqueID.hashCode();
		}
		if (groupName != null) {
			result = 37 * result + groupName.hashCode();
		}
		if (instanceName != null) {
			result = 37 * result + instanceName.hashCode();
		}
		return result;
	}

	public String toString() {
		String uniqueIDString = ((uniqueID == null) ? "null" : uniqueID.toString());
		return uniqueIDString + ":" + groupName + ":" + instanceName;
	}

	@Override
	@SuppressWarnings("unchecked")
	public int compareTo(PeerID other) {
		if (this == other) {
			return 0;
		}
		if (other == null) {
			return 1;
		}
		if (this == NULL_PEER_ID) {
			return -1;
		}
		int result = groupName.compareTo(other.getGroupName());
		if (result != 0) {
			return result;
		}
		result = instanceName.compareTo(other.getInstanceName());
		if (result != 0) {
			return result;
		}

		final Class<T> uniqueIDClass = (Class<T>) uniqueID.getClass();
		if (Comparable.class.isAssignableFrom(uniqueIDClass) && uniqueIDClass.isAssignableFrom(other.getUniqueID().getClass())) {
			return ((Comparable<T>) uniqueID).compareTo((T) other.getUniqueID());
		} else {
			return uniqueID.toString().compareTo(other.getUniqueID().toString());
		}
	}
}
