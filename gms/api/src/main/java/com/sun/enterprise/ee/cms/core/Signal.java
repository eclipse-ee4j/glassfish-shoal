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

package com.sun.enterprise.ee.cms.core;

import java.io.Serializable;
import java.util.Map;

/**
 * A Signal denotes a group event or a message event.
 *
 * Subtypes of Signal will define operations specific to their Signal functionalities i.e specific group events or a
 * message event.
 *
 * <code>Action</code>s consume <code>Signal</code>s.
 *
 * Each Signal is delivered on its own thread.
 *
 * @author Shreedhar Ganapathy Date: November 07, 2003
 * @version $Revision$
 */
public interface Signal {
	/**
	 * Signal is acquired prior to processing of the signal to protect group resources being acquired from being affected by
	 * a race condition Signal must be mandatorily acquired before any processing for recovery operations.
	 *
	 * @throws SignalAcquireException
	 */
	void acquire() throws SignalAcquireException;

	/**
	 * Signal is released after processing of the signal to bring the group resources to a state of availability Signal
	 * should be madatorily released after recovery process is completed.
	 *
	 * @throws SignalReleaseException
	 */
	void release() throws SignalReleaseException;

	/**
	 * returns the identity token of the member that caused this signal to be generated. For instance, in the case of a
	 * MessageSignal, this member token would be the sender. In the case of a FailureNotificationSignal, this member token
	 * would be the failed member. In the case of a JoinNotificationSignal or GracefulShutdownSignal, the member token would
	 * be the member who joined or is being gracefully shutdown, respectively.
	 *
	 * @return returns the identity token of the member
	 */
	String getMemberToken();

	/**
	 * returns the details of the member who caused this Signal to be generated returns a Map containing key-value pairs
	 * constituting data pertaining to the member's details
	 *
	 * @return Map <Serializable, Serializable>
	 */
	Map<Serializable, Serializable> getMemberDetails();

	/**
	 * returns the group to which the member involved in the Signal belonged to
	 *
	 * @return String
	 */
	String getGroupName();

	/**
	 * returns the start time of the member involved in this Signal.
	 *
	 * @return long - time stamp of when this member started
	 */
	long getStartTime();
}
