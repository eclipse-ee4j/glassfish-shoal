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

import java.io.Serializable;

import com.sun.enterprise.ee.cms.impl.base.SystemAdvertisement;

/**
 * Denotes a Cluster View Change Event. Provides the event and the system advertisement in question.
 *
 * @author Shreedhar Ganapathy Date: Jun 29, 2006
 * @version $Revision$
 */
public class ClusterViewEvent implements Serializable {
	static final long serialVersionUID = 4125228994646649851L;

	private final ClusterViewEvents event;
	private final SystemAdvertisement advertisement;

	/**
	 * Constructor for the ClusterViewEvent object
	 *
	 * @param event the Event
	 * @param advertisement The system advertisement associated with the event
	 */
	ClusterViewEvent(final ClusterViewEvents event, final SystemAdvertisement advertisement) {

		if (event == null) {
			throw new IllegalArgumentException("Null event not allowed");
		}
		if (advertisement == null) {
			throw new IllegalArgumentException("Null advertisement not allowed");
		}
		this.event = event;
		this.advertisement = advertisement;
	}

	/**
	 * Gets the event attribute of the ClusterViewEvent object
	 *
	 * @return The event value
	 */
	public ClusterViewEvents getEvent() {
		return event;
	}

	/**
	 * Gets the advertisement attribute of the ClusterViewEvent object
	 *
	 * @return The advertisement value
	 */
	public SystemAdvertisement getAdvertisement() {
		return advertisement;
	}
}
