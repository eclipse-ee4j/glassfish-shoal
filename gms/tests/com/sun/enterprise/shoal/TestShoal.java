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

package com.sun.enterprise.shoal;

import com.sun.enterprise.ee.cms.core.CallBack;
import com.sun.enterprise.ee.cms.core.GMSException;
import com.sun.enterprise.ee.cms.core.GMSFactory;
import com.sun.enterprise.ee.cms.core.GroupManagementService;
import com.sun.enterprise.ee.cms.core.GroupManagementService.MemberType;
import com.sun.enterprise.ee.cms.core.JoinNotificationSignal;
import com.sun.enterprise.ee.cms.core.MessageSignal;
import com.sun.enterprise.ee.cms.core.ServiceProviderConfigurationKeys;
import com.sun.enterprise.ee.cms.core.Signal;
import com.sun.enterprise.ee.cms.core.GMSConstants;
import com.sun.enterprise.ee.cms.impl.client.JoinNotificationActionFactoryImpl;
import com.sun.enterprise.ee.cms.impl.client.MessageActionFactoryImpl;

import java.util.Properties;

/**
 * @author Rafael Barbosa
 */
public class TestShoal implements Runnable, CallBack {
    String serviceName = "service";
    GroupManagementService gms;
    private final Object sendMessagesSignal = new Object();
    private int nbOfMembers = 2;
    int total_msgs_received = 0;

    public TestShoal() {
        Properties props = new Properties();
        props.put(ServiceProviderConfigurationKeys.LOOPBACK.toString(), "true");
        gms = (GroupManagementService) GMSFactory.startGMSModule(System.getProperty("INSTANCEID"), "group", MemberType.CORE, props);

        try {
            gms.addActionFactory(new MessageActionFactoryImpl(this), serviceName);
            gms.addActionFactory(new JoinNotificationActionFactoryImpl(this));
            gms.join();
        } catch (GMSException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void run() {
        if (gms.getGroupHandle().getAllCurrentMembers().size() < nbOfMembers) {
            System.out.println("Waiting for all members to join...");
            synchronized (sendMessagesSignal) {
                try {
                    sendMessagesSignal.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        int num_msgs = 10000;
        byte[] payload = generatePayload();
        long log_interval = 1000;
        int total_msgs = 0;
        try {
            for (int i = 0; i < num_msgs; i++) {
                gms.getGroupHandle().sendMessage(serviceName, payload);
                total_msgs++;
                if (total_msgs % log_interval == 0) {
                    System.out.println("++ sent " + total_msgs);
                }
            }
        } catch (GMSException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
            synchronized (sendMessagesSignal) {
                try {
                    sendMessagesSignal.wait(60000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        // run is complete shutdown the instance
        gms.shutdown(GMSConstants.shutdownType.INSTANCE_SHUTDOWN);
    }

    byte[] generatePayload() {
        byte[] payload = new byte[128];
        for (int i = 0; i < payload.length; i++) {
            if (i % 2 == 0) {
                payload[i] = 1;
            }
        }
        return payload;
    }

    public void processNotification(Signal signal) {
        if (signal instanceof JoinNotificationSignal) {
            if (gms.getGroupHandle().getAllCurrentMembers().size() == nbOfMembers) {
                synchronized (sendMessagesSignal) {
                    sendMessagesSignal.notify();
                }
            }
        } else if (signal instanceof MessageSignal) {
            total_msgs_received++;
            if (total_msgs_received % 1000 == 0) {
                System.out.println("-- received " + total_msgs_received);
            }
        } else {
            System.err.println(new StringBuffer().append(serviceName)
                    .append(": Notification Received from:")
                    .append(signal.getMemberToken())
                    .append(":[")
                    .append(signal.toString())
                    .append("] has been processed")
                    .toString());
        }
    }

    public static void main(String[] args) {
        Thread t = new Thread(new TestShoal());
        t.start();
    }
}
