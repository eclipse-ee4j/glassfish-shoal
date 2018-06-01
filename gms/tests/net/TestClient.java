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

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.text.MessageFormat;

public class TestClient {
    /**
     * number of runs to make
     */
    private final static long RUNS = 10;
    private int SO_TO_VALUE = 500;
    private int SO_TO_INCREMENT = 500;

    public TestClient() {
    }

    /**
     * Interact with the server.
     */
    public void run() {
        try {
            System.out.println("Connecting to the server");
            Socket socket = new Socket();
            SocketAddress address = new InetSocketAddress(InetAddress.getLocalHost(), 9000);

            System.out.println(MessageFormat.format("Setting SO TO to :{0}ms", SO_TO_VALUE));
            socket.setSoTimeout(SO_TO_VALUE);

            long start = System.currentTimeMillis();
            socket.bind(address);
            System.out.println(MessageFormat.format("Connected Socket in {0}ms", System.currentTimeMillis() - start));
            System.out.println("closing connection");
            socket.close();
            SO_TO_VALUE += SO_TO_INCREMENT;
        } catch (IOException io) {
            io.printStackTrace();
        }
    }

    private void stop() {
    }

    /**
     *
     * @param args none recognized.
     */
    public static void main(String args[]) {

        try {
            Thread.currentThread().setName(TestClient.class.getName() + ".main()");
            TestClient testClient = new TestClient();
            for (int i = 1; i <= RUNS; i++) {
                System.out.println("Run #" + i);
                testClient.run();
            }
            testClient.stop();
        } catch (Throwable e) {
            System.out.flush();
            System.err.println("Failed : " + e);
            e.printStackTrace(System.err);
            System.exit(-1);
        }
    }
}
