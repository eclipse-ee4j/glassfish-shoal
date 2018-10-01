
/*
 * Copyright (c) 2010, 2018 Oracle and/or its affiliates. All rights reserved.
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

import com.sun.enterprise.ee.cms.core.*;
import junit.framework.TestCase;

import java.util.Properties;

public class GMSFactoryTest extends TestCase {

	public GMSFactoryTest(String testName) {
		super(testName);
	}

	void mySetup() {
	}

	// test to verify i18n string lookup for GMSNotInitializedException thrown when GMSFactory.getGMSModule() is called and
	// there are no gms groups yet.
	public void testGMSFactoryGetGMSModuleThrows() {
		mySetup();
		try {
			GMSFactory.getGMSModule();
			assertTrue(Boolean.FALSE);
		} catch (GMSException ge) {
			assertTrue(Boolean.TRUE);
			System.out.println("passed. exception message:" + ge.getLocalizedMessage());
		}
		try {
			GMSFactory.getGMSModule("undefinedGroup");
			assertTrue(Boolean.FALSE);
		} catch (GMSException ge) {
			assertTrue(Boolean.TRUE);
			System.out.println("passed. exception message:" + ge.getLocalizedMessage());
		}
		GMSFactory.setGMSEnabledState("noGmsGroup", false);
		try {
			GMSFactory.getGMSModule("noGmsGroup");
			assertTrue(Boolean.FALSE); // fail if no exception thrown.
		} catch (GMSException ge) {
			assertTrue(Boolean.TRUE);
			System.out.println("passed. exception message:" + ge.getLocalizedMessage());
		}

	}

	public void testStartGMSModule() {
		mySetup();
		try {
			GMSFactory.startGMSModule(null, "group", GroupManagementService.MemberType.CORE, new Properties());
			assertTrue(Boolean.FALSE);
		} catch (RuntimeException ge) {
			assertTrue(Boolean.TRUE);
			System.out.println("passed. exception message:" + ge.getLocalizedMessage());
		}
		try {
			GMSFactory.startGMSModule("instanceName", null, GroupManagementService.MemberType.CORE, new Properties());
			assert (Boolean.FALSE);
		} catch (RuntimeException ge) {
			assertTrue(Boolean.TRUE);
			System.out.println("passed. exception message:" + ge.getLocalizedMessage());
		}

	}
}
