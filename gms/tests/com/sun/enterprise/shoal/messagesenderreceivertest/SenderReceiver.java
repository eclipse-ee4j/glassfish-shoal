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

import com.sun.enterprise.ee.cms.core.*;
import com.sun.enterprise.ee.cms.impl.client.JoinedAndReadyNotificationActionFactoryImpl;
import com.sun.enterprise.ee.cms.impl.client.PlannedShutdownActionFactoryImpl;
import com.sun.enterprise.ee.cms.impl.client.MessageActionFactoryImpl;
import com.sun.enterprise.ee.cms.logging.GMSLogDomain;
import com.sun.enterprise.ee.cms.spi.MemberStates;
import com.sun.enterprise.shoal.*;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
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

public class SenderReceiver {

    private GroupManagementService gms = null;
    private static final Logger logger = GMSLogDomain.getLogger(GMSLogDomain.GMS_LOGGER);
    private final String group = "TestGroup";
    static ConcurrentHashMap<String, ConcurrentHashMap<Integer,String>> chm = new ConcurrentHashMap<String, ConcurrentHashMap<Integer,String>>();
    static AtomicBoolean completedCheck = new AtomicBoolean(false);
    static boolean msgIdReceived[];
    static String memberID = null;
    static int numberOfInstances = 0;
    static int payloadSize = 0;
    static int numberOfMessages = 0;
    static AtomicInteger numOfStopMsgReceived = new AtomicInteger(0);
    static AtomicInteger numberOfPlannedShutdown = new AtomicInteger(0);
    static AtomicInteger numberOfJoinAndReady = new AtomicInteger(0);
    static List<String> waitingToReceiveStopFrom;
    static Calendar sendStartTime = null;
    static Calendar sendEndTime = null;
    static Calendar receiveStartTime = null;
    static Calendar receiveEndTime = null;
    static AtomicBoolean firstMsgReceived = new AtomicBoolean(false);

