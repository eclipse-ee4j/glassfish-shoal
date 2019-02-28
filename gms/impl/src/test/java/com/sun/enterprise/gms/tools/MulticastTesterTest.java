/*
 * Copyright (c) 2011, 2018 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.enterprise.gms.tools;

import junit.framework.TestCase;

public class MulticastTesterTest extends TestCase {
    private MulticastTester tester;

    public MulticastTesterTest(String testName) {
        super(testName);
    }

    public void mySetUp() {
    }

    public void testValidateMulticastDefaults() {
        // validate all defaults EXCEPT the default timeout of 20 seconds.
        // (in interest of running junit test in minimum amount of time)
        String[] params = { "--timeout", "3" };
        tester = new MulticastTester();
        tester.run(params);
    }

    public void testValidateMulticastBadTimeout() {
        String[] params = { "--timeout", "five" };
        tester = new MulticastTester();
        assertTrue("validate detection of non-numeric parameter for --timeout", tester.run(params) != 0);
    }

    public void testValidateMulticastBadPort() {
        String[] params = { "--multicastport", "five" };
        tester = new MulticastTester();
        tester.run(params);
        assertTrue("validate detection of non-numeric parameter for --multicastport", tester.run(params) != 0);
    }

    public void testValidateMulticastBadTimeToLive() {
        String[] params = { "--timetolive", "five" };
        tester = new MulticastTester();
        tester.run(params);
        assertTrue("validate detection of non-numeric parameter for --timetolive", tester.run(params) != 0);
    }

    public void testValidateMulticastBadSendPeriod() {
        String[] params = { "--sendperiod", "five" };
        tester = new MulticastTester();
        tester.run(params);
        assertTrue("validate detection of non-numeric parameter for --period", tester.run(params) != 0);
    }

    public void testValidateMulticastNonDefaults() {
        String[] params = { "--timeout", "3", "--multicastport", "3001", "--bindinterface", "127.0.0.1", "--timetolive", "3", "--debug", "--multicastaddress",
                "228.9.9.1", "--sendperiod", "1000" };
        tester = new MulticastTester();
        tester.run(params);
    }

    public void testValidateMulticastHelp() {
        String[] params = { "--help" };
        tester = new MulticastTester();
        tester.run(params);
    }
}
