/*
 * Copyright (c) 2008, 2018 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.enterprise.ee.cms.tests.multigroupjoin;

/**
 * Created by IntelliJ IDEA.
 * User: sheetal
 * Date: Jan 14, 2008
 * Time: 2:56:41 PM
 * This test is for checking if a server instance can join 2 different groups within the same VM
 * This test can be run in 2 different terminals to see if the 2 server instances that are started in 2 different
 * VMs can join the 2 groups and send/receive messages from each other.
 */

import com.sun.enterprise.ee.cms.core.*;
import com.sun.enterprise.ee.cms.impl.client.*;
import com.sun.enterprise.ee.cms.impl.base.Utility;

import java.text.MessageFormat;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MultiGroupJoinTest implements CallBack {
    final static Logger logger = Logger.getLogger("MultiGroupJoinTest");
    final Object waitLock = new Object();
    final String serverName;
    public MultiGroupJoinTest(String serverName) {
        this.serverName = serverName;
    }

    public static void main(String[] args){
        Utility.setLogger(logger);
        Utility.setupLogHandler();
        MultiGroupJoinTest multiGroupJoin = new MultiGroupJoinTest(System.getProperty("INSTANCEID"));
        try {
            multiGroupJoin.runSimpleSample();
        } catch (GMSException e) {
            logger.log(Level.SEVERE, "Exception occured while joining group:" + e);
        }
    }

    /**
     * Runs this sample
     * @throws GMSException
     */
    private void runSimpleSample() throws GMSException {
        logger.log(Level.INFO, "Starting MultiGroupJoinTest....");

        //final String serverName = "server"+System.currentTimeMillis();
        final String group1 = "Group1";
        final String group2 = "Group2";

        //initialize Group Management Service
        GroupManagementService gms1 = initializeGMS(serverName, group1);
        GroupManagementService gms2 = initializeGMS(serverName, group2);

        //register for Group Events
        registerForGroupEvents(gms1);
        registerForGroupEvents(gms2);
        //join group
        joinGMSGroup(group1, gms1);
        joinGMSGroup(group2, gms2);
        try {
            //send some messages
            sendMessages(gms1, serverName, group1);
            sendMessages(gms2, serverName, group2);
            waitForShutdown();

        } catch (InterruptedException e) {
            logger.log(Level.WARNING, e.getMessage());
        }
        //leave the group gracefully
        leaveGroupAndShutdown(serverName, gms1);
        leaveGroupAndShutdown(serverName, gms2);
        System.exit(0);

    }

    private GroupManagementService initializeGMS(String serverName, String groupName) {
        logger.log(Level.INFO, "Initializing Shoal for member: "+serverName+" group:"+groupName);
        return (GroupManagementService) GMSFactory.startGMSModule(serverName,
                groupName, GroupManagementService.MemberType.CORE, null);
    }

    private void registerForGroupEvents(GroupManagementService gms) {
        logger.log(Level.INFO, "Registering for group event notifications");
        gms.addActionFactory(new JoinNotificationActionFactoryImpl(this));
        gms.addActionFactory(new FailureSuspectedActionFactoryImpl(this));
        gms.addActionFactory(new FailureNotificationActionFactoryImpl(this));
        gms.addActionFactory(new PlannedShutdownActionFactoryImpl(this));
        gms.addActionFactory(new JoinedAndReadyNotificationActionFactoryImpl(this));
        gms.addActionFactory(new MessageActionFactoryImpl(this),"SimpleSampleComponent");
    }

    private void joinGMSGroup(String groupName, GroupManagementService gms) throws GMSException {
        logger.log(Level.INFO, "Joining Group "+groupName);
        gms.join();
    }

    private void sendMessages(GroupManagementService gms, String serverName, String groupName) throws InterruptedException, GMSException {
        logger.log(Level.INFO, "wait 5 secs to send 10 messages");
        synchronized(waitLock){
            waitLock.wait(10000);
        }
        GroupHandle gh = gms.getGroupHandle();

        logger.log(Level.INFO, "Sending messages...");
        for(int i = 0; i<=10; i++ ){
            gh.sendMessage("SimpleSampleComponent",
                    MessageFormat.format("Message {0}from server {1} to group {2}", i, serverName, groupName).getBytes());
            logger.info("Message " + i + " sent from " + serverName + " to Group " + groupName);
        }
    }

    private void waitForShutdown() throws InterruptedException {
        logger.log(Level.INFO, "wait 10 secs to shutdown");
        synchronized(waitLock){
            waitLock.wait(20000);
        }
    }

    private void leaveGroupAndShutdown(String serverName, GroupManagementService gms) {
        logger.log(Level.INFO, "Shutting down gms " + gms + "for server " + serverName);
        gms.shutdown(GMSConstants.shutdownType.INSTANCE_SHUTDOWN);
        //System.exit(0);
    }

    public void processNotification(Signal signal) {
        logger.log(Level.INFO, "Received Notification of type : "+signal.getClass().getName());
        try {
            signal.acquire();
            logger.log(Level.INFO,"Source Member: "+signal.getMemberToken() + " group : " + signal.getGroupName());
            if(signal instanceof MessageSignal){
                logger.log(Level.INFO,"Message: "+new String(((MessageSignal)signal).getMessage()));
            }
            signal.release();
        } catch (SignalAcquireException e) {
            logger.log(Level.WARNING, "Exception occured while acquiring signal"+e);
        } catch (SignalReleaseException e) {
            logger.log(Level.WARNING, "Exception occured while releasing signal"+e);
        }

    }
}

