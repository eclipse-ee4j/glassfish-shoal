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

package com.sun.enterprise.mgmt;

import java.text.SimpleDateFormat;
import java.util.logging.Level;
import java.util.Date;

import com.sun.enterprise.ee.cms.logging.GMSLogDomain;
import com.sun.enterprise.mgmt.transport.Message;
import com.sun.enterprise.mgmt.transport.MessageImpl;
import junit.framework.TestCase;

public class ReliableMulticastTest extends TestCase  {

    static final long TEST_EXPIRATION_DURATION_MS = 500;  // 1/2 second.
    private ReliableMulticast rm;
    private static final String RFC_3339_DATE_FORMAT =
            "yyyy-MM-dd'T'HH:mm:ss.SSSZ";
    private static final SimpleDateFormat dateFormatter =
            new SimpleDateFormat( RFC_3339_DATE_FORMAT );

    public ReliableMulticastTest( String testName ) {
        super( testName );
        GMSLogDomain.getMcastLogger().setLevel(Level.FINEST);
    }


    private Message createMessage(long seqId) {
        Message msg = new MessageImpl();
        msg.addMessageElement(MasterNode.MASTERVIEWSEQ, seqId);
        return msg;
    }


    private void mySetup() {
        rm = new ReliableMulticast(TEST_EXPIRATION_DURATION_MS);
        rm.add(createMessage(1), TEST_EXPIRATION_DURATION_MS);
        rm.add(createMessage(2), TEST_EXPIRATION_DURATION_MS);
        rm.add(createMessage(3), TEST_EXPIRATION_DURATION_MS);
        rm.add(createMessage(4), TEST_EXPIRATION_DURATION_MS);
        assertTrue(rm.sendHistorySize() == 4);
    }

    public void testReaperExpiration() {
        mySetup();
        try {
            Thread.sleep(TEST_EXPIRATION_DURATION_MS);
        } catch (InterruptedException ie) {
        }
        assertTrue(rm.sendHistorySize() == 4);
        try {
            Thread.sleep(TEST_EXPIRATION_DURATION_MS * 3);
        } catch (InterruptedException ie) {
        }
        assertTrue(rm.sendHistorySize() == 0);
        rm.stop();
    }


    public void testAdd() {
        mySetup();
        rm.stop();
    }

    public void testExpiration() {
        testAdd();
        rm.processExpired();
        assertTrue(rm.sendHistorySize() == 4);
         try {
            Thread.sleep(TEST_EXPIRATION_DURATION_MS * 3);
        } catch (InterruptedException ie) {}
        rm.processExpired();
        GMSLogDomain.getMcastLogger().info("sendHistorySize=" + rm.sendHistorySize());
        assertTrue(rm.sendHistorySize() == 0);
        rm.stop();
    }


}
