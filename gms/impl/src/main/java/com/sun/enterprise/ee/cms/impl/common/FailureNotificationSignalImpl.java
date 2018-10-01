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
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.enterprise.ee.cms.core.AliveAndReadyView;
import com.sun.enterprise.ee.cms.core.FailureNotificationSignal;
import com.sun.enterprise.ee.cms.core.SignalAcquireException;
import com.sun.enterprise.ee.cms.core.SignalReleaseException;
import com.sun.enterprise.ee.cms.logging.GMSLogDomain;

/**
 * Implements FailureNotificationSignal
 *
 * @author Shreedhar Ganapathy Date: Jan 21, 2004
 * @version $Revision$
 */
public class FailureNotificationSignalImpl implements FailureNotificationSignal {
	protected String failedMember = null;
	protected String groupName = null;
	protected static final String MEMBER_DETAILS = "MEMBERDETAILS";
	protected GMSContext ctx;

	// Logging related stuff
	protected static final Logger logger = GMSLogDomain.getLogger(GMSLogDomain.GMS_LOGGER);
	protected long startTime;
	private AliveAndReadyView previousView = null;
	private AliveAndReadyView currentView = null;

	FailureNotificationSignalImpl() {

	}

	public FailureNotificationSignalImpl(final String failedMember, final String groupName, final long startTime) {
		this.failedMember = failedMember;
		this.groupName = groupName;
		this.startTime = startTime;
		ctx = GMSContextFactory.getGMSContext(groupName);
	}

	FailureNotificationSignalImpl(final FailureNotificationSignal signal) {
		this.failedMember = signal.getMemberToken();
		this.groupName = signal.getGroupName();
		this.startTime = signal.getStartTime();
		ctx = GMSContextFactory.getGMSContext(groupName);
		this.previousView = signal.getPreviousView();
		this.currentView = signal.getCurrentView();
	}

	/**
	 * Signal is acquired prior to processing of the signal to protect group resources that are being acquired from being
	 * affected by a race condition
	 *
	 * @throws com.sun.enterprise.ee.cms.core.SignalAcquireException the exception when signal is not acquired
	 */
	public void acquire() throws SignalAcquireException {
		logger.log(Level.FINE, "FailureNotificationSignal Acquired...");
	}

	/**
	 * Signal is released after processing of the signal to bring the group resources to a state of availability
	 *
	 * @throws com.sun.enterprise.ee.cms.core.SignalReleaseException the exception when signal is not released
	 */
	public void release() throws SignalReleaseException {
		failedMember = null;
		logger.log(Level.FINE, "FailureNotificationSignal Released...");
	}

	/**
	 * returns the identity token of the failed member
	 */
	public String getMemberToken() {
		return this.failedMember;
	}

	/**
	 * returns the identity token of the failed member
	 *
	 * @return java.lang.String
	 * @deprecated
	 */
	public String getFailedMemberToken() {
		return this.failedMember;
	}

	/**
	 * returns the details of the member who caused this Signal to be generated returns a Map containing key-value pairs
	 * constituting data pertaining to the member's details
	 *
	 * @return Map - &lt;Serializable, Serializable&gt;
	 */
	public Map<Serializable, Serializable> getMemberDetails() {
		return ctx.getDistributedStateCache().getFromCacheForPattern(MEMBER_DETAILS, failedMember);
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
