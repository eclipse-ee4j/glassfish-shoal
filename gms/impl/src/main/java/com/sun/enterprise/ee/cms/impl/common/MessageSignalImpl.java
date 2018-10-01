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
import java.util.Hashtable;
import java.util.Map;

import com.sun.enterprise.ee.cms.core.MessageSignal;
import com.sun.enterprise.ee.cms.core.SignalAcquireException;
import com.sun.enterprise.ee.cms.core.SignalReleaseException;

/**
 * Implements MessageSignal and provides methods to access message sent by a remote member.
 *
 * @author Shreedhar Ganapathy Date: Jan 20, 2004
 * @version $Revision$
 */
public class MessageSignalImpl implements MessageSignal {
	private byte[] message;
	private String targetComponent;
	private String sender;
	private String groupName;
	private long startTime;

	public MessageSignalImpl(final byte[] message, final String targetComponent, final String sender, final String groupName, final long startTime) {
		this.message = message;
		this.targetComponent = targetComponent;
		this.sender = sender;
		this.groupName = groupName;
		this.startTime = startTime;
	}

	/**
	 * Returns the target component in this member to which this message is addressed.
	 *
	 * @return String targetComponent
	 */
	public String getTargetComponent() {
		return targetComponent;
	}

	/**
	 * Returns the message(payload) as a byte array.
	 *
	 * @return byte[]
	 */
	public byte[] getMessage() {
		return message;
	}

	/**
	 * Signal is acquired prior to processing of the signal to protect group resources being acquired from being affected by
	 * a race condition
	 *
	 * @throws com.sun.enterprise.ee.cms.core.SignalAcquireException Exception when unable to aquire the signal
	 */
	public void acquire() throws SignalAcquireException {
	}

	/**
	 * Signal is released after processing of the signal to bring the group resources to a state of availability
	 *
	 * @throws com.sun.enterprise.ee.cms.core.SignalReleaseException Exception when unable to release the signal
	 */
	public void release() throws SignalReleaseException {
		// JMF: do not release message resources.
		// was a bug when sending a message to null TargetComponent and more than one target component was registered.

		// message=null;
		// targetComponent=null;
		// sender=null;
	}

	public String getMemberToken() {
		return sender;
	}

	public Map<Serializable, Serializable> getMemberDetails() {
		return new Hashtable<Serializable, Serializable>();
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
}
