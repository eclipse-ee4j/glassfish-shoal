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

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

/**
 * Provides support for maintaining information about an impending shutdown announcement either in an instance context
 * or in a group context. An instance of this object is consulted for distinguishing an abnormal failure from a planned
 * shutdown resulting in a failure notification from heart beat agents. Also consulted for determining if a member being
 * suspected has already announced shutdown or there is a group shutdown.
 *
 * @author Shreedhar Ganapathy Date: Sep 21, 2005
 * @version $Revision$
 */
public class ShutdownHelper {
	private final List<String> gracefulShutdownList = new Vector<String>();
	private final List<String> groupShutdownList = new ArrayList<String>();

	public ShutdownHelper() {

	}

	public synchronized boolean isGroupBeingShutdown(final String groupName) {
		return groupShutdownList.contains(groupName);
	}

	public synchronized boolean isMemberBeingShutdown(final String memberToken) {
		return gracefulShutdownList.contains(memberToken);
	}

	public void addToGroupShutdownList(final String groupName) {
		synchronized (groupShutdownList) {
			groupShutdownList.add(groupName);
		}
	}

	public void addToGracefulShutdownList(final String memberToken) {
		synchronized (gracefulShutdownList) {
			gracefulShutdownList.add(memberToken);
		}
	}

	public void removeFromGracefulShutdownList(final String memberToken) {
		synchronized (gracefulShutdownList) {
			gracefulShutdownList.remove(memberToken);
		}
	}

	public void removeFromGroupShutdownList(final String groupName) {
		synchronized (groupShutdownList) {
			groupShutdownList.remove(groupName);
		}
	}
}
