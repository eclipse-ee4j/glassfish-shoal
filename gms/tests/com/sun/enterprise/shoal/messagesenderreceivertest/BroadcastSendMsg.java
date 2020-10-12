/*
 * Copyright (c) 2010, 2018 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020 Payara Services Ltd.
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

package com.sun.enterprise.shoal.messagesenderreceivertest;

import com.sun.enterprise.ee.cms.core.*;
import com.sun.enterprise.ee.cms.impl.client.JoinNotificationActionFactoryImpl;
import com.sun.enterprise.ee.cms.impl.client.JoinedAndReadyNotificationActionFactoryImpl;
import com.sun.enterprise.ee.cms.impl.client.PlannedShutdownActionFactoryImpl;
import com.sun.enterprise.ee.cms.impl.client.MessageActionFactoryImpl;
import com.sun.enterprise.ee.cms.logging.GMSLogDomain;
import com.sun.enterprise.ee.cms.spi.MemberStates;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.GregorianCalendar;
import java.util.List;

import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class BroadcastSendMsg {

    private GroupManagementService gms = null;
    private static final Logger logger = GMSLogDomain.getLogger(GMSLogDomain.GMS_LOGGER);
    private static String groupName = "TestGroup";
    static ConcurrentHashMap<String, ConcurrentHashMap<Integer, String>> chm = new ConcurrentHashMap<String, ConcurrentHashMap<Integer, String>>();
    static AtomicBoolean completedCheck = new AtomicBoolean(false);
    static boolean msgIdReceived[];
    static String memberID = null;
    static int numberOfInstances = 0;
    static int payloadSize = 0;
    static int numberOfMessages = 0;
    static final String UDP = "UDP";
    static final String SYNCBROADCAST = "SYNCBROADCAST";
    static String sendMessageType = UDP;
    static AtomicInteger numOfStopMsgReceived = new AtomicInteger(0);
    static AtomicInteger numberOfPlannedShutdown = new AtomicInteger(0);
    static AtomicInteger numberOfJoinAndReady = new AtomicInteger(0);
    static List<String> waitingToReceiveStopFrom;
    static Calendar sendStartTime = null;
    static Calendar sendEndTime = null;
    static Calendar receiveStartTime = null;
    static Calendar receiveEndTime = null;
    static AtomicBoolean firstMsgReceived = new AtomicBoolean(false);
    static ConcurrentHashMap<String, String> numberOfPlannedShutdownReceived = new ConcurrentHashMap<String, String>();
    static int exceedTimeoutLimit = 9;

    public static void main(String[] args) {

        for (int z = 0; z < args.length; z++) {
            System.out.println(z + "=" + args[z]);
        }

        if (args[0].equalsIgnoreCase("-h")) {
            usage();
        }

        if (args[0].equalsIgnoreCase("master")) {
            if (args.length > 3) {
                usage();
            }

            memberID = args[0];
            System.out.println("memberID=" + memberID);
            groupName = args[1];
            System.out.println("groupName=" + groupName);
            numberOfInstances = Integer.parseInt(args[2]);
            System.out.println("numberOfInstances=" + numberOfInstances);
        } else if (args[0].contains("instance")) {
            if (args.length >= 5) {
                memberID = args[0];
                System.out.println("memberID=" + memberID);
                groupName = args[1];
                System.out.println("groupName=" + groupName);
                numberOfInstances = Integer.parseInt(args[2]);
                System.out.println("numberOfInstances=" + numberOfInstances);
                numberOfMessages = Integer.parseInt(args[3]);
                System.out.println("numberOfMessages=" + numberOfMessages);
                payloadSize = Integer.parseInt(args[4]);
                System.out.println("payloadSize=" + payloadSize);
                sendMessageType = args[5];
                System.out.println("sendMessageType=" + sendMessageType);
            } else {
                usage();
            }
        } else {
            usage();
        }

        BroadcastSendMsg sender = new BroadcastSendMsg();

        sender.test();
        sender.waitTillDone();

        // only do verification for INSTANCES
        if (!memberID.equalsIgnoreCase("master")) {
            System.out.println("Checking to see if correct number of messages (" + numberOfMessages + ")  were received from each instance");

            //System.out.println("chm.size()=" + chm.size());

            Enumeration e = chm.keys();
            while (e.hasMoreElements()) {
                int droppedMessages = 0;

                String key = (String) e.nextElement();
                ConcurrentHashMap instance_chm = chm.get(key);

                for (int i = 1; i <= numberOfMessages; i++) {
                    if (instance_chm.get(i) == null) {
                        droppedMessages++;
                        System.out.println("Never received msgId:" + i + ", from:" + key);
                    }
                }
                System.out.println("================================================================");

                if (droppedMessages == 0) {
                    System.out.println(key + ": PASS.  No dropped messages");
                } else {
                    System.out.println(key + ": FAILED. Confirmed (" + droppedMessages + ") messages were dropped from: " + key);
                }

            }
            long TimeDelta = 0;
            long remainder = 0;
            long msgPerSec = 0;
            if (sendEndTime != null && sendStartTime != null) {
                TimeDelta = (sendEndTime.getTimeInMillis() - sendStartTime.getTimeInMillis()) / 1000;
                remainder = (sendEndTime.getTimeInMillis() - sendStartTime.getTimeInMillis()) % 1000;
                msgPerSec = (numberOfMessages * numberOfInstances) / TimeDelta;
                System.out.println("Sending Messages Time data: Start[" + sendStartTime.getTime() + "], End[" + sendEndTime.getTime() + "], Delta[" + TimeDelta + "." + remainder + "] secs, MsgsPerSec[" + msgPerSec + "]");
            }
            if (receiveEndTime != null && receiveStartTime != null) {
                TimeDelta = (receiveEndTime.getTimeInMillis() - receiveStartTime.getTimeInMillis()) / 1000;
                remainder = (receiveEndTime.getTimeInMillis() - receiveStartTime.getTimeInMillis()) % 1000;
                msgPerSec = (numberOfMessages * numberOfInstances) / TimeDelta;
                System.out.println("Receiving Messages Time data: Start[" + receiveStartTime.getTime() + "], End[" + receiveEndTime.getTime() + "], Delta[" + TimeDelta + "." + remainder + "] secs, MsgsPerSec[" + msgPerSec + "]");
            }
        }


        System.out.println("================================================================");
        System.out.println("Testing Complete");

    }

    public static void usage() {

        System.out.println(" For master:");
        System.out.println("    <memberid(master)> <number_of_instances>");
        System.out.println(" For instances:");
        System.out.println("    <memberid(instancexxx)> <number_of_instances> <payloadsize> <number_of_messages> <sendMsgType(UDP|SYNCBROADCAST)>");
        System.exit(0);
    }

    private void test() {

        System.out.println("Testing Started");
        List<String> members;


        //initialize Group Management Service and register for Group Events

        System.out.println("Registering for group event notifications");
        if (memberID.equalsIgnoreCase("master")) {
            gms = initializeGMS(memberID, groupName, GroupManagementService.MemberType.SPECTATOR);
            gms.addActionFactory(new JoinNotificationActionFactoryImpl(new JoinNotificationCallBack(memberID)));
            gms.addActionFactory(new JoinedAndReadyNotificationActionFactoryImpl(new JoinAndReadyNotificationCallBack(memberID)));
            gms.addActionFactory(new PlannedShutdownActionFactoryImpl(new PlannedShutdownCallBack(memberID)));

        } else {
            gms = initializeGMS(memberID, groupName, GroupManagementService.MemberType.CORE);
            gms.addActionFactory(new JoinNotificationActionFactoryImpl(new JoinNotificationCallBack(memberID)));
            gms.addActionFactory(new JoinedAndReadyNotificationActionFactoryImpl(new JoinAndReadyNotificationCallBack(memberID)));
            gms.addActionFactory(new MessageActionFactoryImpl(new MessageCallBack(memberID, numberOfMessages)), "TestComponent");
        }


        System.out.println("Joining Group " + groupName);
        try {
            gms.join();
            sleep(5);
            gms.reportJoinedAndReadyState();
            sleep(5);

            if (memberID.equalsIgnoreCase("master")) {
                System.out.println("===================================================");
                System.out.println("Waiting for JOINEDANDREADY from all CORE members");
                while (true) {
                    sleep(5);
                    System.out.println("===================================================");
                    System.out.println("Number of JOINEDANDREADY received from all CORE members is " + numberOfJoinAndReady);
                    // if we received the correct number of joinedandready messages or if the all core members are aliveandready
                    if ((numberOfJoinAndReady.get() == numberOfInstances) || (areAllMembersInState(gms,MemberStates.ALIVEANDREADY))) {
                        System.out.println("===================================================");
                        System.out.println("All CORE members have sent JOINEDANDREADY (" + numberOfJoinAndReady + "," + numberOfInstances + ")");
                        System.out.println("===================================================");
                        break;
                    }

                }
            } else {

                System.out.println("Waiting for all members to joined the group:" + groupName);

                while (true) {
                    sleep(2);
                    System.out.println("numberOfJoinAndReady=" + numberOfJoinAndReady.get());
                    System.out.println("numberOfInstances=" + numberOfInstances);

                    if ((numberOfJoinAndReady.get() == numberOfInstances) || (areAllMembersInState(gms,MemberStates.ALIVEANDREADY))) {
                        members = gms.getGroupHandle().getCurrentCoreMembers();
                        System.out.println("===================================================");
                        System.out.println("All members have joined the group:" + groupName);
                        System.out.println("Members are:" + members.toString());
                        System.out.println("===================================================");

                        members.remove(memberID);
                        waitingToReceiveStopFrom = new ArrayList<String>();
                        for (String instanceName : members) {
                            waitingToReceiveStopFrom.add(instanceName);
                        }
                        System.out.println("waitingToReceiveStopFrom=" + waitingToReceiveStopFrom.toString());
                        break;
                    }

                }

                System.out.println("Sending Messages to the following members=" + members.toString());

                String nullString = null;
                sendStartTime = new GregorianCalendar();
                for (int i = 1; i <= numberOfMessages; i++) {
                    StringBuilder sb = new StringBuilder(payloadSize);
                    sb.append("[from:").append(memberID).append(":").append(i).append("]");
                    for (int k = 0; k < payloadSize; k++) {
                        sb.append("X");
                    }
                    //System.out.println("Sending Message:" + sb.toString().substring(0, 25));
                    try {
                        if (sendMessageType.equalsIgnoreCase(UDP)) {
                            gms.getGroupHandle().sendMessage(nullString, "TestComponent", sb.toString().getBytes());
                        } else {
                            gms.getGroupHandle().sendMessage("TestComponent", sb.toString().getBytes());
                        }
                        sleep(50,"milliseconds");
                    } catch (Exception e) {
                        System.out.println("Exception occurred sending msgID" + i + ":" + e);
                    }
                    sendEndTime = new GregorianCalendar();
                }
                for (String instanceName : members) {
                    if (!instanceName.equalsIgnoreCase(memberID)) {
                        System.out.println("Sending STOP message to " + instanceName + "!!!!!!!!!!!!!!!");
                        gms.getGroupHandle().sendMessage(instanceName, "TestComponent", ("[from:" + memberID + ":0]STOP").getBytes());
                    }
                }
            }

        } catch (GMSException e) {
            System.err.println("Exception occurred during testing for member: " + memberID + "terminated :" + e);

        }

    }

    public void waitTillDone() {
        if (memberID.equalsIgnoreCase("master")) {
            System.out.println("===================================================");
            System.out.println("Waiting for all CORE members to send PLANNEDSHUTDOWN");
            while (true) {

                sleep(10);


                System.out.println("===================================================");
                System.out.println("Number of PLANNEDSHUTDOWN received from all CORE members is " + numberOfPlannedShutdown.get());
                if (numberOfPlannedShutdown.get() == numberOfInstances) {
                    System.out.println("===================================================");
                    System.out.println("Have received PLANNEDSHUTDOWN from all CORE members (" + numberOfPlannedShutdown.get() + ")");
                    System.out.println("===================================================");
                    break;
                } else if (gms.getGroupHandle().getCurrentCoreMembers().size() == 0) {
                    System.out.println("===================================================");
                    System.out.println("Missed a PLANNEDSHUTDOWN. All core members have stopped. Only received PLANNEDSHUTDOWN from CORE members (" + numberOfPlannedShutdown.get() + ")");
                    System.out.println("===================================================");
                    break;
                }

            }
        } else {
            // instance
            long waitForStartTime = System.currentTimeMillis();
            while (!completedCheck.get() && (gms.getGroupHandle().getCurrentCoreMembers().size() > 1)) {
                int waitTime = 10000; // 10 seconds

                System.out.println("Waiting " + (waitTime / 1000) + " seconds inorder to complete processing of expected incoming messages...");
                System.out.println("members=" + gms.getGroupHandle().getCurrentCoreMembers().toString());


                synchronized (completedCheck) {
                    try {
                        completedCheck.wait(waitTime);
                    } catch (InterruptedException ie) {
                    }
                }
                long currentTime = System.currentTimeMillis();
                long exceedTimeout = ((currentTime - waitForStartTime) / 60000);
                System.out.println("exceeding timeout=" + exceedTimeout);
                if (exceedTimeout > 4) {
                    logger.log(Level.SEVERE, "EXCEEDED 5 minute timeout waiting to receive STOP message");
                    break;
                }

            }
            System.out.println("Completed processing of incoming messages");

            sleep(1);

        }
        leaveGroupAndShutdown(memberID, gms);

    }

    private GroupManagementService initializeGMS(String memberID, String groupName, GroupManagementService.MemberType mType) {
        System.out.println("Initializing Shoal for member: " + memberID + " group:" + groupName);
        return (GroupManagementService) GMSFactory.startGMSModule(
                memberID,
                groupName,
                mType,
                new Properties());

    }

    private void leaveGroupAndShutdown(String memberID, GroupManagementService gms) {
        System.out.println("Shutting down gms " + gms + "for member: " + memberID);
        gms.shutdown(GMSConstants.shutdownType.INSTANCE_SHUTDOWN);
    }

    private class JoinAndReadyNotificationCallBack implements CallBack {

        private String memberID;

        public JoinAndReadyNotificationCallBack(String memberID) {
            this.memberID = memberID;
        }

        public void processNotification(Signal notification) {
            System.out.println("***JoinAndReadyNotification received from: " + notification.getMemberToken());
            if (!(notification instanceof JoinedAndReadyNotificationSignal)) {
                logger.log(Level.SEVERE, "received unknown notification type:" + notification + " from:" + notification.getMemberToken());
            } else {
                synchronized (this) {
                    // determine how many core members are ready to begin testing
                    JoinedAndReadyNotificationSignal readySignal = (JoinedAndReadyNotificationSignal) notification;
                    List<String> currentCoreMembers = readySignal.getCurrentCoreMembers();
                    numberOfJoinAndReady.set(0);
                    for (String instanceName : currentCoreMembers) {
                        MemberStates state = gms.getGroupHandle().getMemberState(instanceName, 6000, 3000);
                        switch (state) {
                            case READY:
                            case ALIVEANDREADY:
                                numberOfJoinAndReady.getAndIncrement();
                            default:
                        }
                    }

                    System.out.println("numberOfJoinAndReady received so far is: " + numberOfJoinAndReady.get());
                }

            }
        }
    }

    private class JoinNotificationCallBack implements CallBack {
        //
        //
        // this callback should only be registered for core102
        //
        //

        private String memberID;

        public JoinNotificationCallBack(String memberID) {
            this.memberID = memberID;
        }

        public void processNotification(Signal notification) {
            System.out.println("***JoinNotification received from: " + notification.getMemberToken());
            if (!(notification instanceof JoinNotificationSignal)) {
                System.err.println("received unknown notification type:" + notification + " from:" + notification.getMemberToken());
            } else {
                System.out.println(memberID + " setting state to JoinedAndReady");

            }
        }
    }

    private class PlannedShutdownCallBack implements CallBack {

        private String memberID;

        public PlannedShutdownCallBack(String memberID) {
            this.memberID = memberID;
        }

        public void processNotification(Signal notification) {
            System.out.println("***PlannedShutdownNotification received from: " + notification.getMemberToken());
            if (!(notification instanceof PlannedShutdownSignal)) {
                logger.log(Level.SEVERE, "received unknown notification type:" + notification + " from:" + notification.getMemberToken());
            } else {
                synchronized (this) {
                    if (!notification.getMemberToken().equals("master")) {
                        numberOfPlannedShutdown.incrementAndGet();
                        System.out.println("Number of PLANNEDSHUTDOWN msgs received so far is:" + numberOfPlannedShutdown.get());
                    }
                }
            }

        }
    }

    private class MessageCallBack implements CallBack {

        private String memberID;
        private int numberOfMsgs;

        public MessageCallBack(String memberID, int numberOfMsgs) {
            this.memberID = memberID;
            this.numberOfMsgs = numberOfMsgs;
        }

        public void processNotification(Signal notification) {
            // System.out.println("***Message received from: " + notification.getMemberToken());
            if (!(notification instanceof MessageSignal)) {
                logger.log(Level.SEVERE, "received unknown notification type:" + notification + " from:" + notification.getMemberToken());
            } else {
                synchronized (this) {
                    if (!firstMsgReceived.get()) {
                        firstMsgReceived.set(true);
                        receiveStartTime = new GregorianCalendar();
                    }
                    try {
                        MessageSignal messageSignal = (MessageSignal) notification;

                        String msgString = new String(messageSignal.getMessage());
                        String shortPayLoad = msgString;
                        if (msgString.length() > 25) {
                            shortPayLoad = shortPayLoad.substring(0, 25) + "...";
                        }
                        //System.out.println(memberID + " Received msg: " + shortPayLoad);
                        int startBracketIndex = msgString.indexOf("[");
                        int endBracketIndex = msgString.indexOf("]");
                        int firstColonIndex = msgString.indexOf(":");
                        int secondColonIndex = msgString.indexOf(":", firstColonIndex + 1);
                        String from = msgString.substring(firstColonIndex + 1, secondColonIndex);
                        int msgIdInt = Integer.parseInt(msgString.substring(secondColonIndex + 1, endBracketIndex));
                        if (msgString.contains("STOP")) {
                            System.out.println("Received STOP message from " + from + " !!!!!!!!");
                            numOfStopMsgReceived.incrementAndGet();
                            if ((numOfStopMsgReceived.get() == numberOfInstances - 1)) {
                                receiveEndTime = new GregorianCalendar();
                                completedCheck.set(true);
                            }
                            waitingToReceiveStopFrom.remove(from);
                            System.out.println("Total number of STOP messages received so far is: " + numOfStopMsgReceived.get());
                            if (waitingToReceiveStopFrom.size() > 1) {
                                System.out.println("Waiting to receive STOP from: " + waitingToReceiveStopFrom.toString());
                            }
                        } else {
                            // if the INSTANCE does not exist in the map, create it.
                            ConcurrentHashMap<Integer, String> instance_chm = chm.get(from);

                            if (instance_chm == null) {
                                instance_chm = new ConcurrentHashMap<Integer, String>();
                            }
                            instance_chm.put(msgIdInt, "");

                            //System.out.println(msgFrom + ":instance_chm.size()=" + instance_chm.size());

                            chm.put(from, instance_chm);
                            //System.out.println("chm.size()=" + chm.size());

                        }
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                }
            }
        }
    }

    public static void sleep(long i, String units) {
        try {
            if (units.equalsIgnoreCase("seconds")) {
                Thread.sleep(i * 1000);
            } else {
                Thread.sleep(i);
            }
        } catch (InterruptedException ex) {
        }
    }

    public static void sleep(long i) {
        sleep(i,"seconds");
    }
    public static boolean areAllMembersInState(GroupManagementService gms, Enum state) {
        boolean result=true;
        List<String> members = gms.getGroupHandle().getCurrentCoreMembers();
        for (String member:members){
            Enum memberState = gms.getGroupHandle().getMemberState(member);
            if (!memberState.equals(state)) {
                result=false;
            }
        }
        return result;
    }
}

