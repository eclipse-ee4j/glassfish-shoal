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

package com.sun.enterprise.gms.tools;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.sun.enterprise.gms.tools.MulticastTester.SEP;

/**
 * Used to listen for multicast messages.
 */
public class MultiCastReceiverThread extends Thread {

    static final StringManager sm = StringManager.getInstance();

    final AtomicBoolean receivedAnything = new AtomicBoolean(false);

    volatile boolean done = false;

    int mcPort;
    String mcAddress;
    boolean debug;
    String targetData;
    MulticastSocket ms;
    String expectedPrefix;

    public MultiCastReceiverThread(int mcPort, String mcAddress,
        boolean debug, String targetData) {
        super("McastReceiver");
        this.mcPort = mcPort;
        this.mcAddress = mcAddress;
        this.debug = debug;
        this.targetData = targetData;
        StringTokenizer st = new StringTokenizer(targetData, SEP);
        expectedPrefix = st.nextToken() + SEP;
    }

    @Override
    public void run() {
        log(String.format("expected message prefix is '%s'", expectedPrefix));
        try {
            final int bufferSize = 8192;

            InetAddress group = InetAddress.getByName(mcAddress);
            ms = new MulticastSocket(mcPort);
            ms.joinGroup(group);

            System.out.println(sm.get("listening.info"));
            Set<String> uniqueData = new HashSet<String>();

            /*
             * 'done' will almost never be read as true here unless
             * there is some unusual timing. But we have leaveGroup
             * and socket closing code here anyway to be polite. Maybe
             * the thread interrupt call will interrupt the receive call
             * in a different JDK impl/version.
             */
            while (!done) {
                DatagramPacket dp = new DatagramPacket(new byte[bufferSize],
                    bufferSize);
                ms.receive(dp);
                String newData = new String(dp.getData(), Charset.defaultCharset()).trim();
                log(String.format("received '%s'", newData));

                // if data is from some other process, ignore
                if (!newData.startsWith(expectedPrefix)) {
                    log("Ignoring previous data");
                    continue;
                }

                if (uniqueData.add(newData)) {
                    if (targetData.equals(newData)) {
                        System.out.println(sm.get("loopback.from",
                            MulticastTester.trimDataString(newData)));
                        receivedAnything.set(true);
                    } else {
                        System.out.println(sm.get("received.from",
                            MulticastTester.trimDataString(newData)));
                        receivedAnything.set(true);
                    }
                }
            }
        } catch (SocketException se) {
            log("caught socket exception as expected");
        } catch (Exception e) {
            System.err.println(sm.get("whoops", e.toString()));
            if (debug) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void interrupt() {
        if (ms != null) {
            log("closing socket in interrupt");
            try {
                ms.close();
            } catch (Throwable ignored) {
                log(ignored.getMessage());
            } finally {
                super.interrupt();
            }
        }
    }

    private void log(String msg) {
        if (debug) {
            System.err.println(String.format("%s: %s",
                getName(), msg));
        }
    }
}
