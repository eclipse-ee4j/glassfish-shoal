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

/*
 *
 * This program creates a cluster comprised of a master and N number of core members.
 * Each core member acts as an instance in the cluster which replicates(sends messages)
 * to the instance that is one greater than itself. The last instance replicates
 * to the first instance in the cluster. The names of the instances are instance101,
 * instance102, etc... The master node is called server. Based on the arguments
 * passed into the program the instances will send M objects*N messages to the replica.
 * The replica saves the messages and can verify it's content was correct. Once an
 * instance is done
 * sending it's messages a final message (DONE) is sent to the replica(s). The replica
 * upon receiving a done message(s) from it's sends then sends a single message to the master.
 * Once the master has received a DONE message from each instance, it calls group shutdown
 * on the cluster.
 *
 */
package com.sun.enterprise.shoal.messagesenderreceivertest;

import com.sun.enterprise.ee.cms.impl.base.Utility;
import com.sun.enterprise.ee.cms.core.*;
import com.sun.enterprise.ee.cms.core.PlannedShutdownSignal;
import com.sun.enterprise.ee.cms.impl.client.JoinNotificationActionFactoryImpl;
import com.sun.enterprise.ee.cms.impl.client.JoinedAndReadyNotificationActionFactoryImpl;
import com.sun.enterprise.ee.cms.impl.client.MessageActionFactoryImpl;
import com.sun.enterprise.ee.cms.impl.client.PlannedShutdownActionFactoryImpl;
import com.sun.enterprise.ee.cms.logging.GMSLogDomain;
import com.sun.enterprise.ee.cms.logging.NiceLogFormatter;
import com.sun.enterprise.ee.cms.spi.MemberStates;
import com.sun.enterprise.mgmt.transport.grizzly.GrizzlyUtil;
import com.sun.enterprise.mgmt.transport.grizzly.GrizzlyConfigConstants;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.List;

import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.ConsoleHandler;
import java.util.logging.ErrorManager;

public class HAMessageReplicationSimulator {

    static final int TIMELIMIT = 5;  // in minutes
    static final int MAXRETRYCOUNT = 8;
    static final int RETRYSLEEP = 5000;  // 5 seconds
    private GroupManagementService gms = null;
    private static final Logger gmsLogger = GMSLogDomain.getLogger(GMSLogDomain.GMS_LOGGER);
    private static final Logger gmsMonitorLogger = GMSLogDomain.getMonitorLogger();
    private static final Level GMSDEFAULTLOGLEVEL = Level.WARNING;
    private static final Logger myLogger = java.util.logging.Logger.getLogger("HAMessageReplicationSimulator");
    private static final Level TESTDEFAULTLOGLEVEL = Level.INFO;
    //static ConcurrentHashMap<String, String> msgIDs_received = new ConcurrentHashMap<String, String>();
    static ConcurrentHashMap<Integer, ConcurrentHashMap<Long, String>> payloads_received = new ConcurrentHashMap<Integer, ConcurrentHashMap<Long, String>>();
    static String memberID = null;
    static Integer memberIDNum = new Integer(0);
    static int numberOfInstances = 0;
    static int payloadSize = 0;
    static int thinktime = 10;  // in milliseconds
    static long numberOfObjects = 0;
    static long msgsPerObject = 0;
    static AtomicInteger numberOfJoinAndReady = new AtomicInteger(0);
    static AtomicInteger numberOfPlannedShutdown = new AtomicInteger(0);
    static Calendar sendStartTime = null;
    static Calendar sendEndTime = null;
    static Calendar receiveStartTime = null;
    static Calendar receiveEndTime = null;
    static AtomicBoolean firstMsgReceived = new AtomicBoolean(false);
    static List<String> members;
    static List<String> others;
    static String groupName = null;
    static final String BUDDY = "buddy";
    static final String CHASH = "chash";
    static String replicationType = BUDDY;
    static AtomicBoolean groupShutdown = new AtomicBoolean(false);
    static AtomicBoolean startTesting = new AtomicBoolean(false);
    static int minInstanceNum = 101;
    public static String expectedPayload;
    public static int payloadErrors = 0;
    public static boolean validateAllPayloads = false;
    public static int validatedMessages = 0;
    public static AtomicLong totalduration = new AtomicLong(0);
    public static AtomicLong number_of_notifications = new AtomicLong(0);
    private static final String READY_TO_TEST = "Ready_To_Test";
    private static final String START_TESTING = "Start_Testing";
    private static final String DONE_SENDING = "DONE_Sending";
    private static final String DONE_RECEIVING = "DONE_Receiving";
    static ConcurrentHashMap<String, String> waitingForDoneSendingFrom = new ConcurrentHashMap<String, String>();
    static ConcurrentHashMap<String, String> waitingForDoneReceivingFrom = new ConcurrentHashMap<String, String>();
    static ConcurrentHashMap<String, String> receivedReadyToTestFrom = new ConcurrentHashMap<String, String>();
    static int replica = 0;
    static String sReplica = "";

