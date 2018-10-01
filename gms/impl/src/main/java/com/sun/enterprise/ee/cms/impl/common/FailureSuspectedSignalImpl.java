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

import com.sun.enterprise.ee.cms.core.FailureSuspectedSignal;
import com.sun.enterprise.ee.cms.core.SignalAcquireException;
import com.sun.enterprise.ee.cms.core.SignalReleaseException;
import com.sun.enterprise.ee.cms.logging.GMSLogDomain;

import java.io.Serializable;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Shreedhar Ganapathy Date: Sep 14, 2005
 * @version $Revision$
 */
public class FailureSuspectedSignalImpl implements FailureSuspectedSignal {
	protected String failedMember = null;
	protected String groupName = null;
	protected static final String MEMBER_DETAILS = "MEMBERDETAILS";
	protected GMSContext ctx;

	// Logging related stuff
	protected static final Logger logger = GMSLogDomain.getLogger(GMSLogDomain.GMS_LOGGER);
	protected long startTime;

	FailureSuspectedSignalImpl() {

	}

	public FailureSuspectedSignalImpl(final String failedMember, final String groupName, final long startTime) {
		this.failedMember = failedMember;
		this.groupName = groupName;
		this.startTime = startTime;
		ctx = GMSContextFactory.getGMSContext(groupName);
	}

	FailureSuspectedSignalImpl(final FailureSuspectedSignal signal) {
		this.failedMember = signal.getMemberToken();
		this.groupName = signal.getGroupName();
		this.startTime = signal.getStartTime();
		ctx = GMSContextFactory.getGMSContext(groupName);
	}

	/**
	 * Signal is acquired prior to processing of the signal to protect group resources being acquired from being affected by
	 * a race condition Signal must be mandatorily acquired before any processing for recovery operations.
	 *
	 * @throws com.sun.enterprise.ee.cms.core.SignalAcquireException Exception when unable to acquire the signal
	 *
	 */
	public void acquire() throws SignalAcquireException {
		logger.log(Level.FINE, "FailureSuspectedSignal Acquired...");
	}

	/**
	 * Signal is released after processing of the signal to bring the group resources to a state of availability Signal
	 * should be madatorily released after recovery process is completed.
	 *
	 * @throws com.sun.enterprise.ee.cms.core.SignalReleaseException Exception when unable to release the signal
	 *
	 */
	public void release() throws SignalReleaseException {
		failedMember = null;
		logger.log(Level.FINE, "FailureSuspectedSignal Released...");
	}

	/**
	 * returns the identity token of the member that caused this signal to be generated. For instance, in the case of a
	 * MessageSignal, this member token would be the sender. In the case of a FailureNotificationSignal, this member token
	 * would be the failed member. In the case of a JoinNotificationSignal or GracefulShutdownSignal, the member token would
	 * be the member who joined or is being gracefully shutdown, respectively.
	 */
	public String getMemberToken() {
		return this.failedMember;
	}

	/**
	 * returns the details of the member who caused this Signal to be generated returns a Map containing key-value pairs
	 * constituting data pertaining to the member's details
	 *
	 * @return Map &lt;Serializable, Serializable&gt;
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

	/**
	 * returns the start time of the member involved in this Signal.
	 *
	 * @return long - time stamp of when this member started
	 */
	public long getStartTime() {
		return startTime;
	}
}