    public static void main(String[] args) {

        //for (int z = 0; z < args.length; z++) {
        //    System.out.println(z + "=" + args[z]);
        //}

        if (args[0].equalsIgnoreCase("-h")) {
            usage();
        }

        if (args[0].equalsIgnoreCase("server")) {
            if (args.length != 2) {
                usage();
            }
            memberID = args[0];
            System.out.println("memberID=" + memberID);
            numberOfInstances = Integer.parseInt(args[1]);
            System.out.println("numberOfInstances=" + numberOfInstances);
        } else if (args[0].contains("instance")) {
            if (args.length >= 4) {
                memberID = args[0];
                System.out.println("memberID=" + memberID);
                numberOfInstances = Integer.parseInt(args[1]);
                System.out.println("numberOfInstances=" + numberOfInstances);
                payloadSize = Integer.parseInt(args[2]);
                System.out.println("payloadSize=" + payloadSize);
                numberOfMessages = Integer.parseInt(args[3]);
                System.out.println("numberOfMessages=" + numberOfMessages);
            } else {
                usage();
            }
        } else {
            usage();
        }

        SenderReceiver sender = new SenderReceiver();
        try {
            sender.test();
        } catch (GMSException e) {
            logger.log(Level.SEVERE, "Exception occured while joining group:" + e);
        }
        sender.waitTillDone();

        // only do verification for INSTANCES
        if (!memberID.equalsIgnoreCase("server")) {
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
        logger.log(Level.INFO, "Testing Complete");

    }

    public static void usage() {

        System.out.println(" For server:");
        System.out.println("    <memberid(server)> <number_of_instances>");
        System.out.println(" For instances:");
        System.out.println("    <memberid(instancexxx)> <number_of_instances> <payloadsize> <number_of_messages>");
        System.exit(0);
    }

    private void test() throws GMSException {

        System.out.println("Testing Started");
        List<String> members;


        //initialize Group Management Service and register for Group Events

        logger.log(Level.INFO, "Registering for group event notifications");
        if (memberID.equalsIgnoreCase("server")) {
            gms = initializeGMS(memberID, group, GroupManagementService.MemberType.SPECTATOR);
            gms.addActionFactory(new JoinedAndReadyNotificationActionFactoryImpl(new JoinAndReadyNotificationCallBack(memberID)));
            gms.addActionFactory(new PlannedShutdownActionFactoryImpl(new PlannedShutdownCallBack(memberID)));

        } else {
            gms = initializeGMS(memberID, group, GroupManagementService.MemberType.CORE);
            gms.addActionFactory(new JoinedAndReadyNotificationActionFactoryImpl(new JoinAndReadyNotificationCallBack(memberID)));
            gms.addActionFactory(new MessageActionFactoryImpl(new MessageCallBack(memberID, numberOfMessages)), "TestComponent");
        }


        logger.log(Level.INFO, "Joining Group " + group);
        gms.join();

        try {
            Thread.sleep(5000);
        } catch (InterruptedException ex) {
        }


        gms.reportJoinedAndReadyState();

        if (memberID.equalsIgnoreCase("server")) {
            logger.log(Level.INFO, ("==================================================="));
            logger.log(Level.INFO, ("Waiting for JOINEDANDREADY from all CORE members"));
            while (true) {
                try {
                    Thread.sleep(5000); // 5 seconds
                } catch (InterruptedException e) {
                }
                logger.log(Level.INFO, ("==================================================="));
                logger.log(Level.INFO, ("Number of JOINEDANDREADY received from all CORE members is " + numberOfJoinAndReady));
                if (numberOfJoinAndReady.get() == numberOfInstances) {
                    logger.log(Level.INFO, ("==================================================="));
                    logger.log(Level.INFO, ("All CORE members have sent JOINEDANDREADY (" + numberOfJoinAndReady + "," + numberOfInstances + ")"));
                    logger.log(Level.INFO, ("==================================================="));
                    break;
                }

            }
        } else {

            logger.log(Level.INFO, ("Waiting for all members to joined the group:" + group));

            while (true) {

                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                }

                System.out.println("numberOfJoinAndReady=" + numberOfJoinAndReady.get());
                System.out.println("numberOfInstances=" + numberOfInstances);

                if (numberOfJoinAndReady.get() == numberOfInstances) {
                    members = gms.getGroupHandle().getCurrentCoreMembers();
                    logger.log(Level.INFO, ("==================================================="));
                    logger.log(Level.INFO, ("All members have joined the group:" + group));
                    logger.log(Level.INFO, ("Members are:" + members.toString()));
                    logger.log(Level.INFO, ("==================================================="));

                    members.remove(memberID);
                    waitingToReceiveStopFrom = new ArrayList<String>();
                    for (String instanceName : members) {
                        waitingToReceiveStopFrom.add(instanceName);
                    }
                    logger.log(Level.INFO, ("waitingToReceiveStopFrom=" + waitingToReceiveStopFrom.toString()));
                    break;
                }

            }

            logger.log(Level.INFO, ("Sending Messages to the following members=" + members.toString()));

            sendStartTime = new GregorianCalendar();
            for (int i = 1; i <= numberOfMessages; i++) {
                for (String instanceName : members) {
                    if (!instanceName.equalsIgnoreCase(memberID)) {

                        StringBuilder sb = new StringBuilder(payloadSize);
                        for (int k = 0; k < payloadSize; k++) {
                            sb.append("X");
                        }
                        TestMessage msg = new TestMessage(instanceName, memberID, i, sb.toString());
                        logger.log(Level.INFO, ("Sending Message:" + msg.toString()));
                        try {
                            gms.getGroupHandle().sendMessage(msg.getTo(), "TestComponent", ShoalMessageHelper.serializeObject(msg));
                        } catch (java.io.NotSerializableException nse) {
                            logger.log(Level.SEVERE, "Exception occurred during sending of message:" + nse);
                        } catch (java.io.IOException ioe) {
                            logger.log(Level.SEVERE, "Exception occurred during sending of message:" + ioe);
                        }
                    }
                }
            }
            for (String instanceName : members) {
                if (!instanceName.equalsIgnoreCase(memberID)) {
                    TestMessage msg = new TestMessage(instanceName, memberID, 0, "STOP");
                    System.out.println("Sending STOP message to " + msg.getTo() + "!!!!!!!!!!!!!!!");
                    try {
                        gms.getGroupHandle().sendMessage(msg.getTo(), "TestComponent", ShoalMessageHelper.serializeObject(msg));
                    } catch (java.io.NotSerializableException nse) {
                        logger.log(Level.SEVERE, "Exception occurred during sending of message:" + nse);
                    } catch (java.io.IOException ioe) {
                        logger.log(Level.SEVERE, "Exception occurred during sending of message:" + ioe);
                    }
                }
            }
            sendEndTime = new GregorianCalendar();
        }

    }

