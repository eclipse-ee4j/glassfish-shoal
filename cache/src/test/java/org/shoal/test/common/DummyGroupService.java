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

package org.shoal.test.common;

import java.util.Collections;
import java.util.List;

import org.shoal.ha.cache.impl.util.MessageReceiver;
import org.shoal.ha.group.GroupMemberEventListener;
import org.shoal.ha.group.GroupService;

/**
 * @author Mahesh Kannan
 */
public class DummyGroupService implements GroupService {

	private String memberName;

	private String groupName;

	public DummyGroupService(String memberName, String groupName) {
		this.memberName = memberName;
		this.groupName = groupName;
	}

	@Override
	public String getGroupName() {
		return groupName;
	}

	@Override
	public String getMemberName() {
		return memberName;
	}

	@Override
	public void registerGroupMemberEventListener(GroupMemberEventListener listener) {
		// To change body of implemented methods use File | Settings | File Templates.
	}

	@Override
	public void removeGroupMemberEventListener(GroupMemberEventListener listener) {
		// To change body of implemented methods use File | Settings | File Templates.
	}

	@Override
	public void close() {
		// To change body of implemented methods use File | Settings | File Templates.
	}

	@Override
	public void registerGroupMessageReceiver(String messagetoken, MessageReceiver receiver) {
		// To change body of implemented methods use File | Settings | File Templates.
	}

	@Override
	public boolean sendMessage(String targetMemberName, String token, byte[] data) {
		return false; // To change body of implemented methods use File | Settings | File Templates.
	}

	@Override
	public List<String> getCurrentCoreMembers() {
		return Collections.EMPTY_LIST;
	}
}
