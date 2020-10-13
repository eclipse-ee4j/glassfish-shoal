/*
 * Copyright (c) 1997, 2018 Oracle and/or its affiliates. All rights reserved.
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

import com.sun.enterprise.ee.cms.impl.base.Utility;
import com.sun.enterprise.ee.cms.core.*;
import com.sun.enterprise.ee.cms.impl.client.JoinedAndReadyNotificationActionFactoryImpl;
import com.sun.enterprise.ee.cms.impl.client.PlannedShutdownActionFactoryImpl;
import com.sun.enterprise.ee.cms.impl.client.MessageActionFactoryImpl;
import com.sun.enterprise.ee.cms.logging.GMSLogDomain;
import com.sun.enterprise.ee.cms.spi.MemberStates;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;

import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class HAMessagingSimulator {

    private GroupManagementService gms = null;
    private static final Logger gmsLogger = GMSLogDomain.getLogger(GMSLogDomain.GMS_LOGGER);
    // private static final Logger gmsLogger = HASimulatorLogger.getLogger(HASimulatorLogger.HAMSGSimulator_LOGGER);
    private final String group = "TestGroup";
    static ConcurrentHashMap<Integer, ConcurrentHashMap<Long, String>> chm_Payloads = new ConcurrentHashMap<Integer, ConcurrentHashMap<Long, String>>();
    static ConcurrentHashMap<Integer, ConcurrentHashMap<String, String>> chm_MsgIDs = new ConcurrentHashMap<Integer, ConcurrentHashMap<String, String>>();
    static AtomicBoolean completedCheck = new AtomicBoolean(false);
    static boolean msgIdReceived[];
    static String memberID = null;
    static Integer memberIDNum = new Integer(0);
    static int numberOfInstances = 0;
    static int payloadSize = 0;
    static int numberOfObjects = 0;
    static int numberOfMsgsPerObject = 0;
    //static AtomicInteger numberOfPlannedShutdownReceived = new AtomicInteger(0);
    static ConcurrentHashMap<String, String> numberOfPlannedShutdownReceived = new ConcurrentHashMap<String, String>();
    static AtomicInteger numberOfJoinAndReady = new AtomicInteger(0);
    static List<Integer> receivedStopFrom = new ArrayList<Integer>();
    static Calendar sendStartTime = null;
    static Calendar sendEndTime = null;
    static Calendar receiveStartTime = null;
    static Calendar receiveEndTime = null;
    static AtomicBoolean firstMsgReceived = new AtomicBoolean(false);
    static List<String> members;
    static List<Integer> memberIDs;
    static int exceedTimeoutLimit = 9;

    public static void main(String[] args) {

        //for (int z = 0; z < args.length; z++) {
        //    gmsLogger.log(Level.INFO,(z + "=" + args[z]);
        //}

        if (args[0].equalsIgnoreCase("-h")) {
            usage();
        }

        if (args[0].equalsIgnoreCase("server")) {
            if (args.length != 2) {
                usage();
            }
            memberID = args[0];
            gmsLogger.log(Level.INFO, ("memberID=" + memberID));
            numberOfInstances = Integer.parseInt(args[1]);
            gmsLogger.log(Level.INFO, ("numberOfInstances=" + numberOfInstances));
        } else if (args[0].contains("instance")) {
            if (args.length >= 4) {
                memberID = args[0];
                if (!memberID.startsWith("instance")) {
                    System.err.println("ERROR: The member name must be in the format 'instancexxx'");
                    System.exit(1);
                }
                gmsLogger.log(Level.INFO, ("memberID=" + memberID));
                memberIDNum = new Integer(memberID.replace("instance", ""));
                numberOfInstances = Integer.parseInt(args[1]);
                gmsLogger.log(Level.INFO, ("numberOfInstances=" + numberOfInstances));
                numberOfObjects = Integer.parseInt(args[2]);
                gmsLogger.log(Level.INFO, ("numberOfObjects=" + numberOfObjects));
                numberOfMsgsPerObject = Integer.parseInt(args[3]);
                gmsLogger.log(Level.INFO, ("numberOfMsgsPerObject=" + numberOfMsgsPerObject));
                payloadSize = Integer.parseInt(args[4]);
                gmsLogger.log(Level.INFO, ("payloadSize=" + payloadSize));
            } else {
                usage();
            }
        } else {
            usage();
        }
        Utility.setLogger(gmsLogger);
        Utility.setupLogHandler();



        HAMessagingSimulator sender = new HAMessagingSimulator();

        sender.test();

        sender.waitTillDone();

        // only do verification for INSTANCES
        if (!memberID.equalsIgnoreCase("server")) {
            System.out.println("Checking to see if correct number of messages (Objects:" + numberOfObjects + ", NumberOfMsg:" + numberOfMsgsPerObject + " = [" + (numberOfObjects * numberOfMsgsPerObject) + "])  were received from each instance");

            //gmsLogger.log(Level.INFO,("chm.size()=" + chm.size());
            System.out.println("================================================================");


            //  Enumeration e = chm_MsgIDs.keys();
            // while (e.hasMoreElements()) {

            for (Integer instanceNum : memberIDs) {
                int droppedMessages = 0;
                int payloadErrors = 0;

                ConcurrentHashMap<String, String> msgIDs = chm_MsgIDs.get(instanceNum);
                //System.out.println("msgIDs=" + msgIDs.toString());
                String key = null;
                for (long objectNum = 1; objectNum <= numberOfObjects; objectNum++) {
                    for (long msgId = 1; msgId <= numberOfMsgsPerObject; msgId++) {
                        key = objectNum + ":" + msgId;
                        if (!msgIDs.containsKey(key)) {
                            droppedMessages++;
                            System.out.println("Never received objectId:" + objectNum + " msgId:" + msgId + ", from:" + instanceNum);
                        }
                    }
                }
                System.out.println("---------------------------------------------------------------");

                if (droppedMessages == 0) {
                    System.out.println(instanceNum + ": PASS.  No dropped messages");
                } else {
                    System.out.println(instanceNum + ": FAILED. Confirmed (" + droppedMessages + ") messages were dropped");
                }
                System.out.println("================================================================");

                /* header format is:
                 *  long objectID
                long msgID
                int to
                int from
                 */
                int tmpSize = payloadSize - (8 + 8 + 4 + 4);
                String expectedPayload = new String(createPayload(tmpSize));
                //gmsLogger.log(Level.INFO,("expected Payload" + expectedPayload);

                ConcurrentHashMap<Long, String> payLoads = chm_Payloads.get(instanceNum);

                for (long objectNum = 1; objectNum <= numberOfObjects; objectNum++) {
                    if (!payLoads.containsKey(objectNum)) {
                        payloadErrors++;
                        System.out.println("INTERNAL ERROR: objectId:" + objectNum + " from:" + instanceNum + " missing from payload structure");
                    } else {
                        String payLoad = payLoads.get(objectNum);
                        if (!payLoad.equals(expectedPayload)) {
                            System.out.println("actual Payload[objectNum]:" + payLoad);
                            payloadErrors++;
                            System.out.println("Payload did not match for objectId:" + objectNum + ", from:" + instanceNum);
                        }
                    }


                }
                System.out.println("---------------------------------------------------------------");

                if (payloadErrors == 0) {
                    System.out.println(instanceNum + ": PASS.  No payload errors");
                } else {
                    System.out.println(instanceNum + ": FAILED. Confirmed (" + payloadErrors + ") payload errors");
                }
                System.out.println("================================================================");


            }
            long timeDelta = 0;
            long remainder = 0;
            long msgPerSec = 0;
            if (sendEndTime != null && sendStartTime != null) {
                timeDelta = (sendEndTime.getTimeInMillis() - sendStartTime.getTimeInMillis()) / 1000;
                remainder = (sendEndTime.getTimeInMillis() - sendStartTime.getTimeInMillis()) % 1000;
                if (timeDelta == 0) {
                    msgPerSec = 0;
                } else {
                    msgPerSec = (numberOfObjects * numberOfMsgsPerObject * numberOfInstances) / timeDelta;
                }
                System.out.println("Sending Messages Time data: Start[" + sendStartTime.getTime() + "], End[" + sendEndTime.getTime() + "], Delta[" + timeDelta + "." + remainder + "] secs, MsgsPerSec[" + msgPerSec + "]");
            }
            if (receiveEndTime != null && receiveStartTime != null) {
                timeDelta = (receiveEndTime.getTimeInMillis() - receiveStartTime.getTimeInMillis()) / 1000;
                remainder = (receiveEndTime.getTimeInMillis() - receiveStartTime.getTimeInMillis()) % 1000;
                if (timeDelta == 0) {
                    msgPerSec = 0;
                } else {
                    msgPerSec = (numberOfObjects * numberOfMsgsPerObject * numberOfInstances) / timeDelta;
                }
                System.out.println("Receiving Messages Time data: Start[" + receiveStartTime.getTime() + "], End[" + receiveEndTime.getTime() + "], Delta[" + timeDelta + "." + remainder + "] secs, MsgsPerSec[" + msgPerSec + "]");
            }
        }


        System.out.println("================================================================");
        System.out.println("Testing Complete");

    }

    public static void usage() {

        System.out.println(" For server:");
        System.out.println("    <memberid(server)> <number_of_instances>");
        System.out.println(" For instances:");
        System.out.println("    <memberid(instancexxx)> <number_of_instances> <number_of_objects> <number_of_msgs_per_object> <payloadsize>");
        System.exit(0);
    }

    private void test() {

        System.out.println("Testing Started");



        //initialize Group Management Service and register for Group Events

        gmsLogger.log(Level.INFO, "Registering for group event notifications");
        if (memberID.equalsIgnoreCase("server")) {
            gms = initializeGMS(memberID, group, GroupManagementService.MemberType.SPECTATOR);
            gms.addActionFactory(new JoinedAndReadyNotificationActionFactoryImpl(new JoinAndReadyNotificationCallBack(memberID)));
            gms.addActionFactory(new PlannedShutdownActionFactoryImpl(new PlannedShutdownCallBack(memberID)));

        } else {
            gms = initializeGMS(memberID, group, GroupManagementService.MemberType.CORE);
            gms.addActionFactory(new JoinedAndReadyNotificationActionFactoryImpl(new JoinAndReadyNotificationCallBack(memberID)));
            gms.addActionFactory(new MessageActionFactoryImpl(new MessageCallBack(memberID, numberOfObjects)), "TestComponent");
        }


        gmsLogger.log(Level.INFO, "Joining Group " + group);
        try {
            gms.join();
        } catch (GMSException e) {
            gmsLogger.log(Level.SEVERE, "Exception occured :" + e, e);
            System.exit(1);
        }

        try {
            Thread.sleep(5000);
        } catch (InterruptedException ex) {
        }


        gms.reportJoinedAndReadyState();

        gmsLogger.log(Level.INFO, "Waiting for all members to joined the group:" + group);

        if (!memberID.equalsIgnoreCase("server")) {
            gmsLogger.log(Level.INFO, "Waiting for all members to report joined and ready for the group:" + group);

            while (true) {

                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                }

                gmsLogger.log(Level.INFO, ("numberOfJoinAndReady=" + numberOfJoinAndReady.get()));
                gmsLogger.log(Level.INFO, ("numberOfInstances=" + numberOfInstances));

                if (numberOfJoinAndReady.get() == numberOfInstances) {
                    members = gms.getGroupHandle().getCurrentCoreMembers();
                    System.out.println("===================================================");
                    System.out.println("All members are joined and ready in the group:" + group);
                    System.out.println("Members are:" + members.toString());
                    System.out.println("===================================================");
                    members.remove(memberID);
                    memberIDs = new ArrayList<Integer>();
                    for (String instanceName : members) {
                        Integer instanceNum = new Integer(instanceName.replace("instance", ""));
                        memberIDs.add(instanceNum);
                    }
                    break;
                }

            }
            gmsLogger.log(Level.INFO, "Starting Testing");

            gmsLogger.log(Level.INFO, "Sending Messages to the following members=" + members.toString());

            byte[] msg = new byte[1];

            sendStartTime = new GregorianCalendar();
            long summaryCount = numberOfMsgsPerObject;
            boolean displaySummary = true;

            for (long objectNum = 1; objectNum <= numberOfObjects; objectNum++) {
                displaySummary = true;
                for (Integer instanceNum : memberIDs) {
                    for (long msgNum = 1; msgNum <= numberOfMsgsPerObject; msgNum++) {
                        if (!instanceNum.equals(memberID)) {
                            // create the message to be sent
                            msg = createMsg(objectNum, msgNum, instanceNum, memberIDNum, payloadSize);

                            gmsLogger.log(Level.FINE, "Sending Message:" + displayMsg(msg));
                            //System.out.println("Sending Message:" + displayMsg(msg));

                            try {
                                gms.getGroupHandle().sendMessage("instance" + instanceNum, "TestComponent", msg);
                            } catch (GMSException ge) {
                                if (!ge.getMessage().contains("Client is busy or timed out")) {
                                    gmsLogger.log(Level.WARNING, "Exception occured sending message (object:" + objectNum + ",MsgID:" + msgNum + " to :instance" + instanceNum + "):" + ge, ge);
                                } else {
                                    for (int i = 1; i <= 3; i++) {
                                        try {
                                            try {
                                                Thread.sleep(10);
                                            } catch (InterruptedException iex) {
                                            }
                                            gms.getGroupHandle().sendMessage("instance" + instanceNum, "TestComponent", msg);
                                        } catch (GMSException ge1) {
                                            gmsLogger.log(Level.FINE, "\n-----------------------------\nException occured during send message retry (" + i + ") for (object:" + objectNum + ",MsgID:" + msgNum + " to :instance" + instanceNum + "):" + ge1, ge1);
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (displaySummary) {
                        gmsLogger.log(Level.INFO, "Message Sent Summary:" + summaryCount + " messages have been sent.");
                        displaySummary = false;
                    }
                    // think time
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException iex) {
                    }
                }
                summaryCount += numberOfMsgsPerObject;
            }
            gmsLogger.log(Level.INFO, "Finished Sending Messages, Now sending STOP");

            for (Integer instanceNum : memberIDs) {
                msg = createMsg(0, 0, instanceNum, memberIDNum, -1);
                //TestMessage msg = new TestMessage(0, 0, instanceName, memberID, "STOP");
                System.out.println("Sending STOP message to " + displayMsg(msg) + "!!!!!!!!!!!!!!!");
                try {
                    gms.getGroupHandle().sendMessage("instance" + instanceNum, "TestComponent", msg);
                } catch (GMSException e) {
                    gmsLogger.log(Level.WARNING, "Exception occured sending STOP message to :instance" + instanceNum + e, e);
                }
            }
            sendEndTime = new GregorianCalendar();
        }

    }

    public void waitTillDone() {
        long waitForStartTime = 0;
        long exceedTimeout = 0;
        boolean firstTime = true;
        long currentTime = 0;
        if (memberID.equalsIgnoreCase("server")) {
            gmsLogger.log(Level.INFO, "===================================================");
            gmsLogger.log(Level.INFO, "Waiting for all CORE members to send PLANNEDSHUTDOWN");
            while (true) {

                try {
                    Thread.sleep(20000); // 20 seconds
                } catch (InterruptedException e) {
                }

                gmsLogger.log(Level.INFO, "===================================================");
                gmsLogger.log(Level.INFO, "CORE members who have sent PLANNEDSHUTDOWN are: (" + numberOfPlannedShutdownReceived.size() + ") " + numberOfPlannedShutdownReceived.toString());
                if (numberOfPlannedShutdownReceived.size() == numberOfInstances) {
                    gmsLogger.log(Level.INFO, "===================================================");
                    gmsLogger.log(Level.INFO, "PLANNEDSHUTDOWN received from all CORE members");
                    gmsLogger.log(Level.INFO, "===================================================");
                    break;
                } else {
                    int size = gms.getGroupHandle().getCurrentCoreMembers().size();
                    gmsLogger.log(Level.INFO, "Number of CORE members that still exist are:" + size);
                    if (size == 0) {
                        gmsLogger.log(Level.INFO, "===================================================");
                        gmsLogger.log(Level.INFO, "Missed a PLANNEDSHUTDOWN. All core members have stopped. Only received PLANNEDSHUTDOWN from CORE members : (" + numberOfPlannedShutdownReceived.size() + ") " + numberOfPlannedShutdownReceived.toString());
                        gmsLogger.log(Level.INFO, "===================================================");
                        break;
                    }

                    // set a timeout of 5 minutes to wait once the first instance reports plannedshutdown
                    if (numberOfPlannedShutdownReceived.size() > 1) {

                        if (firstTime) {
                            waitForStartTime = System.currentTimeMillis();
                            firstTime = false;
                        }
                        currentTime = System.currentTimeMillis();
                        exceedTimeout = ((currentTime - waitForStartTime) / 60000);
                        gmsLogger.log(Level.INFO, ("exceed timeout=" + exceedTimeout));
                        if (exceedTimeout > exceedTimeoutLimit + 1) {
                            gmsLogger.log(Level.SEVERE, memberID + " EXCEEDED " + (exceedTimeoutLimit + 2) + " minute timeout waiting to receive all PLANNEDSHUTDOWN messages");
                            break;
                        }
                    }
                }


            }
        } else {
            // instance
            while (!completedCheck.get() && (gms.getGroupHandle().getCurrentCoreMembers().size() > 1)) {
                long waitTime = 20000; // 20 seconds

                System.out.println("Waiting " + waitTime / 1000 + " seconds in order to complete processing of expected incoming messages...");
                gmsLogger.log(Level.INFO, ("members=" + gms.getGroupHandle().getCurrentCoreMembers().toString()));


                synchronized (completedCheck) {
                    try {
                        completedCheck.wait(waitTime);
                    } catch (InterruptedException ie) {
                    }
                }

                /*
                currentTime = System.currentTimeMillis();
                exceedTimeout = ((currentTime - waitForStartTime) / 60000);
                gmsLogger.log(Level.INFO, ("exceed timeout=" + exceedTimeout));
                if (exceedTimeout > 1) {
                gmsLogger.log(Level.SEVERE, memberID + " EXCEEDED 2 minute timeout waiting to receive STOP message");
                break;
                }
                 */

                // set a timeout of 10 minutes to wait once the first instance sends STOP
                if (receivedStopFrom.size() > 1) {

                    if (firstTime) {
                        waitForStartTime = System.currentTimeMillis();
                        firstTime = false;
                    }
                    currentTime = System.currentTimeMillis();
                    exceedTimeout = ((currentTime - waitForStartTime) / 60000);
                    gmsLogger.log(Level.INFO, ("exceed timeout=" + exceedTimeout));
                    if (exceedTimeout > exceedTimeoutLimit) {
                        gmsLogger.log(Level.SEVERE, memberID + " EXCEEDED " + (exceedTimeoutLimit + 1) + " minute timeout waiting to receive all STOP messages");
                        break;
                    }
                }


            }
            gmsLogger.log(Level.INFO, ("Completed processing of incoming messages"));
            /*try {
            Thread.sleep(1000);
            } catch (Throwable t) {
            }
             * */

        }
        leaveGroupAndShutdown(memberID, gms);
    }

    private GroupManagementService initializeGMS(String memberID, String groupName, GroupManagementService.MemberType mType) {
        gmsLogger.log(Level.INFO, "Initializing Shoal for member: " + memberID + " group:" + groupName);
        return (GroupManagementService) GMSFactory.startGMSModule(
                memberID,
                groupName,
                mType,
                new Properties());

    }

    private void leaveGroupAndShutdown(String memberID, GroupManagementService gms) {
        gmsLogger.log(Level.INFO, "Shutting down gms " + gms + "for member: " + memberID);
        gms.shutdown(GMSConstants.shutdownType.INSTANCE_SHUTDOWN);
    }

    private class JoinAndReadyNotificationCallBack implements CallBack {

        private String memberID;

        public JoinAndReadyNotificationCallBack(String memberID) {
            this.memberID = memberID;
        }

        public void processNotification(Signal notification) {
            gmsLogger.log(Level.INFO, "***JoinAndReadyNotification received from: " + notification.getMemberToken());
            if (!(notification instanceof JoinedAndReadyNotificationSignal)) {
                gmsLogger.log(Level.SEVERE, "received unknown notification type:" + notification + " from:" + notification.getMemberToken());
            } else {
                if (!notification.getMemberToken().equals("server")) {

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

                    gmsLogger.log(Level.INFO, "numberOfJoinAndReady received so far is: " + numberOfJoinAndReady.get());
                }
            }
        }
    }

    private class PlannedShutdownCallBack implements CallBack {

        private String memberID;

        public PlannedShutdownCallBack(String memberID) {
            this.memberID = memberID;
        }

        public void processNotification(Signal notification) {
            String member = notification.getMemberToken();
            gmsLogger.log(Level.INFO, "***PlannedShutdownNotification received from: " + member);
            if (!(notification instanceof PlannedShutdownSignal)) {
                gmsLogger.log(Level.SEVERE, "received unknown notification type:" + notification + " from:" + member);
            } else {
                if (!notification.getMemberToken().equals("server")) {
                    numberOfPlannedShutdownReceived.put(member, "");
                    gmsLogger.log(Level.INFO, "CORE members who we have received PLANNEDSHUTDOWN from are: (" + numberOfPlannedShutdownReceived.size() + ") " + numberOfPlannedShutdownReceived.toString());
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
            // gmsLogger.log(Level.INFO, "***Message received from: " + notification.getMemberToken());
            if (!(notification instanceof MessageSignal)) {
                gmsLogger.log(Level.SEVERE, "received unknown notification type:" + notification + " from:" + notification.getMemberToken());
            } else {
                if (!firstMsgReceived.get()) {
                    firstMsgReceived.set(true);
                    receiveStartTime = new GregorianCalendar();
                }
                try {
                    MessageSignal messageSignal = (MessageSignal) notification;

                    final byte[] msg = messageSignal.getMessage();

                    ByteBuffer buf = ByteBuffer.wrap(msg);

                    long objectID = buf.getLong(0);
                    long msgID = buf.getLong(8);
                    int to = buf.getInt(16);
                    int from = buf.getInt(20);
                    String payload = new String(msg, 24, msg.length - 24);

                    String shortPayLoad = payload;
                    if (shortPayLoad.length() > 10) {
                        shortPayLoad = shortPayLoad.substring(0, 10) + "..." + shortPayLoad.substring(shortPayLoad.length() - 10, shortPayLoad.length());
                    }
                    gmsLogger.log(Level.FINE, memberID + " Received msg:" + displayMsg(msg));
                    //System.out.println(" Received msg:" + displayMsg(msg));
                    if (msgID > 0) {


                        // keep track of the objectIDs
                        // if the INSTANCE does not exist in the map, create it.
                        ConcurrentHashMap<Long, String> object_chm = chm_Payloads.get(from);
                        if (object_chm == null) {
                            object_chm = new ConcurrentHashMap<Long, String>();
                        }
                        object_chm.put(objectID, payload);
                        chm_Payloads.put(from, object_chm);


                        // keep track of the msgIDs
                        // if the INSTANCE does not exist in the map, create it.
                        ConcurrentHashMap<String, String> object_MsgIDs = chm_MsgIDs.get(from);
                        if (object_MsgIDs == null) {
                            object_MsgIDs = new ConcurrentHashMap<String, String>();
                        }
//gmsLogger.log(Level.INFO,("saving msgid"+msgID);
//gmsLogger.log(Level.INFO,("sizebefore="+object_MsgIDs.size());

                        object_MsgIDs.put(objectID + ":" + msgID, "");
//gmsLogger.log(Level.INFO,("sizeafter="+object_MsgIDs.size());

                        chm_MsgIDs.put(from, object_MsgIDs);

                    } else {
                        gmsLogger.log(Level.FINE, "Comparing message |" + shortPayLoad + "| to see if it is a stop command");
                        if (shortPayLoad.contains("STOP")) {
                            System.out.println("Received STOP message from " + from + " !!!!!!!!");
                            //gmsLogger.log(Level.INFO,("before waitingToReceiveStopFrom=" + waitingToReceiveStopFrom.toString());
                            receivedStopFrom.add(from);
                            //gmsLogger.log(Level.INFO,("before waitingToReceiveStopFrom=" + waitingToReceiveStopFrom.toString());

                            gmsLogger.log(Level.INFO, ("Total number of STOP messages received so far is: " + receivedStopFrom.size()));
                            gmsLogger.log(Level.INFO, ("Received STOP from: " + receivedStopFrom.toString() + " so far"));


                        }
                    }
                    if ((receivedStopFrom.size() == numberOfInstances - 1)) {
                        receiveEndTime = new GregorianCalendar();
                        completedCheck.set(true);
                        synchronized (completedCheck) {
                            completedCheck.notify();
                        }
                    }
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        }
    }

    public static byte[] long2bytearray(long l) {
        byte b[] = new byte[8];
        ByteBuffer buf = ByteBuffer.wrap(b);
        buf.putLong(l);
        return b;
    }

    public static byte[] int2bytearray(int i) {
        byte b[] = new byte[4];
        ByteBuffer buf = ByteBuffer.wrap(b);
        buf.putInt(i);
        return b;
    }

    public static byte[] createMsg(long objectID, long msgID, int to, int from, int payloadsize) {

        // create the message to be sent
        byte[] b1 = long2bytearray(objectID);
        byte[] b2 = long2bytearray(msgID);
        byte[] b3 = int2bytearray(to);
        byte[] b4 = int2bytearray(from);
        byte[] b5 = new byte[1];

        if (payloadsize == -1) {
            b5 = createPayload(0);
        } else {
            int pls = payloadsize - (b1.length + b2.length + b3.length + b4.length);
            b5 = createPayload(pls);
        }
        int msgSize = b1.length + b2.length + b3.length + b4.length + b5.length;

        byte[] msg = new byte[(int) msgSize];
        int j = 0;
        for (int i = 0; i < b1.length; i++) {
            msg[j++] = b1[i];
        }
        for (int i = 0; i < b2.length; i++) {
            msg[j++] = b2[i];
        }
        for (int i = 0; i < b3.length; i++) {
            msg[j++] = b3[i];
        }
        for (int i = 0; i < b4.length; i++) {
            msg[j++] = b4[i];
        }
        for (int i = 0; i < b5.length; i++) {
            msg[j++] = b5[i];
        }
        return msg;
    }

    public static String displayMsg(byte[] msg) {
        StringBuilder sb = new StringBuilder(60);
        ByteBuffer buf = ByteBuffer.wrap(msg);
        /*
        long objectID = buf.getLong(0);
        long msgID = buf.getLong(8);
        int to = buf.getInt(16);
        int from = buf.getInt(20);
        String payload = new String(msg, 24, msg.length - 24);
         */
        sb.append("[");
        sb.append("objectId:").append(buf.getLong(0));
        sb.append(" msgId:").append(buf.getLong(8));
        sb.append(" to:").append(buf.getInt(16));
        sb.append(" from:").append(buf.getInt(20));
        String payload = new String(msg, 24, msg.length - 24);
        if (payload.length() > 25) {
            sb.append(" payload:").append(payload.substring(0, 10)).append("...");
            sb.append(payload.substring(payload.length() - 10, payload.length()));
            //sb.append(" payload:").append(payload);
            sb.append(" payload length:").append(Integer.toString(payload.length()));
        } else {
            sb.append(" payload:").append(payload);
        }
        sb.append("]");

        return sb.toString();
    }

    public static byte[] createPayload(int size) {
        byte[] b = new byte[1];
        if (size > 2) {
            b = new byte[size];
            b[0] = 'a';
            int k = 1;
            for (; k < size - 1; k++) {
                b[k] = 'X';
            }
            b[k] = 'z';
        } else {
            b = "STOP".getBytes();
        }
        return b;
    }
}

