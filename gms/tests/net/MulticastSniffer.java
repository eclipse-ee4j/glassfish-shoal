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

package net;

import java.net.*;
import java.io.*;

public class MulticastSniffer {

    public static void main(String[] args) {
        InetAddress group = null;
        InetAddress interfaceAddr = null;
        int port = 0;
// read the address from the command line
        try {
            group = InetAddress.getByName(args[0]);
            port = Integer.parseInt(args[1]);
            if (args.length >= 3) {
                interfaceAddr = InetAddress.getByName(args[2]);
            }
        } // end try
        catch (Exception ex) {
// ArrayIndexOutOfBoundsException, NumberFormatException,
// or UnknownHostException
            System.err.println(
                    "Usage: java MulticastSniffer multicast_address port multicast_interface_address");
            System.exit(1);
        }
        MulticastSocket ms = null;
        try {
            //InetSocketAddress isa = new InetSocketAddress(interfaceAddr, port);
            //ms = new MulticastSocket(isa);
            ms = new MulticastSocket(port);
            if (interfaceAddr != null) {
                ms.setInterface(interfaceAddr);
            }
            ms.joinGroup(group);
            byte[] buffer = new byte[8192];
            while (true) {
                DatagramPacket dp = new DatagramPacket(buffer, buffer.length);
                ms.receive(dp);
                String s = new String(dp.getData());
                System.out.println(s);
            }
        } catch (IOException ex) {
            System.err.println(ex);
        } finally {
            if (ms != null) {
                try {
                    ms.leaveGroup(group);
                    ms.close();
                } catch (IOException ex) {
                }
            }
        }
    }
}
