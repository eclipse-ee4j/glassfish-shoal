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

package com.sun.enterprise.shoal.memberdetailstest;

import java.io.Serializable;
import java.util.Collection;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.sun.enterprise.ee.cms.core.GMSException;
import com.sun.enterprise.ee.cms.core.GMSFactory;
import com.sun.enterprise.ee.cms.core.GroupManagementService;
import com.sun.enterprise.ee.cms.core.GroupManagementService.MemberType;
import com.sun.enterprise.ee.cms.impl.common.GroupManagementServiceImpl;

/**
 * Simple test for MemberDetails
 *
 * @author leehui
 */

public class MemberDetailsTest {

	/**
	 * main
	 * 
	 * every node's member details contains a key named memberToken.
	 * 
	 * start MemberDetailsTest's serveral instance,see the console's infomation. 
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
						
		Map<String, Object> memberDetails = new Hashtable<String, Object>();			
		memberDetails.put("memberToken", serverToken); 	
		
		try {
			((GroupManagementServiceImpl)gms).setMemberDetails(serverToken, memberDetails);
		} catch (GMSException e) {
			e.printStackTrace();
		}
		
		while(true){
			try {
				Thread.sleep(3000);
			} catch (InterruptedException e) {
				System.out.println("error");
				break;
			}
			System.out.println("********************************************************");
			Map<Serializable,Serializable> members = gms.getAllMemberDetails("memberToken");
			Collection<Serializable> memberValues = members.values();
			for(Serializable member : memberValues){
				System.out.println(member);
			}
			System.out.println("---The above print result should be the same as below---");
			List<String> allMembers = gms.getGroupHandle().getAllCurrentMembers();
			for(String member : allMembers){
				System.out.println(gms.getMemberDetails(member).get("memberToken"));
			}
			System.out.println();
			
		}
	}

}
