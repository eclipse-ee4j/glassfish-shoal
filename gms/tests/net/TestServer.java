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
import java.net.ServerSocket;
import java.net.Socket;

public class TestServer {

    public TestServer() {
    }

    /**
     * Wait for connections
     */
    public void run() {

        System.out.println("Starting ServerSocket");
        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(9000);
            serverSocket.setSoTimeout(0);
        } catch (IOException e) {
            System.out.println("failed to create a server socket");
            e.printStackTrace();
            System.exit(-1);
        }

        while (true) {
            try {
                System.out.println("Waiting for connections");
                Socket socket = serverSocket.accept();
                if (socket != null) {
                    socket.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * main
     *
     * @param args none recognized.
     */
    public static void main(String args[]) {
        try {
            Thread.currentThread().setName(TestServer.class.getName() + ".main()");
            TestServer testServer = new TestServer();
            testServer.run();
        } catch (Throwable e) {
            System.err.println("Failed : " + e);
            e.printStackTrace(System.err);
            System.exit(-1);
        }
    }

}
