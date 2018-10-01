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

package com.sun.enterprise.mgmt.transport;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import junit.framework.TestCase;

public class NetworkUtilityTest extends TestCase {

    /*
     * Separate test in case there is an error that prevents test from running.
     */
    public void testBindInterfaceValidLocalHost() {
        InetAddress localhost;
        try {
            localhost = InetAddress.getLocalHost();
        } catch (UnknownHostException uhe) {
            throw new RuntimeException(uhe);
        }
        String s = localhost.getHostAddress();
        assertTrue(String.format("Expected true result for '%s'", s), NetworkUtility.isBindAddressValid(s));
        s = localhost.getHostName();
        assertTrue(String.format("Expected true result for '%s'", s), NetworkUtility.isBindAddressValid(s));
    }

    /*
     * Change the values to test specific addresses. This has been tested with my local IP and IPv6 addresses, but those
     * values cannot obviously be checked in.
     */
    public void testBindInterfaceValid() {
        final String local[] = { "127.0.0.1", "127.0.1", // same as 127.0.0.1
                "127.1", // ditto
                "localhost"
//            "::1" // ipv6 version of 127.0.0.1
        };
        final String notLocalOrValid[] = { "_", "99999999999999", "www.oracle.com" };

        for (String s : local) {
            assertTrue(String.format("Expected true result for '%s'", s), NetworkUtility.isBindAddressValid(s));
        }
        for (String s : notLocalOrValid) {
            assertFalse(String.format("Expected false result for '%s'", s), NetworkUtility.isBindAddressValid(s));
        }
    }

    public void testAllLocalAddresses() {
        List<InetAddress> locals = NetworkUtility.getAllLocalAddresses();
        for (InetAddress local : locals) {
            assertTrue(NetworkUtility.isBindAddressValid(local.getHostAddress()));
        }
    }

    public void testGetFirstAddress() throws IOException {
        boolean preferIPv6Addresses = NetworkUtility.getPreferIpv6Addresses();

        System.out.println("AllLocalAddresses() = " + NetworkUtility.getAllLocalAddresses());
        System.out.println("getFirstNetworkInterface(preferIPv6) = " + NetworkUtility.getFirstNetworkInterface(preferIPv6Addresses));
        System.out.println("getFirstNetworkInterface(!preferIPv6) = " + NetworkUtility.getFirstNetworkInterface(!preferIPv6Addresses));
        System.out.println("java.net.preferIPv6Addresses=" + preferIPv6Addresses);
        System.out.println("getFirstInetAddress( preferIPv6Addresses:" + preferIPv6Addresses + ") = " + NetworkUtility.getFirstInetAddress());
        System.out.println("getFirstInetAddress( true ) = " + NetworkUtility.getFirstInetAddress(true));
        System.out.println("getFirstInetAddress( false ) = " + NetworkUtility.getFirstInetAddress(false));
        System.out.println("getNetworkInetAddress(firstNetworkInteface, true) = "
                + NetworkUtility.getNetworkInetAddress(NetworkUtility.getFirstNetworkInterface(true), true));
        System.out.println("getNetworkInetAddress(firstNetworkInteface, false) = "
                + NetworkUtility.getNetworkInetAddress(NetworkUtility.getFirstNetworkInterface(false), false));

    }
}