    public void waitTillDone() {
        if (memberID.equalsIgnoreCase("server")) {
            logger.log(Level.INFO, ("==================================================="));
            logger.log(Level.INFO, ("Waiting for all CORE members to send PLANNEDSHUTDOWN"));
            while (true) {

                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                }

                logger.log(Level.INFO, ("==================================================="));
                logger.log(Level.INFO, ("Number of PLANNEDSHUTDOWN received from all CORE members is " + numberOfPlannedShutdown.get()));
                if (numberOfPlannedShutdown.get() == numberOfInstances) {
                    logger.log(Level.INFO, ("==================================================="));
                    logger.log(Level.INFO, ("Have received PLANNEDSHUTDOWN from all CORE members (" + numberOfPlannedShutdown.get() + ")"));
                    logger.log(Level.INFO, ("==================================================="));
                    break;
                } else if (gms.getGroupHandle().getCurrentCoreMembers().size() == 0) {
                    logger.log(Level.INFO, ("==================================================="));
                    logger.log(Level.INFO, ("Missed a PLANNEDSHUTDOWN. All core members have stopped. Only received PLANNEDSHUTDOWN from CORE members (" + numberOfPlannedShutdown.get() + ")"));
                    logger.log(Level.INFO, ("==================================================="));
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
            try {
                Thread.sleep(1000);
            } catch (Throwable t) {
            }
        }
        leaveGroupAndShutdown(memberID, gms);
    }

    private GroupManagementService initializeGMS(String memberID, String groupName, GroupManagementService.MemberType mType) {
        logger.log(Level.INFO, "Initializing Shoal for member: " + memberID + " group:" + groupName);
        return (GroupManagementService) GMSFactory.startGMSModule(
                memberID,
                groupName,
                mType,
                new Properties());

    }

    private void leaveGroupAndShutdown(String memberID, GroupManagementService gms) {
        logger.log(Level.INFO, "Shutting down gms " + gms + "for member: " + memberID);
        gms.shutdown(GMSConstants.shutdownType.INSTANCE_SHUTDOWN);
    }

    private class JoinAndReadyNotificationCallBack implements CallBack {

        private String memberID;

        public JoinAndReadyNotificationCallBack(String memberID) {
            this.memberID = memberID;
        }

        public void processNotification(Signal notification) {
            logger.log(Level.INFO, "***JoinAndReadyNotification received from: " + notification.getMemberToken());
            if (!(notification instanceof JoinedAndReadyNotificationSignal)) {
                logger.log(Level.SEVERE, "received unknown notification type:" + notification + " from:" + notification.getMemberToken());
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

                    logger.log(Level.INFO, "numberOfJoinAndReady received so far is: " + numberOfJoinAndReady.get());
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
            logger.log(Level.INFO, "***PlannedShutdownNotification received from: " + notification.getMemberToken());
            if (!(notification instanceof PlannedShutdownSignal)) {
                logger.log(Level.SEVERE, "received unknown notification type:" + notification + " from:" + notification.getMemberToken());
            } else {
                if (!notification.getMemberToken().equals("server")) {
                    numberOfPlannedShutdown.incrementAndGet();
                    logger.log(Level.INFO, "numberOfPlannedShutdown received so far is" + numberOfPlannedShutdown.get());
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
            // logger.log(Level.INFO, "***Message received from: " + notification.getMemberToken());
            if (!(notification instanceof MessageSignal)) {
                logger.log(Level.SEVERE, "received unknown notification type:" + notification + " from:" + notification.getMemberToken());
            } else {
                if (!firstMsgReceived.get()) {
                    firstMsgReceived.set(true);
                    receiveStartTime = new GregorianCalendar();
                }
                try {
                    notification.acquire();
                    MessageSignal messageSignal = (MessageSignal) notification;

                    final byte[] serializedObj = messageSignal.getMessage();
                    TestMessage msg = (TestMessage) ShoalMessageHelper.deserializeObject(serializedObj);

                    String payload = msg.getPayLoad();
                    String shortPayLoad = msg.getPayLoad();
                    if (shortPayLoad.length() > 10) {
                        shortPayLoad = shortPayLoad.substring(0, 10) + "...";
                    }
                    System.out.println(memberID + " Received msg: " + msg.toString());

                    String msgFrom = msg.getFrom();
                    int msgIdInt = msg.getMsgId();
                    if (msgIdInt > 0) {

                        // if the INSTANCE does not exist in the map, create it.
                        ConcurrentHashMap<Integer, String> instance_chm = chm.get(msgFrom);

                        if (instance_chm == null) {
                            instance_chm = new ConcurrentHashMap<Integer, String>();
                        }
                        instance_chm.put(msgIdInt, "");

                        //System.out.println(msgFrom + ":instance_chm.size()=" + instance_chm.size());

                        chm.put(msgFrom, instance_chm);
                        //System.out.println("chm.size()=" + chm.size());

                    } else {
                        System.out.println("Comparing message |" + shortPayLoad + "| to see if it is a stop command");
                        if (shortPayLoad.contains("STOP")) {
                            System.out.println("Received STOP message from " + msgFrom + " !!!!!!!!");
                            numOfStopMsgReceived.incrementAndGet();
                            waitingToReceiveStopFrom.remove(msgFrom);
                            System.out.println("Total number of STOP messages received so far is: " + numOfStopMsgReceived.get());
                            if (waitingToReceiveStopFrom.size() > 1) {
                                System.out.println("Waiting to receive STOP from: " + waitingToReceiveStopFrom.toString());
                            }

                        }
                    }
                    if ((numOfStopMsgReceived.get() == numberOfInstances - 1)) {
                        receiveEndTime = new GregorianCalendar();
                        completedCheck.set(true);
                        synchronized (completedCheck) {
                            completedCheck.notify();
                        }
                    }
                } catch (SignalAcquireException e) {
                    e.printStackTrace();
                } catch (Throwable t) {
                    t.printStackTrace();
                } finally {
                    try {
                        notification.release();
                    } catch (Exception e) {
                    }

                }
            }
        }
    }

    public static class TestMessage implements Externalizable {

        public final static long serialVersionUID = 1L;
        public String to = "none";
        public String from = "none";
        public int msgId = -1;   // -1 - initialized, 0 - STOP msg , 1...n msgids
        public String payload = "none";

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(60);
            sb.append("[");
            sb.append("to:").append(to);
            sb.append(" from:").append(from);
            sb.append(" msgId:").append(Integer.toString(msgId));
            String thePayload = payload;
            if (payload.length() > 25) {
                sb.append(" payload.substring(0,25):").append(payload.substring(0, 25));
                //sb.append(" payload:").append(payload);
                sb.append(" payload length:").append(Integer.toString(payload.length()));
            } else {
                sb.append(" payload:").append(payload);
            }
            sb.append("]");

            return sb.toString();
        }

        // required for serializable.
        public TestMessage() {
        }

        public String getTo() {
            return to;
        }

        public String getFrom() {
            return from;
        }

        public int getMsgId() {
            return msgId;
        }

        public String getPayLoad() {
            return payload;
        }

        public TestMessage(String to, String from, int msgId, String payload) {
            this.to = to;
            this.from = from;
            this.msgId = msgId;
            this.payload = payload;
        }

        public void writeExternal(ObjectOutput oos) {
            try {

                oos.writeUTF(to);
                oos.writeUTF(from);
                oos.writeInt(msgId);
                oos.writeUTF(payload);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void readExternal(ObjectInput ois) throws IOException {
            to = ois.readUTF();
            from = ois.readUTF();
            msgId = ois.readInt();
            payload = ois.readUTF();
        }
    }
}