    public static void main(String[] args) {
        // this configures the formatting of the gms log output
        Utility.setLogger(gmsLogger);
        Utility.setupLogHandler();
        // this sets the grizzly log level
        GrizzlyUtil.getLogger().setLevel(Level.WARNING);
        try {
            gmsLogger.setLevel(Level.parse(System.getProperty("LOG_LEVEL", GMSDEFAULTLOGLEVEL.toString())));
        } catch (Exception e) {
            gmsLogger.setLevel(GMSDEFAULTLOGLEVEL);
        }
        gmsLogger.info("GMS Logging using log level of:" + gmsLogger.getLevel());
        gmsMonitorLogger.setLevel(Level.FINE);
        try {
            myLogger.setLevel(Level.parse(System.getProperty("TEST_LOG_LEVEL", TESTDEFAULTLOGLEVEL.toString())));
            setupLogHandler(myLogger.getLevel());
        } catch (Exception e) {
            myLogger.setLevel(TESTDEFAULTLOGLEVEL);
        }
        myLogger.info("Test Logging using log level of:" + myLogger.getLevel());
        if (args[0].equalsIgnoreCase("-h")) {
            usage();
        }
        if (args[0].equalsIgnoreCase("server")) {
            if (args.length != 4) {
                usage();
            }
            memberID = args[0].toLowerCase();
            groupName = args[1].toLowerCase();
            numberOfInstances = Integer.parseInt(args[2]);
            replicationType = args[3].toLowerCase();
            if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
                myLogger.log(TESTDEFAULTLOGLEVEL, "memberID=" + memberID);
                myLogger.log(TESTDEFAULTLOGLEVEL, "GroupName=" + groupName);
                myLogger.log(TESTDEFAULTLOGLEVEL, "numberOfInstances=" + numberOfInstances);
                myLogger.log(TESTDEFAULTLOGLEVEL, "replicationType=" + replicationType);
            }
        } else if (args[0].contains("instance")) {
            if (args.length == 8) {
                memberID = args[0].toLowerCase();
                if (!memberID.startsWith("instance")) {
                    System.err.println("ERROR: The member name must be in the format 'instancexxx'");
                    System.exit(1);
                }
                memberIDNum = new Integer(memberID.replace("instance", ""));
                groupName = args[1].toLowerCase();
                numberOfInstances = Integer.parseInt(args[2]);
                numberOfObjects = Long.parseLong(args[3]);
                msgsPerObject = Long.parseLong(args[4]);
                payloadSize = Integer.parseInt(args[5]);
                thinktime = Integer.parseInt(args[6]);
                replicationType = args[7].toLowerCase();
                if (!replicationType.equals(BUDDY) && !replicationType.equals(CHASH)) {
                    System.err.println("ERROR: Replication type must be either buddy or chash");
                    usage();
                }

                if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
                    myLogger.log(TESTDEFAULTLOGLEVEL, "memberID=" + memberID);
                    myLogger.log(TESTDEFAULTLOGLEVEL, "GroupName=" + groupName);
                    myLogger.log(TESTDEFAULTLOGLEVEL, "numberOfInstances=" + numberOfInstances);
                    myLogger.log(TESTDEFAULTLOGLEVEL, "numberOfObjects=" + numberOfObjects);
                    myLogger.log(TESTDEFAULTLOGLEVEL, "msgsPerObject=" + msgsPerObject);
                    myLogger.log(TESTDEFAULTLOGLEVEL, "payloadSize=" + payloadSize);
                    myLogger.log(TESTDEFAULTLOGLEVEL, "thinktime=" + thinktime);
                    myLogger.log(TESTDEFAULTLOGLEVEL, "replicationType=" + replicationType);
                }
                int tmpSize = payloadSize - (8 + 8 + 4 + 4);
                expectedPayload = new String(createPayload(tmpSize));
            } else {
                usage();
            }
        } else {
            usage();
        }
        HAMessageReplicationSimulator sender = new HAMessageReplicationSimulator();
        sender.test();
        sender.waitTillDone();
        // only do verification for INSTANCES
        if (!memberID.equalsIgnoreCase("server")) {
            /*
            // SINCE THIS CODE WILL ONLY WORK FOR A STABLE BUDDY REPLICATION ENVIRONMENT WHERE
            // THERE IS NO KILLING OR SHUTDOWN OF INSTANCES, I AM COMMENTING IT OUT.
            // This code is in flux and will need to be fixed
            System.out.println("Checking to see if correct number of messages (Objects:" + numberOfObjects + ", MsgsPerObject:" + msgsPerObject + " = [" + (numberOfObjects * msgsPerObject) + "])  were received from each instance");
            System.out.println("================================================================");
            int droppedMessages = 0;
            Enumeration msgIDs = msgIDs_received.keys();
            //System.out.println("msgIDs=" + msgIDs.toString());
            String key = null;
            int replicaIndex = 0;
            String sReplica = "";
            int replica = 0;
            if ((msgIDs != null) && msgIDs.hasMoreElements()) {
            for (long msgNum = 1; msgNum <= msgsPerObject; msgNum++) {
            for (long objectNum = 1; objectNum <= numberOfObjects; objectNum++) {
            replicaIndex = (int) (objectNum % (long) members.size());
            sReplica = members.get(replicaIndex);
            replica = new Integer(sReplica.replace("instance", ""));
            if (replica == memberIDNum) {
            key = objectNum + ":" + msgNum;
            if (msgIDs.containsKey(key)) {
            msgIDs.remove(key);
            } else {
            droppedMessages++;
            System.out.println("Never received objectId:" + objectNum + " msgId:" + msgNum + ", from:" + instanceNum);
            }
            }
            }
            }
            } else {
            droppedMessages = -1;
            }
            if (msgIDs.size() > 0) {
            Enumeration eNum = msgIDs.keys();
            while (eNum.hasMoreElements()) {
            key = (String) eNum.nextElement();
            System.out.println("Received object we should not have - msgID:" + key + ", from:" + msgIDs.get(key));
            }
            }
            System.out.println("---------------------------------------------------------------");
            if (droppedMessages == 0) {
            System.out.println(instanceNum + ": PASS.  No dropped messages");
            } else if (droppedMessages == -1) {
            System.out.println(instanceNum + ": FAILED. No message IDs were received");
            } else {
            System.out.println(instanceNum + ": FAILED. Confirmed (" + droppedMessages + ") messages were dropped");
            }
            System.out.println("================================================================");
            ConcurrentHashMap<Long, String> payLoads = payloads_received.get(instanceNum);
            for (long objectNum = 1; objectNum <= numberOfObjects; objectNum++) {
            if (!payLoads.containsKey(objectNum)) {
            payloadErrors++;
            System.out.println("INTERNAL ERROR: objectId:" + objectNum + " from:" + instanceNum + " missing from payload structure");
            } else {
            String payLoad = payLoads.get(objectNum);
            if (payLoad.equals(expectedPayload)) {
            if (!validateAllPayloads) {
            // avoid double counting when validation enabled in message receive processing.
            validatedMessages++;
            }
            } else {
            System.out.println("actual Payload[objectNum]:" + payLoad);
            payloadErrors++;
            System.out.println("Payload did not match for objectId:" + objectNum + ", from:" + instanceNum);
            }
            }
            }
            System.out.println("---------------------------------------------------------------");
            if (payloadErrors == 0) {
            System.out.println(instanceNum + ": PASS.  No payload errors. Confirmed valid " + validatedMessages + " payloads");
            } else {
            System.out.println(instanceNum + ": FAILED. Confirmed (" + payloadErrors + ") payload errors. Confirmed valid " + validatedMessages + " payloads.");
            }
             */
            System.out.println("================================================================");
            long timeDelta = 0;
            long remainder = 0;
            long msgPerSec = 0;
            long bytespersec = 0;
            long kbytespersec = 0;
            // NOTE
            // The receive time could be quicker than the send time since we might be a little
            // slower at sending than we are receiving the message and the other members in the
            // cluster could be finished before us
            if (sendEndTime != null && sendStartTime != null) {
                timeDelta = (sendEndTime.getTimeInMillis() - sendStartTime.getTimeInMillis()) / 1000;
                remainder = (sendEndTime.getTimeInMillis() - sendStartTime.getTimeInMillis()) % 1000;
                if (timeDelta != 0) {
                    msgPerSec = (numberOfObjects * msgsPerObject) / timeDelta;
                    bytespersec = ((long) numberOfObjects * (long) msgsPerObject * (long) payloadSize) / timeDelta;
                    kbytespersec = bytespersec / 1000L;
                }
                System.out.println("\nSending Messages Time data: Start[" + sendStartTime.getTime() + "], End[" + sendEndTime.getTime() + "], Delta[" + timeDelta + "." + remainder + "] secs, MsgsPerSec[" + msgPerSec + "], BytesPerSecond[" + bytespersec + "], KbytesPerSecond[" + kbytespersec + "], MsgSize[" + payloadSize + "]\n");
            } else {
                if (sendEndTime == null) {
                    System.out.println("\nSEVERE: Sending End Time was null");
                }
                if (sendStartTime == null) {
                    System.out.println("\nSEVERE: Sending End Time was null");
                }
            }
            bytespersec = 0;
            kbytespersec = 0;
            if (receiveEndTime != null && receiveStartTime != null) {
                timeDelta = (receiveEndTime.getTimeInMillis() - receiveStartTime.getTimeInMillis()) / 1000;
                remainder = (receiveEndTime.getTimeInMillis() - receiveStartTime.getTimeInMillis()) % 1000;
                msgPerSec = 0;
                if (timeDelta != 0) {
                    msgPerSec = (numberOfObjects * msgsPerObject) / timeDelta;
                    bytespersec = ((long) numberOfObjects * (long) msgsPerObject * (long) payloadSize) / timeDelta;
                    kbytespersec = bytespersec / 1000L;
                }
                System.out.println("\nReceiving Messages Time data: Start[" + receiveStartTime.getTime() + "], End[" + receiveEndTime.getTime() + "], Delta[" + timeDelta + "." + remainder + "] secs, MsgsPerSec[" + msgPerSec + "], BytesPerSecond[" + bytespersec + "], KbytesPerSecond[" + kbytespersec + "], MsgSize[" + payloadSize + "]\n");
            } else {
                if (receiveEndTime == null) {
                    System.out.println("\nSEVERE: Receiving End Time was null");
                }
                if (receiveStartTime == null) {
                    System.out.println("\nSEVERE: Receiving End Time was null");
                }
            }
            System.out.println("NUMBER OF NOTIFICATIONS:" + number_of_notifications.get());
            System.out.println("TOTAL DURATION (MS):" + totalduration + ", (SEC):" + totalduration.get() / 1000);
            System.out.println("AVG NOTIFICATION PROCESSING TIME(wallclock):" + totalduration.get() / number_of_notifications.get() + " (MS)");
        }
        System.out.println("================================================================");
        System.out.println("Testing Complete");
    }

    public static void usage() {
        System.out.println(" For server:");
        System.out.println("    <memberid(server)> <groupName> <number_of_instances> <replication_type>");
        System.out.println(" For instances:");
        System.out.println("    <memberid(instancexxx)> <groupName> <number_of_instances> <number_of_objects> <number_of_msgs_per_object> <payloads_receivedize> <thinktime> <replication_type>");
        System.exit(0);
    }

    private void test() {
        System.out.println("Testing Started");
        //initialize Group Management Service and register for Group Events
        if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
            myLogger.log(TESTDEFAULTLOGLEVEL, "Registering for group event notifications");
        }
        if (memberID.equalsIgnoreCase("server")) {
            gms = initializeGMS(memberID, groupName, GroupManagementService.MemberType.SPECTATOR);
            gms.addActionFactory(new PlannedShutdownActionFactoryImpl(new PlannedShutdownNotificationCallBack()));
        } else {
            gms = initializeGMS(memberID, groupName, GroupManagementService.MemberType.CORE);
        }
        gms.addActionFactory(new JoinNotificationActionFactoryImpl(new JoinNotificationCallBack()));
        gms.addActionFactory(new JoinedAndReadyNotificationActionFactoryImpl(new JoinAndReadyNotificationCallBack(memberID)));
        gms.addActionFactory(new MessageActionFactoryImpl(new MessageCallBack(memberID)), "TestComponent");
        if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
            myLogger.log(TESTDEFAULTLOGLEVEL, "Joining Group " + groupName);
        }
        try {
            gms.join();
        } catch (GMSException e) {
            myLogger.log(Level.SEVERE, "Exception occured :" + e, e);
            System.exit(1);
        }
        sleep(5000);
        gms.reportJoinedAndReadyState();
        sleep(5000);
        if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
            myLogger.log(TESTDEFAULTLOGLEVEL, "Waiting for all members to joined the group:" + groupName);
        }
        // an instance
        if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
            myLogger.log(TESTDEFAULTLOGLEVEL, "Waiting for all members to report joined and ready for the group:" + groupName);
        }
        while (true) {
            sleep(2000);
            if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
                myLogger.log(TESTDEFAULTLOGLEVEL, "numberOfJoinAndReady=" + numberOfJoinAndReady.get());
                myLogger.log(TESTDEFAULTLOGLEVEL, "numberOfInstances=" + numberOfInstances);
            }
            if (numberOfJoinAndReady.get() == numberOfInstances) {
                members = gms.getGroupHandle().getCurrentCoreMembers();
                others = new ArrayList<String>(members);
                others.remove(memberID);
                others = Collections.unmodifiableList(others);

                if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
                    myLogger.log(TESTDEFAULTLOGLEVEL, "All members [" + members.size() + "] are joined and ready in the group:" + groupName);
                } // remove ourselves from the list if we are an instance

                break;
            }
        }
        if (memberID.equalsIgnoreCase("server")) {
            // build a list of all the expected done messages we expected to receive so that
            // we can tell who we have not received a done message from
            for (String member : members) {
                waitingForDoneReceivingFrom.put(member, "");
            }
            myLogger.log(TESTDEFAULTLOGLEVEL, "waitingForDoneReceivingFrom:" + waitingForDoneReceivingFrom.toString().replace("=", ""));

        } else {
            if (replicationType.equals(CHASH)) {
                for (String other : others) {
                    waitingForDoneSendingFrom.put(other, "");
                }
            } else {
                // buddy replication
                int replicaSender = memberIDNum - 1;
                if (replicaSender < minInstanceNum) {
                    replicaSender = minInstanceNum + (numberOfInstances - 1);
                }
                waitingForDoneSendingFrom.put("instance" + replicaSender, "");
            }
            myLogger.log(TESTDEFAULTLOGLEVEL, "waitingForDoneSendingFrom:" + waitingForDoneSendingFrom.toString().replace("=", ""));
        }


        if (!memberID.equalsIgnoreCase("server")) {

            if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
                myLogger.log(TESTDEFAULTLOGLEVEL, "Tell MASTER we are " + READY_TO_TEST);
            }
            String _msg = READY_TO_TEST + ":" + memberID;
            try {
                gms.getGroupHandle().sendMessage("server", "TestComponent", _msg.getBytes());
            } catch (GMSException ge) {
                if (!ge.getMessage().contains("Client is busy or timed out")) {
                    myLogger.log(Level.WARNING, "Exception occurred with sending message (" + _msg + "):" + ge, ge);
                } else {
                    //retry the send up to 3 times
                    int retryCount = 1;
                    for (; retryCount <= MAXRETRYCOUNT; retryCount++) {
                        try {
                            myLogger.log(Level.WARNING, "Need to retry, sleeping " + RETRYSLEEP + " ms");
                            sleep(RETRYSLEEP);
                            myLogger.log(Level.WARNING, "Retry [" + retryCount + "] time(s) to send message (" + _msg + ")");
                            gms.getGroupHandle().sendMessage("server", "TestComponent", _msg.getBytes());
                            break; // if successful
                        } catch (GMSException ge1) {
                            myLogger.log(Level.WARNING, "Exception occurred during send message retry (" + retryCount + ") for (" + _msg + "):" + ge1, ge1);
                        }
                    }
                    if (retryCount > MAXRETRYCOUNT) {
                        myLogger.log(Level.SEVERE, "Retry count exceeded " + MAXRETRYCOUNT + " times while trying to send the message (" + _msg + ")");
                    }
                }
            }
            if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
                myLogger.log(TESTDEFAULTLOGLEVEL, "Waiting for MASTER to signal StartTesting");
            }
            synchronized (startTesting) {
                try {
                    startTesting.wait(); // wait till master tells us to start testing
                } catch (InterruptedException ie) {
                }
            }
            if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
                myLogger.log(TESTDEFAULTLOGLEVEL, "Starting Testing");
            }
            byte[] msg = new byte[1];
            sendStartTime = new GregorianCalendar();
            if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
                myLogger.log(TESTDEFAULTLOGLEVEL, "Send start time: " + sendStartTime);
            }
            int sendmsg = 1;
            long msgNum = 1;
            long objectNum = 1;
            for (; msgNum <= msgsPerObject; msgNum++) {
                for (objectNum = 1; objectNum <= numberOfObjects; objectNum++) {
                    if (!gms.isGroupBeingShutdown()) {

                        // if replicationType is buddy, the replica and sReplica value was set earlier
                        if (replicationType.equals(CHASH)) {
                            int replicaIndex = (int) (objectNum % (long) others.size());
                            sReplica = others.get(replicaIndex);
                            replica = new Integer(sReplica.replace("instance", ""));
                        } else {
                            // buddy replication
                            replica = memberIDNum + 1;
                            if (replica > minInstanceNum + (numberOfInstances - 1)) {
                                replica = minInstanceNum;
                            }
                            sReplica = "instance" + replica;
                        }
                        // create a unique objectnum
                        //String sObject = Integer.toString(memberIDNum) + Long.toString(objectNum);
                        //long _objectNum = Long.parseLong(sObject);
                        // create the message to be sent
                        msg = createMsg(objectNum, msgNum, replica, memberIDNum, payloadSize);
                        if (myLogger.isLoggable(Level.FINE)) {
                            myLogger.log(Level.FINE, "Sending Message:" + displayMsg(msg));
                        }
                        try {
                            gms.getGroupHandle().sendMessage(sReplica, "TestComponent", msg);
                        } catch (GMSException ge) {
                            if (!ge.getMessage().contains("Client is busy or timed out")) {
                                myLogger.log(Level.WARNING, "Exception occurred with sending message (" + displayMsg(msg) + "):" + ge, ge);
                            } else {
                                //retry the send up to 3 times
                                int retryCount = 1;
                                for (; retryCount <= MAXRETRYCOUNT; retryCount++) {
                                    try {
                                        myLogger.log(Level.WARNING, "Need to retry, sleeping " + RETRYSLEEP + " ms");
                                        sleep(RETRYSLEEP);
                                        if (gms.isGroupBeingShutdown()) {
                                            break;// group shutdown has begun
                                        }
                                        myLogger.log(Level.WARNING, "Retry [" + retryCount + "] time(s) to send message (" + displayMsg(msg) + ")");
                                        gms.getGroupHandle().sendMessage(sReplica, "TestComponent", msg);
                                        break; // if successful
                                    } catch (GMSException ge1) {
                                        myLogger.log(Level.WARNING, "Exception occurred during send message retry (" + retryCount + ") for (" + displayMsg(msg) + "):" + ge1, ge1);
                                    }
                                }
                                if (retryCount > MAXRETRYCOUNT) {
                                    myLogger.log(Level.SEVERE, "Retry count exceeded " + MAXRETRYCOUNT + " times while trying to send the message (" + displayMsg(msg) + ")");
                                }
                            }
                        }
                        //if ((sendmsg % (numberOfInstances - 1)*numberOfInstances) == 0) {
                        //    sleep(thinktime);
                        // }
                    } else {
                        break; // group shutdown has begun
                    }
                }
                if (gms.isGroupBeingShutdown()) {
                    break;// group shutdown has begun
                }
            }
            if (gms.isGroupBeingShutdown()) {
                myLogger.log(Level.SEVERE, "Group Shutdown has begun, Sending of messages has terminated - objectNum=" + objectNum + ", msgNum=" + msgNum);
            } else {
                sendEndTime = new GregorianCalendar();
                if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
                    myLogger.log(TESTDEFAULTLOGLEVEL, "Send end time: " + sendEndTime);
                    myLogger.log(TESTDEFAULTLOGLEVEL, "Finished Sending Messages to:" + sReplica);
                } // send donesending message out
                List<String> sendDoneTo = new ArrayList<String>();
                if (replicationType.equals(CHASH)) {
                    sendDoneTo.addAll(others);
                } else {
                    sendDoneTo.add(sReplica);
                }
                if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
                    myLogger.log(TESTDEFAULTLOGLEVEL, "Sending  message (" + DONE_SENDING + ") to replication member(s)" + sendDoneTo.toString());
                }
                for (String member : sendDoneTo) {
                    try {
                        if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
                            myLogger.log(TESTDEFAULTLOGLEVEL, "Sending message (" + DONE_SENDING + ") to member:" + member);
                        }
                        if (gms.isGroupBeingShutdown()) {
                            myLogger.log(Level.SEVERE, "Group Shutdown has begun, Sending of DONE message(s) has terminated");
                            break;
                        }
                        gms.getGroupHandle().sendMessage(member, "TestComponent", DONE_SENDING.getBytes());
                    } catch (GMSException e) {
                        myLogger.log(Level.WARNING, "Exception occured sending message (" + DONE_SENDING + ") to sReplica" + e, e);
                        //retry the send up to 3 times
                        int retryCount = 1;
                        for (; retryCount <= MAXRETRYCOUNT; retryCount++) {
                            try {
                                myLogger.log(Level.WARNING, "Need to retry, sleeping " + RETRYSLEEP + " ms");
                                sleep(RETRYSLEEP);
                                myLogger.log(Level.WARNING, "Retry [" + retryCount + "] time(s) to send message (" + DONE_SENDING + ")");
                                if (gms.isGroupBeingShutdown()) {
                                    myLogger.log(Level.SEVERE, "Group Shutdown has begun, Retry of Sending of DONE message(s) has terminated");
                                    break;
                                }
                                gms.getGroupHandle().sendMessage(member, "TestComponent", DONE_SENDING.getBytes());
                                break; // if successful
                            } catch (GMSException ge1) {
                                myLogger.log(Level.WARNING, "Exception occurred while resending message retry (" + retryCount + ") for (" + DONE_SENDING + ") to :" + member + " : " + ge1, ge1);
                            }
                            if (retryCount > MAXRETRYCOUNT) {
                                myLogger.log(Level.SEVERE, "Retry count exceeded " + MAXRETRYCOUNT + " times while trying to resend the message (" + DONE_SENDING + ") to :" + member);
                            }
                        }
                    }
                }
            }
        }

    }

    public void waitTillDone() {
        long waitForStartTime = 0;
        long _timelimit = 0;
        boolean firstTime = true;
        long currentTime = 0;
        if (memberID.equalsIgnoreCase("server")) {
            if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
                myLogger.log(TESTDEFAULTLOGLEVEL, "Waiting for all CORE members to send messages (" + DONE_RECEIVING + ")");
                myLogger.log(TESTDEFAULTLOGLEVEL, "timelimit=" + TIMELIMIT);
            }

            while (true) {
                // wait for all instances to forward the DONE messages that they received to us
                if (waitingForDoneReceivingFrom.size() == 0) {
                    break;
                }
                if ((waitingForDoneReceivingFrom.size() < numberOfInstances)) {
                    if (firstTime) {
                        waitForStartTime = System.currentTimeMillis();
                        firstTime = false;
                        if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
                            myLogger.log(TESTDEFAULTLOGLEVEL, "First " + DONE_RECEIVING + " received, starting timelimit");
                        }
                    }
                    currentTime = System.currentTimeMillis();
                    _timelimit = ((currentTime - waitForStartTime) / 60000);
                    if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
                        myLogger.log(TESTDEFAULTLOGLEVEL, "current timelimit=" + _timelimit + ", max timlimit=" + (TIMELIMIT - 1));
                    }
                    if (_timelimit >= TIMELIMIT - 1) {
                        myLogger.log(Level.SEVERE, memberID + " EXCEEDED " + TIMELIMIT + " minute timelimit waiting to receive all DONE messages");
                        break;
                    }
                }
                if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
                    myLogger.log(TESTDEFAULTLOGLEVEL, "Waiting 10 seconds to receive message (" + DONE_RECEIVING + ") from: " + waitingForDoneReceivingFrom.toString().replace("=", ""));
                }
                sleep(10000); // 10 seconds
            }
            gms.announceGroupShutdown(groupName, GMSConstants.shutdownState.INITIATED);
            synchronized (numberOfPlannedShutdown) {
                try {
                    numberOfPlannedShutdown.wait(20000); // wait till all members shutdown OR twenty seconds
                } catch (InterruptedException ie) {
                }
                if (numberOfPlannedShutdown.get() == numberOfInstances) {
                    if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
                        myLogger.log(TESTDEFAULTLOGLEVEL, "Notified of " + numberOfPlannedShutdown.get() + " members shutting down out of " + numberOfInstances);
                    }
                } else {
                    myLogger.log(Level.SEVERE, "Notified of " + numberOfPlannedShutdown.get() + " members shutting down out of " + numberOfInstances);
                    myLogger.log(Level.SEVERE, "Still waiting for " + DONE_RECEIVING + " from (" + waitingForDoneReceivingFrom.toString().replace("=", "") + ")");
                }
            }
            gms.announceGroupShutdown(groupName, GMSConstants.shutdownState.COMPLETED);
        } else { // instance
            int count = 0;
            boolean shutdownOK = true;
            List<String> _members;
            while (!gms.isGroupBeingShutdown()) {
                _members = gms.getGroupHandle().getAllCurrentMembers();
                if ((count % 12) == 0) {
                    if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
                        myLogger.log(TESTDEFAULTLOGLEVEL, "Waiting for group shutdown, current members are (" + _members.toString() + ")");
                    }
                }
                if (!_members.contains("server")) {
                    myLogger.log(Level.SEVERE, "Server has left group before we received GroupShutdown msg:" + _members.toString());
                    myLogger.log(Level.SEVERE, "TERMINATING wait processing");
                    myLogger.log(Level.SEVERE, "Still waiting for " + DONE_SENDING + " from (" + waitingForDoneSendingFrom.toString().replace("=", "") + ")");
                    shutdownOK = false;
                    break;
                }
                sleep(5000);
                count++;
            }
            if (shutdownOK) {
                if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
                    myLogger.log(TESTDEFAULTLOGLEVEL, "Group Shutdown is has begun");
                }
            }
        }
        leaveGroupAndShutdown(memberID, gms);
    }

    private GroupManagementService initializeGMS(String memberID, String groupName, GroupManagementService.MemberType mType) {
        gmsLogger.log(Level.INFO, "Initializing Shoal for member: " + memberID + " group:" + groupName);
        Properties configProps = new Properties();
        configProps.put(ServiceProviderConfigurationKeys.MULTICASTADDRESS.toString(),
                System.getProperty("MULTICASTADDRESS", "229.9.1.4"));
        configProps.put(ServiceProviderConfigurationKeys.MULTICASTPORT.toString(), 2299);
//        gmsLogger.FINE("Is initial host="+System.getProperty("IS_INITIAL_HOST"));
        configProps.put(ServiceProviderConfigurationKeys.IS_BOOTSTRAPPING_NODE.toString(),
                System.getProperty("IS_INITIAL_HOST", "false"));
        /*
        if(System.getProperty("INITIAL_HOST_LIST") != null){
        configProps.put(ServiceProviderConfigurationKeys.VIRTUAL_MULTICAST_URI_LIST.toString(),
        System.getProperty("INITIAL_HOST_LIST"));
        }
         */
        configProps.put(ServiceProviderConfigurationKeys.FAILURE_DETECTION_RETRIES.toString(),
                System.getProperty("MAX_MISSED_HEARTBEATS", "3"));
        configProps.put(ServiceProviderConfigurationKeys.FAILURE_DETECTION_TIMEOUT.toString(),
                System.getProperty("HEARTBEAT_FREQUENCY", "2000"));
        configProps.put(ServiceProviderConfigurationKeys.INCOMING_MESSAGE_QUEUE_SIZE.toString(),
                System.getProperty("INCOMING_MESSAGE_QUEUE_SIZE", "3000"));
        configProps.put("MONITORING", 20L);

        //Uncomment this to receive loop back messages
        //configProps.put(ServiceProviderConfigurationKeys.LOOPBACK.toString(), "true");
        final String bindInterfaceAddress = System.getProperty("BIND_INTERFACE_ADDRESS");
        if (bindInterfaceAddress != null) {
            configProps.put(ServiceProviderConfigurationKeys.BIND_INTERFACE_ADDRESS.toString(), bindInterfaceAddress);
        }
        return (GroupManagementService) GMSFactory.startGMSModule(
                memberID,
                groupName,
                mType,
                configProps);
    }

    private void leaveGroupAndShutdown(String memberID, GroupManagementService gms) {
        gmsLogger.log(Level.INFO, "Shutting down gms " + gms + "for member: " + memberID);
        gms.shutdown(GMSConstants.shutdownType.INSTANCE_SHUTDOWN);
    }

    private class JoinNotificationCallBack implements CallBack {

        public JoinNotificationCallBack() {
        }

        public void processNotification(Signal notification) {
            if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
                myLogger.log(TESTDEFAULTLOGLEVEL, "***JoinNotification received from: " + notification.getMemberToken());
            }
            if (!(notification instanceof JoinNotificationSignal)) {
                if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
                    myLogger.log(TESTDEFAULTLOGLEVEL, "received unknown notification type:" + notification + " from:" + notification.getMemberToken());
                }
            }
        }
    }

    private class JoinAndReadyNotificationCallBack implements CallBack {

        private String memberID;

        public JoinAndReadyNotificationCallBack(String memberID) {
            this.memberID = memberID;
        }

        public void processNotification(Signal notification) {
            if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
                myLogger.log(TESTDEFAULTLOGLEVEL, "***JoinAndReadyNotification received from: " + notification.getMemberToken());
            }
            if (!(notification instanceof JoinedAndReadyNotificationSignal)) {
                if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
                    myLogger.log(TESTDEFAULTLOGLEVEL, "received unknown notification type:" + notification + " from:" + notification.getMemberToken());
                }
            } else {
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
                if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
                    myLogger.log(TESTDEFAULTLOGLEVEL, "numberOfJoinAndReady received so far is: " + numberOfJoinAndReady.get());
                }
            }
        }
    }

    private class PlannedShutdownNotificationCallBack implements CallBack {

        public void processNotification(Signal notification) {
            if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
                myLogger.log(TESTDEFAULTLOGLEVEL, "***PlannedShutdown received from: " + notification.getMemberToken());
            }
            if (!(notification instanceof PlannedShutdownSignal)) {
                myLogger.log(Level.SEVERE, "received unknown notification type:" + notification + " from:" + notification.getMemberToken());
            } else {
                // determine how many core members are ready to begin testing
                PlannedShutdownSignal readySignal = (PlannedShutdownSignal) notification;
                synchronized (numberOfPlannedShutdown) {
                    numberOfPlannedShutdown.getAndIncrement();
                    if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
                        myLogger.log(TESTDEFAULTLOGLEVEL, "numberOfPlannedShutdown received so far is: " + numberOfPlannedShutdown.get());
                    }
                    if (numberOfPlannedShutdown.get() == numberOfInstances) {
                        numberOfPlannedShutdown.notify();
                    }
                }
            }
        }
    }

    private class MessageCallBack implements CallBack {

        private String memberID;

        public MessageCallBack(String memberID) {
            this.memberID = memberID;
        }

        public void processNotification(Signal notification) {
            long starttime = System.currentTimeMillis();
            String from = notification.getMemberToken();
            // gmsLogger.log(Level.INFO, "***Message received from: " + notification.getMemberToken());
            if (!(notification instanceof MessageSignal)) {
                myLogger.log(Level.SEVERE, "received unknown notification type:" + notification + " from:" + from);
            } else {
                //synchronized (this) {
                MessageSignal messageSignal = (MessageSignal) notification;
                String msgString = new String(messageSignal.getMessage());

                // this is an instance
                if (!memberID.equalsIgnoreCase("server")) {
                    if (!firstMsgReceived.get()) {
                        firstMsgReceived.set(true);
                        receiveStartTime = new GregorianCalendar();
                        if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
                            myLogger.log(TESTDEFAULTLOGLEVEL, "Receive start time: " + receiveStartTime);
                        }
                    }

                    // is this a done message
                    if (msgString.contains(DONE_SENDING)) {

                        if (!waitingForDoneSendingFrom.contains(from)) {
                            waitingForDoneSendingFrom.remove(from);
                        }
                        if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
                            // forward this message onto the server
                            myLogger.log(TESTDEFAULTLOGLEVEL, "Received " + DONE_SENDING + " message from " + from + ", waiting for (" + waitingForDoneSendingFrom.toString() + ")");
                        }
                        // if we have received a DONE message from each of the other instances then stop timing
                        if ((waitingForDoneSendingFrom.size() == 0)) {
                            receiveEndTime = new GregorianCalendar();
                            if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
                                myLogger.log(TESTDEFAULTLOGLEVEL, "Receive end time: " + receiveEndTime);
                            }


                            if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
                                // forward this message onto the server
                                myLogger.log(TESTDEFAULTLOGLEVEL, "Received all " + DONE_SENDING + " messages from other instances, now sending " + DONE_RECEIVING + " message to server");
                            }
                            try {
                                gms.getGroupHandle().sendMessage("server", "TestComponent", DONE_RECEIVING.getBytes());
                            } catch (GMSException e) {
                                if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
                                    myLogger.log(TESTDEFAULTLOGLEVEL, "Exception occured while sending message (" + DONE_RECEIVING + ") to server" + e, e);
                                }
                                //retry the send up to 3 times
                                int retryCount = 1;
                                for (; retryCount <= 3; retryCount++) {
                                    try {
                                        myLogger.log(Level.WARNING, "Need to retry, sleeping 10 ms");
                                        sleep(thinktime);
                                        myLogger.log(Level.WARNING, "Retry [" + retryCount + "] time(s) to send message (" + DONE_RECEIVING + ")");
                                        gms.getGroupHandle().sendMessage("server", "TestComponent", DONE_RECEIVING.getBytes());
                                        break; // if successful
                                    } catch (GMSException ge1) {
                                        myLogger.log(Level.WARNING, "Exception occured while sending message (" + DONE_RECEIVING + ") to server : retry (" + retryCount + ")" + ge1, ge1);
                                    }
                                }
                                if (retryCount > 3) {
                                    myLogger.log(Level.SEVERE, "Retry count exceeded 3 times while trying to send message (" + DONE_RECEIVING + ") to server");
                                }
                            }
                        }
                    } else if (msgString.contains(START_TESTING)) {
                        synchronized (startTesting) {
                            startTesting.notifyAll();
                        }
                    } else {
                        // it is not a done message so process it
                        byte[] msg = messageSignal.getMessage();
                        ByteBuffer buf = ByteBuffer.wrap(msg);
                        long objectID = buf.getLong(0);
                        long msgID = buf.getLong(8);
                        int to = buf.getInt(16);
                        int msgFrom = buf.getInt(20);
                        String payload = new String(msg, 24, msg.length - 24);
                        String shortPayLoad = payload;
                        if (shortPayLoad.length() > 10) {
                            shortPayLoad = shortPayLoad.substring(0, 10) + "..." + shortPayLoad.substring(shortPayLoad.length() - 10, shortPayLoad.length());
                        }
                        if (myLogger.isLoggable(Level.FINE)) {
                            myLogger.log(Level.FINE, memberID + " Received msg:" + displayMsg(msg));
                        }
                        if (msgID > 0) {
                            // keep track of the objectIDs
                            // if the INSTANCE does not exist in the map, create it.
                            ConcurrentHashMap<Long, String> objects = payloads_received.get(msgFrom);
                            if (objects == null) {
                                objects = new ConcurrentHashMap<Long, String>();
                            }
                            //TODO
                            objects.put(objectID, payload);
                            payloads_received.put(msgFrom, objects);
                            if (validateAllPayloads) {
                                if (payload.equals(expectedPayload)) {
                                    validatedMessages++;
                                } else {
                                    myLogger.severe("Payload did not match for objId:version[" + objectID + ":" + msgID + "] from: instance" + from + " actual Payload[objectNum]:" + payload);
                                    payloadErrors++;
                                }
                            }
                            // keep track of the msgIDs
                            // SINCE THIS CODE WILL ONLY WORK FOR A STABLE BUDDY REPLICATION ENVIRONMENT WHERE
                            // THERE IS NO KILLING OR SHUTDOWN OF INSTANCES, I AM COMMENTING IT OUT.
                            //msgIDs_received.put(objectID + ":" + msgID+":"+msgFrom,"");
                        }
                    }
                } else {
                    // this is the server
                    if (msgString.contains(DONE_RECEIVING)) {
                        if (!waitingForDoneReceivingFrom.contains(from)) {
                            waitingForDoneReceivingFrom.remove(from);
                        }
                        if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
                            myLogger.log(TESTDEFAULTLOGLEVEL, "Received " + DONE_RECEIVING + " message from:" + from + ", waiting for (" + waitingForDoneReceivingFrom.toString().replace("=", "") + ")");
                        }
                    } else if (msgString.contains(READY_TO_TEST)) {
                        if (!receivedReadyToTestFrom.contains(from)) {
                            receivedReadyToTestFrom.put(from, "");
                        }
                        if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
                            myLogger.log(TESTDEFAULTLOGLEVEL, "Received " + READY_TO_TEST + " message (" + msgString + ") from:" + from + ", waiting for (" + receivedReadyToTestFrom.toString().replace("=", "") + ")");
                        }
                        if (receivedReadyToTestFrom.size() == numberOfInstances) {
                            if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
                                myLogger.log(TESTDEFAULTLOGLEVEL, "Broadcast " + START_TESTING + " to all members");
                            }
                            try {
                                gms.getGroupHandle().sendMessage("TestComponent", START_TESTING.getBytes());
                            } catch (GMSException e) {
                                if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
                                    myLogger.log(TESTDEFAULTLOGLEVEL, "Exception occured while broadcasting (" + START_TESTING + ") message to all members" + e, e);
                                }
                                //retry the send up to 3 times
                                int retryCount = 1;
                                for (; retryCount <= 3; retryCount++) {
                                    try {
                                        myLogger.log(Level.WARNING, "Need to retry, sleeping 10 ms");
                                        sleep(thinktime);
                                        myLogger.log(Level.WARNING, "Retry [" + retryCount + "] time(s) to send message (" + START_TESTING + ")");
                                        gms.getGroupHandle().sendMessage("TestComponent", START_TESTING.getBytes());
                                        break; // if successful
                                    } catch (GMSException ge1) {
                                        myLogger.log(Level.WARNING, "Exception occured while broadcasting (" + START_TESTING + ") to all members" + ge1, ge1);
                                    }
                                }
                                if (retryCount > 3) {
                                    myLogger.log(Level.SEVERE, "Retry count exceeded 3 times while trying to broadcast (" + START_TESTING + ") to all members");
                                }
                            }
                        }
                    }
                }
                //}
            }
            long duration = System.currentTimeMillis() - starttime;
            if (duration > 5000) {
                System.out.println("WARNING: exceeded 5 seconds processing incoming message");
            }
            number_of_notifications.getAndIncrement();
            totalduration.getAndAdd(duration);
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

    public static byte[] createMsg(long objectID, long msgID, int to, int from, int payloads_receivedize) {
        // create the message to be sent
        byte[] b1 = long2bytearray(objectID);
        byte[] b2 = long2bytearray(msgID);
        byte[] b3 = int2bytearray(to);
        byte[] b4 = int2bytearray(from);
        byte[] b5 = new byte[1];
        int pls = payloads_receivedize - (b1.length + b2.length + b3.length + b4.length);
        b5 = createPayload(pls);
        int msgSize = b1.length + b2.length + b3.length + b4.length + b5.length;
        byte[] msg = new byte[(int) msgSize];
        int j = 0;
        for (int i = 0; i
                < b1.length; i++) {
            msg[j++] = b1[i];
        }
        for (int i = 0; i
                < b2.length; i++) {
            msg[j++] = b2[i];
        }
        for (int i = 0; i
                < b3.length; i++) {
            msg[j++] = b3[i];
        }
        for (int i = 0; i
                < b4.length; i++) {
            msg[j++] = b4[i];
        }
        for (int i = 0; i
                < b5.length; i++) {
            msg[j++] = b5[i];
        }
        return msg;
    }

    public static String displayMsg(byte[] msg) {
        StringBuffer sb = new StringBuffer(60);
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
        b = new byte[size];
        b[0] = 'a';
        int k = 1;
        for (; k
                < size - 1; k++) {
            b[k] = 'X';
        }
        b[k] = 'z';
        return b;
    }

    public static void sleep(long i) {
        try {
            Thread.sleep(i);
        } catch (InterruptedException ex) {
        }
    }

    public static void setupLogHandler(Level l) {
        final ConsoleHandler consoleHandler = new ConsoleHandler();
        try {
            consoleHandler.setLevel(Level.ALL);
            consoleHandler.setFormatter(new NiceLogFormatter());
        } catch (SecurityException e) {
            new ErrorManager().error(
                    "Exception caught in setting up ConsoleHandler ",
                    e, ErrorManager.GENERIC_FAILURE);
        }
        myLogger.addHandler(consoleHandler);
        myLogger.setUseParentHandlers(false);
        //final String level = System.getProperty("LOG_LEVEL", "INFO");
        //myLogger.setLevel(Level.parse(level));
        myLogger.setLevel(l);
    }
}

