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

package com.sun.enterprise.ee.cms.tests.p2pmessagesend;

/**
 * Created by IntelliJ IDEA.
 * User: sheetal
 * Date: Jan 14, 2008
 * Time: 2:56:41 PM
 * This test needs to be run on 2 terminals : one as a sender and the other as a receiver.
 * The sender sends 10 messages and the receiver receives them.
 * This test is to check the P2P functionality (with a new thread being spawned for
 * every message send operation) introduced for DSC messages and messages sent using
 * the groupHandle.
 */

import com.sun.enterprise.ee.cms.core.*;
import com.sun.enterprise.ee.cms.impl.client.*;
import org.glassfish.shoal.gms.common.GMSContextFactory;
import org.glassfish.shoal.gms.common.GMSContext;
import com.sun.enterprise.ee.cms.impl.base.Utility;

import java.text.MessageFormat;
import java.util.logging.Level;
import java.util.logging.Logger;

public class P2PMessageSendAndReceive implements CallBack {
    final static Logger logger = Logger.getLogger("P2PMessageSendAndReceive");
    final Object waitLock = new Object();
    private GMSContext ctx;
    final String group = "Group";


    public static void main(String[] args){
        Utility.setLogger(logger);
        Utility.setupLogHandler();
        P2PMessageSendAndReceive p2pMsgSendReceive = new P2PMessageSendAndReceive();
        try {
            if (System.getProperty("TYPE").equals("sender")) {
                p2pMsgSendReceive.send();
            }  else  if (System.getProperty("TYPE").equals("receiver")) {
                p2pMsgSendReceive.receive();
            }
        } catch (GMSException e) {
            logger.log(Level.SEVERE, "Exception occured while joining group:" + e);
        }
    }

      /**
     * sends messages
     * @throws com.sun.enterprise.ee.cms.core.GMSException
     */
    private void send() throws GMSException {
        logger.log(Level.INFO, "Starting P2PMessageSendAndReceive....");

        final String serverName = "C1";//"server"+System.currentTimeMillis();

        //initialize Group Management Service
        GroupManagementService gms = initializeGMS(serverName, group);


        //register for Group Events
        registerForGroupEvents(gms);

        //join group
        joinGMSGroup(group, gms);
        try {
            //send some messages
            sendMessages(gms, serverName, group);
            waitForShutdown();

        } catch (InterruptedException e) {
            logger.log(Level.WARNING, e.getMessage());
        }
        //leave the group gracefully
        leaveGroupAndShutdown(serverName, gms);
        System.exit(0);

    }

       /**
     * receives messages
     * @throws com.sun.enterprise.ee.cms.core.GMSException
     */
    private void receive() throws GMSException {
        logger.log(Level.INFO, "Starting P2PMessageSendAndReceive....");

        final String serverName = "C2";//"server"+System.currentTimeMillis();

        //initialize Group Management Service
        GroupManagementService gms = initializeGMS(serverName, group);


        //register for Group Events
        registerForGroupEvents(gms);

        //join group
        joinGMSGroup(group, gms);

        try {
        waitForShutdown();
    } catch (InterruptedException e) {
        logger.log(Level.WARNING, e.getMessage());
        }

        //leave the group gracefully
        leaveGroupAndShutdown(serverName, gms);
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
        logger.log(Level.INFO, "Sender : wait 10 secs to send 10 messages");
        synchronized(waitLock){
            waitLock.wait(10000);
        }
        GroupHandle gh = gms.getGroupHandle();

        logger.log(Level.INFO, "Sending messages...");
        for(int i = 0; i<=10; i++ ){
            gh.sendMessage("SimpleSampleComponent",
                    MessageFormat.format("P2PMsgSendReceive : message {0} from {1} to {2}", i, serverName, groupName).getBytes());
            logger.info("Message " + i + " sent from " + serverName + " to  " + groupName);
        }
    }

      private GMSContext getGMSContext() {
        if (ctx == null) {
            ctx = (GMSContext) GMSContextFactory.getGMSContext(group);
        }
        return ctx;
    }


    private void waitForShutdown() throws InterruptedException {
        logger.log(Level.INFO, "wait 30 secs to shutdown");
        synchronized(waitLock){
            waitLock.wait(30000);
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
