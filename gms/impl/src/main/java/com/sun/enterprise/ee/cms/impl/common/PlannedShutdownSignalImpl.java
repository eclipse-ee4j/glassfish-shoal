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

package com.sun.enterprise.ee.cms.impl.common;

import java.io.Serializable;
import java.util.Map;
import java.util.logging.Logger;

import com.sun.enterprise.ee.cms.core.AliveAndReadyView;
import com.sun.enterprise.ee.cms.core.GMSConstants;
import com.sun.enterprise.ee.cms.core.PlannedShutdownSignal;
import com.sun.enterprise.ee.cms.core.SignalAcquireException;
import com.sun.enterprise.ee.cms.core.SignalReleaseException;
import com.sun.enterprise.ee.cms.logging.GMSLogDomain;

/**
 * Implementation of PlannedShutdownSignal.
 *
 * @author Shreedhar Ganapathy Date: Feb 22, 2005
 * @version $Revision$
 */
public class PlannedShutdownSignalImpl implements PlannedShutdownSignal {
	private String memberToken;
	private String groupName;
	// Logging related stuff
	protected static final Logger logger = GMSLogDomain.getLogger(GMSLogDomain.GMS_LOGGER);
	private static final String MEMBER_DETAILS = "MEMBERDETAILS";
	private GMSContext ctx;
	private long startTime;
	private GMSConstants.shutdownType shutdownType;
	private AliveAndReadyView previousView = null;
	private AliveAndReadyView currentView = null;

	public PlannedShutdownSignalImpl(final String memberToken, final String groupName, final long startTime, final GMSConstants.shutdownType shutdownType) {
		this.memberToken = memberToken;
		this.groupName = groupName;
		this.startTime = startTime;
		this.shutdownType = shutdownType;
		ctx = GMSContextFactory.getGMSContext(groupName);
	}

	PlannedShutdownSignalImpl(final PlannedShutdownSignal signal) {
		this.memberToken = signal.getMemberToken();
		this.groupName = signal.getGroupName();
		this.startTime = signal.getStartTime();
		this.shutdownType = signal.getEventSubType();
		ctx = GMSContextFactory.getGMSContext(groupName);
		this.previousView = signal.getPreviousView();
		this.currentView = signal.getCurrentView();
	}

	/**
	 * Signal is acquired prior to processing of the signal to protect group resources being acquired from being affected by
	 * a race condition
	 *
	 * @throws com.sun.enterprise.ee.cms.core.SignalAcquireException Exception when unable to aquire the signal
	 *
	 */
	public void acquire() throws SignalAcquireException {

	}

	/**
	 * Signal is released after processing of the signal to bring the group resources to a state of availability
	 *
	 * @throws com.sun.enterprise.ee.cms.core.SignalReleaseException Exception when unable to release the signal
	 *
	 */
	public void release() throws SignalReleaseException {
		memberToken = null;
	}

	public String getMemberToken() {
		return memberToken;
	}

	/**
	 * returns the details of the member who caused this Signal to be generated returns a Map containing key-value pairs
	 * constituting data pertaining to the member's details
	 *
	 * @return Map - &lt;Serializable, Serializable&gt;
	 */
	public Map<Serializable, Serializable> getMemberDetails() {
		return ctx.getDistributedStateCache().getFromCacheForPattern(MEMBER_DETAILS, memberToken);
	}

	/**
	 * returns the group to which the member involved in the Signal belonged to
	 *
	 * @return String
	 */
	public String getGroupName() {
		return groupName;
	}

	public long getStartTime() {
		return startTime;
	}

	/**
	 * Planned shutdown events can be one of two types, Group Shutdown or Instance Shutdown. These types are defined in an
	 * enum in the class GMSConstants.shutdownType
	 *
	 * @see com.sun.enterprise.ee.cms.core.GMSConstants
	 * @return GMSConstants.shutdownType
	 */
	public GMSConstants.shutdownType getEventSubType() {
		return shutdownType;
	}

	@Override
	public AliveAndReadyView getCurrentView() {
		return currentView;
	}

	@Override
	public AliveAndReadyView getPreviousView() {
		return previousView;
	}

	void setCurrentView(AliveAndReadyView current) {
		currentView = current;
	}

	void setPreviousView(AliveAndReadyView previous) {
		previousView = previous;
	}
}
