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

import com.sun.enterprise.ee.cms.spi.MemberStates;

import java.util.List;

/**
 * Signal corresponding to JoinNotificationAction. This Signal enables the consumer to get specifics about a Join
 * notification. This Signal type will only be passed to a JoinNotificationAction. This Signal is delivered to
 * registered GMS Clients on all members of the group.
 * 
 * @author Shreedhar Ganapathy Date: Feb 3, 2005
 * @version $Revision$
 */
public interface JoinNotificationSignal extends Signal, GroupStartupNotificationSignal, RejoinableEvent {

	/**
	 * provides a list of all live and current CORE designated members.
	 *
	 * @return List containing the list of member token ids of core members
	 */
	List<String> getCurrentCoreMembers();

	/**
	 * provides a list of all live members i.e. CORE and SPECTATOR members.
	 * 
	 * @return List containing the list of member token ids of all members.
	 */
	List<String> getAllCurrentMembers();

	/**
	 * Provides the current liveness state of the member whose joining the group is being signaled by this JoinNotification
	 * Signal. The state corresponds to one of the states enumerated by the MemberStates enum
	 * 
	 * @return MemberStates
	 */
	MemberStates getMemberState();
}
