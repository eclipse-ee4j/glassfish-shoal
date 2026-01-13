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

package org.glassfish.shoal.ha.group;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.shoal.gms.logging.GMSLogDomain;
import org.glassfish.shoal.ha.cache.group.GroupServiceProvider;
import org.glassfish.shoal.ha.cache.util.MessageReceiver;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Unit test for simple App.
 */
public class GroupServiceProviderTest extends TestCase {
    private static final Logger logger = GMSLogDomain.getLogger(GMSLogDomain.GMS_LOGGER);

    static private GroupServiceProvider gsp;
    static private MessageReceiver msgReceiver;
    static public int called = 0;
    final static private String COMPONENT = "testComponent";
    final static private String INSTANCE_NAME = "testInstance";
    static private AtomicBoolean initialized = new AtomicBoolean(false);

    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public GroupServiceProviderTest(String testName) {
        super(testName);
    }

    private void init() {
        if (initialized.compareAndSet(false, true)) {
            gsp = new GroupServiceProvider(INSTANCE_NAME, "testGroup", true);
            msgReceiver = new MessageReceiver() {
                @Override
                protected void handleMessage(String senderName, String messageToken, byte[] data) {
                    called++;
                    System.out.println("received message from member:" + senderName + " to component:" + messageToken + "data:" + new String(data));
                }
            };
            gsp.registerGroupMessageReceiver(COMPONENT, msgReceiver);
        }
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite() {
        return new TestSuite(GroupServiceProviderTest.class);
    }

    public void testSendMessageToSelf() {
        init();
        boolean result = gsp.sendMessage(INSTANCE_NAME, COMPONENT, new String("hello").getBytes());
        assertEquals("sendMessage to self", true, result);
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ie) {
        }
        assertEquals("verify received msg", true, called == 1);
    }

    public void testFailToSendMessage() {
        init();
        logger.setLevel(Level.FINE);
        boolean result = gsp.sendMessage("nonExistentInstance", COMPONENT, new String("hello").getBytes());
        assertEquals("sendMessage to nonExistentInstance", false, result);

        // double checked that second failure does not come out in server log until 12 hours pass.
        result = gsp.sendMessage("nonExistentInstance", COMPONENT, new String("hello").getBytes());
        assertEquals("sendMessage to nonExistentInstance", false, result);
        logger.setLevel(Level.INFO);
    }

    public void testFailToBroadcastBigMessage() {
        init();
        logger.setLevel(Level.FINE);
        byte[] bigPayload = new byte[70 * 1024];
        Arrays.fill(bigPayload, (byte) 'e');
        boolean result = gsp.sendMessage(null, COMPONENT, bigPayload);
        assertEquals("broadcast too large a payload", false, result);
        result = gsp.sendMessage("", COMPONENT, bigPayload);
        assertEquals("sendMessage to empty destination", false, result);

        logger.setLevel(Level.INFO);
    }

    public void testShutdown() {
        init();
        gsp.shutdown();
    }
}
