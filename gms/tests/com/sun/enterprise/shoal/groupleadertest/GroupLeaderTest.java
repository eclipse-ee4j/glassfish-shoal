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

package com.sun.enterprise.shoal.groupleadertest;

import java.util.UUID;

import com.sun.enterprise.ee.cms.core.GMSException;
import com.sun.enterprise.ee.cms.core.GMSFactory;
import com.sun.enterprise.ee.cms.core.GroupHandle;
import com.sun.enterprise.ee.cms.core.GroupManagementService;
import com.sun.enterprise.ee.cms.core.GroupManagementService.MemberType;

/**
 * Simple test for GroupLeader
 *
 * @author leehui
 */

public class GroupLeaderTest {

	
	/**
	 * main
	 * 
	 * @param args command line args
	 */
	public static void main(String[] args) {
		
		
		String serverToken = UUID.randomUUID().toString();
		GroupManagementService gms = (GroupManagementService) GMSFactory.startGMSModule(serverToken, "DemoGroup", MemberType.CORE, null);
		try {
			gms.join();
		} catch (GMSException e) {
			e.printStackTrace();
		}
		
		GroupHandle groupHandle = gms.getGroupHandle();
		System.out.println(groupHandle.isGroupLeader());
		System.out.println(groupHandle.getGroupLeader());
		
		try {
			Thread.sleep(10000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		System.out.println(groupHandle.isGroupLeader());
		System.out.println(groupHandle.getGroupLeader());
		
		
	}

}
