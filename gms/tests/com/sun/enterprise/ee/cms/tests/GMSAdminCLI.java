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

package com.sun.enterprise.ee.cms.tests;

import com.sun.enterprise.ee.cms.core.*;
import com.sun.enterprise.ee.cms.impl.base.Utility;
import com.sun.enterprise.ee.cms.impl.client.JoinNotificationActionFactoryImpl;
import com.sun.enterprise.ee.cms.impl.client.JoinedAndReadyNotificationActionFactoryImpl;
import com.sun.enterprise.ee.cms.impl.client.MessageActionFactoryImpl;
import com.sun.enterprise.ee.cms.logging.GMSLogDomain;
import com.sun.enterprise.ee.cms.logging.NiceLogFormatter;
import com.sun.enterprise.ee.cms.spi.MemberStates;
import com.sun.enterprise.mgmt.transport.grizzly.GrizzlyUtil;
import java.util.Properties;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.ConsoleHandler;
import java.util.logging.ErrorManager;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GMSAdminCLI implements CallBack {
//public class ApplicationAdmin {

    private static final Logger gmsLogger = GMSLogDomain.getLogger(GMSLogDomain.GMS_LOGGER);
    private static final Level GMSDEFAULTLOGLEVEL = Level.WARNING;
    private static final Logger myLogger = java.util.logging.Logger.getLogger("GMSAdminCLI");
    private static final Level TESTDEFAULTLOGLEVEL = Level.INFO;
    private static GroupManagementService gms = null;
    static String memberID = GMSAdminConstants.ADMINCLI;
    static String groupName = null;
    static String memberName = null;
    static MemberStates whichState = MemberStates.UNKNOWN;
    static String command = null;
    private static AtomicBoolean receivedReply = new AtomicBoolean(false);
    private String replyMsg = null;
    private String replyFrom = null;
    private List<String> failToReceiveReplyFrom = null;
    private boolean receivedStopClusterReply = false;
    private AtomicBoolean receivedJoinOrJoinedAndReady = new AtomicBoolean(false);
    private long currentTime = 0;
    private static boolean resend = false;
    private static int resendCount = 0;

    public static void main(String[] args) {
        if (args[0].equalsIgnoreCase("-h")) {
            usage();
        }
        if (args.length < 1 || args.length > 3) {
            System.err.println("ERROR: Incorrect number of arguments");
            usage();
        }
        command = args[0].toLowerCase();
        groupName = args[1];
        if ((!command.equals("list"))
                && (!command.equals("stopc"))
                && (!command.equals("stopm"))
                && (!command.equals("killa"))
                && (!command.equals("killm"))
                && (!command.equals("state"))
                && (!command.equals("test"))
                && (!command.equals("waits"))) {
            System.err.println("ERROR: Invalid command specified [" + command + "]");
            usage();
        }
        if (groupName == null || groupName.length() == 0) {
            System.err.println("ERROR: missing groupName");
        }
        if (args.length == 3) {
            if (command.equals("state")) {
                try {
                    whichState = MemberStates.valueOf(args[2]);
                } catch (IllegalArgumentException iae) {
                    System.err.println("ERROR: Invalid memberstate specified, it must be one of the following:");
                    for (MemberStates c : MemberStates.values()) {
                        System.err.print(c + ", ");
                    }
                    System.err.println("\n");
                    usage();
                }
            } else {
                memberName = args[2];
                if (!memberName.contains(GMSAdminConstants.ADMINNAME) && !memberName.contains(GMSAdminConstants.INSTANCEPREFIX)) {
                    System.err.println("ERROR: Invalid memberName specified [" + memberName + "], must be either server or contain instance");
                    usage();
                }
            }
        }
        if (command.equals("stopm") || command.equals("killm")) {
            if (memberName == null || memberName.length() == 0) {
                System.err.println("ERROR: missing memberName");
                usage();
            }
        }
        if (command.equals("killa") && memberName != null && memberName.length() > 1) {
            System.err.println("WARNING: Ignoring invalid argument [" + memberName + "]");
            memberName = null;
        }
        String optArgs = System.getProperty("OPTARGS", null);
        if (optArgs != null) {
            if (optArgs.contains("-retry")) {
                resend = true;
            }
        }
        // this configures the formatting of the gms log output
        Utility.setLogger(gmsLogger);
        Utility.setupLogHandler();
        // this sets the grizzly log level
        GrizzlyUtil.getLogger().setLevel(Level.WARNING);
        // this configures the formatting of the myLogger output
        GMSAdminCLI.setupLogHandler();
        try {
            gmsLogger.setLevel(Level.parse(System.getProperty("LOG_LEVEL", GMSDEFAULTLOGLEVEL.toString())));
        } catch (Exception e) {
            gmsLogger.setLevel(GMSDEFAULTLOGLEVEL);
        }
        gmsLogger.info("GMS Logging using log level of:" + gmsLogger.getLevel());
        try {
            myLogger.setLevel(Level.parse(System.getProperty("TEST_LOG_LEVEL", TESTDEFAULTLOGLEVEL.toString())));
        } catch (Exception e) {
            myLogger.setLevel(TESTDEFAULTLOGLEVEL);
        }
        myLogger.info("Test Logging using log level of:" + myLogger.getLevel());
        if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
            myLogger.log(TESTDEFAULTLOGLEVEL, "Command=" + command);
            myLogger.log(TESTDEFAULTLOGLEVEL, "GroupName=" + groupName);
            myLogger.log(TESTDEFAULTLOGLEVEL, "MemberName=" + memberName);
            myLogger.log(TESTDEFAULTLOGLEVEL, "RetrySend=" + resend);
        }
        if (command.equals("state")) {
            if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
                myLogger.log(TESTDEFAULTLOGLEVEL, "State=" + whichState.toString());
            }
        }
        GMSAdminCLI appAdmin = new GMSAdminCLI();
        appAdmin.registerAndJoinCluster();
        appAdmin.execute();
        leaveGroupAndShutdown();
    }

    public static void usage() {
        System.out.println(new StringBuffer().append("USAGE: java ").append(" -Dcom.sun.management.jmxremote").append(" -DSHOAL_GROUP_COMMUNICATION_PROVIDER=grizzly").append(" -DTCPSTARTPORT=9060").append(" -DTCPENDPORT=9089").append(" -DMULTICASTADDRESS=229.9.1.1").append(" -DMULTICASTPORT=2299").append("-DTEST_LOG_LEVEL=WARNING").append("-DLOG_LEVEL=WARNING").append("-DOPTARGS=OPTARGS").append(" -cp shoal-gms-tests.jar:shoal-gms.jar:grizzly-framework.jar:grizzly-utils.jar").append(" com.sun.enterprise.ee.cms.tests.GMSAdminCLI").append(" ARGUMENTS").toString());
        System.out.println("ARGUMENT usages:");
        System.out.println("        list groupName [memberName(default is all)]  - list member(s)");
        System.out.println("        stopc groupName - stops a cluster");
        System.out.println("        stopm groupName memberName - stop a member");
        System.out.println("        killm groupName memberName - kill a member");
        System.out.println("        killa groupName - kills all members of the cluster ");
        System.out.println("        waits groupName - wait for the cluster to complete startup");
        System.out.println("        test groupName - start testing and wait until its complete");
        System.out.println("        state groupName gmsmemberstate  - list member(s) in the specific state");
        System.exit(0);
    }

    private void registerAndJoinCluster() {
        if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
            myLogger.log(TESTDEFAULTLOGLEVEL, "Registering for group event notifications");
        }
        gms = initializeGMS(memberID, groupName, GroupManagementService.MemberType.SPECTATOR);
        gms.addActionFactory(new MessageActionFactoryImpl(this), GMSAdminConstants.ADMINCLI);
        gms.addActionFactory(new JoinNotificationActionFactoryImpl(this));
        gms.addActionFactory(new JoinedAndReadyNotificationActionFactoryImpl(this));
        try {
            gms.join();
        } catch (GMSException e) {
            myLogger.severe("Exception occured :" + e);
            System.exit(1);
        }
        // give time to join group and receive cluster status
        sleep(5);
    }

    private void execute() {
        if (command.equals("list")) {
            if (memberName == null) {
                // all members in the group
                List<String> members = gms.getGroupHandle().getAllCurrentMembers();
                StringBuffer sb = new StringBuffer();
                for (String _memberName : members) {
                    if (!_memberName.equals(GMSAdminConstants.ADMINCLI)) {
                        sb.append(" ");
                        sb.append(_memberName);
                        sb.append(":");
                        sb.append(gms.getGroupHandle().getMemberState(_memberName));
                    }
                }
                if (sb.length() > 0) {
                    String s = sb.toString();
                    if (!s.equals(" ")) {
                        s = s.replace(' ', ',');
                        if (s.charAt(0) == ',') {
                            s = s.substring(1);
                        }
                        displayMembers(s);
                        displaySuccessful();
                    } else {
                        displayUnsuccessful();
                    }
                } else {
                    displayUnsuccessful();
                }
            } else if (memberName.contains("instance")) {
                // only a specfic instance
                List<String> members = gms.getGroupHandle().getCurrentCoreMembers();
                StringBuffer sb = new StringBuffer(" ");
                for (String _memberName : members) {
                    if (_memberName.equals(memberName)) {
                        sb.append(_memberName);
                        sb.append(":");
                        sb.append(gms.getGroupHandle().getMemberState(_memberName));
                    }
                }
                if (sb.length() > 0) {
                    String s = sb.toString();
                    if (!s.equals(" ")) {
                        s = s.replace(' ', ',');
                        if (s.charAt(0) == ',') {
                            s = s.substring(1);
                        }
                        displayMembers(s);
                        displaySuccessful();
                    } else {
                        displayUnsuccessful();
                    }
                } else {
                    displayUnsuccessful();
                }
            } else {
                // only the das
                List<String> members = gms.getGroupHandle().getAllCurrentMembers();
                StringBuffer sb = new StringBuffer(" ");
                for (String _memberName : members) {
                    if (_memberName.equals(GMSAdminConstants.ADMINNAME)) {
                        sb.append(_memberName);
                        sb.append(":");
                        sb.append(gms.getGroupHandle().getMemberState(_memberName));
                    }
                }
                if (sb.length() > 0) {
                    String s = sb.toString();
                    if (!s.equals(" ")) {
                        s = s.replace(' ', ',');
                        if (s.charAt(0) == ',') {
                            s = s.substring(1);
                        }
                        displayMembers(s);
                        displaySuccessful();
                    } else {
                        displayUnsuccessful();
                    }
                } else {
                    displayUnsuccessful();
                }
            }
        } else if (command.equals("stopc")) {
            replyMsg = GMSAdminConstants.STOPCLUSTERREPLY;
            replyFrom = gms.getGroupHandle().getGroupLeader();
            boolean sendWorked = true;
            do {
                int retryCount = 0;
                try {
                    if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
                        myLogger.log(TESTDEFAULTLOGLEVEL, "Broadcast stopcluster message to master");
                    }
                    // broadcast the shutdown cluster message, only the master should react to this
                    gms.getGroupHandle().sendMessage(GMSAdminConstants.ADMINAGENT, GMSAdminConstants.STOPCLUSTER.getBytes());
                    if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
                        myLogger.log(TESTDEFAULTLOGLEVEL, "Done broadcasting stopcluster message to master");
                    }
                } catch (GMSException e) {
                    myLogger.warning("Exception occurred with broadcasting stopcluster message:" + e);
                    //retry the send up to 3 times
                    for (int i = 1; i <= 3; i++) {
                        try {
                            sleep(3);
                            myLogger.warning("Retry [" + i + "] time(s) to send message (" + GMSAdminConstants.STOPCLUSTER + ")");
                            gms.getGroupHandle().sendMessage(GMSAdminConstants.ADMINAGENT, GMSAdminConstants.STOPCLUSTER.getBytes());
                            if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
                                myLogger.log(TESTDEFAULTLOGLEVEL, "Done broadcasting stopcluster message to master");
                            }
                            sendWorked = true;
                            break; // if successful
                        } catch (GMSException ge1) {
                            myLogger.warning("Exception occurred while resending message: retry (" + i + ") for (" + GMSAdminConstants.STOPCLUSTER + ") : " + ge1);
                        }
                        sendWorked = false;
                    }
                }
                if (sendWorked) {
                    synchronized (receivedReply) {
                        try {
                            receivedReply.wait(10000); // wait till we receive reply OR 10 seconds
                        } catch (InterruptedException ie) {
                        }
                    }
                }
                resendCount++;
                if (sendWorked && resend && resendCount <= 2 && receivedReply.get() == false) {
                    myLogger.info("No Reply received, retry enabled so trying again");
                }
            } while (sendWorked && resend && resendCount <= 2 && receivedReply.get() == false);
            if (receivedReply.get() == false) {
                if (resend) {
                    myLogger.severe(replyMsg + " was never received even with retry enabled from:" + replyFrom);
                } else {
                    myLogger.severe(replyMsg + " was never received from:" + replyFrom);
                }
                displayUnsuccessful();
            } else {
                displaySuccessful();
            }
        } else if (command.equals("stopm")) {
            replyMsg = GMSAdminConstants.STOPINSTANCEREPLY;
            replyFrom = memberName;
            boolean sendWorked = true;
            do {
                try {
                    if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
                        myLogger.log(TESTDEFAULTLOGLEVEL, "Sending stopmember message to:" + memberName);
                    }
                    gms.getGroupHandle().sendMessage(memberName, GMSAdminConstants.ADMINAGENT, GMSAdminConstants.STOPINSTANCE.getBytes());
                    if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
                        myLogger.log(TESTDEFAULTLOGLEVEL, "Done sending stopmember message to:" + memberName);
                    }
                } catch (GMSException e) {
                    myLogger.warning("Exception occurred while sending stopmember message:" + e);
                    //retry the send up to 3 times
                    for (int i = 0; i < 3; i++) {
                        try {
                            sleep(3);
                            myLogger.warning("Retry [" + i + "] time(s) to send message (" + GMSAdminConstants.STOPINSTANCE + ")");
                            gms.getGroupHandle().sendMessage(memberName, GMSAdminConstants.ADMINAGENT, GMSAdminConstants.STOPINSTANCE.getBytes());
                            if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
                                myLogger.log(TESTDEFAULTLOGLEVEL, "Done sending stopmember message to:" + memberName);
                            }
                            sendWorked = true;
                            break; // if successful break out of loop
                        } catch (GMSException ge1) {
                            myLogger.warning("Exception occurred while resending message: retry (" + i + ") for (" + GMSAdminConstants.STOPINSTANCE + ") : " + ge1);
                        }
                        sendWorked = false;
                    }
                }
                if (sendWorked) {
                    synchronized (receivedReply) {
                        try {
                            receivedReply.wait(10000); // wait till we receive reply OR 10 seconds
                        } catch (InterruptedException ie) {
                        }
                    }
                }
                resendCount++;
                if (sendWorked && resend && resendCount <= 2 && receivedReply.get() == false) {
                    myLogger.info("No Reply received [" + resendCount + "] time(s),retry enabled so trying again");
                }
            } while (sendWorked && resend && resendCount <= 2 && receivedReply.get() == false);
            if (receivedReply.get() == false) {
                if (resend) {
                    myLogger.severe(replyMsg + " was never received even with retry enabled from:" + replyFrom);
                } else {
                    myLogger.severe(replyMsg + " was never received from:" + replyFrom);
                }
                displayUnsuccessful();
            } else {
                displaySuccessful();
            }
        } else if (command.equals("killm")) {
            replyMsg = GMSAdminConstants.KILLINSTANCEREPLY;
            replyFrom = memberName;
            boolean sendWorked = true;
            do {
                try {
                    if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
                        myLogger.log(TESTDEFAULTLOGLEVEL, "Sending killmember message to:" + memberName);
                    }
                    gms.getGroupHandle().sendMessage(memberName, GMSAdminConstants.ADMINAGENT, GMSAdminConstants.KILLINSTANCE.getBytes());
                    if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
                        myLogger.log(TESTDEFAULTLOGLEVEL, "Done sending killmember message to:" + memberName);
                    }
                } catch (GMSException e) {
                    myLogger.warning("Exception occurred while sending killmember message:" + e);
                    //retry the send up to 3 times
                    for (int i = 1; i <= 3; i++) {
                        try {
                            sleep(3);
                            myLogger.warning("Retry [" + i + "] time(s) to send message (" + GMSAdminConstants.KILLINSTANCE + ")");
                            gms.getGroupHandle().sendMessage(memberName, GMSAdminConstants.ADMINAGENT, GMSAdminConstants.KILLINSTANCE.getBytes());
                            if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
                                myLogger.log(TESTDEFAULTLOGLEVEL, "Done sending killmember message to:" + memberName);
                            }
                            sendWorked = true;
                            break; // if successful
                        } catch (GMSException ge1) {
                            myLogger.warning("Exception occurred while resending message: retry (" + i + ") for (" + GMSAdminConstants.KILLINSTANCE + ") : " + ge1);
                        }
                        sendWorked = false;
                    }
                }
                if (sendWorked) {
                    synchronized (receivedReply) {
                        try {
                            receivedReply.wait(10000); // wait till we receive reply OR 10 seconds
                        } catch (InterruptedException ie) {
                        }
                    }
                }
                resendCount++;
                if (sendWorked && resend && resendCount <= 2 && receivedReply.get() == false) {
                    myLogger.info("No Reply received, retry enabled so trying again");
                }
            } while (sendWorked && resend && resendCount <= 2 && receivedReply.get() == false);
            if (receivedReply.get() == false) {
                if (resend) {
                    myLogger.severe(replyMsg + " was never received even with retry enabled from:" + replyFrom);
                } else {
                    myLogger.severe(replyMsg + " was never received from:" + replyFrom);
                }
                displayUnsuccessful();
            } else {
                displaySuccessful();
            }
        } else if (command.equals("killa")) {
            if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
                myLogger.log(TESTDEFAULTLOGLEVEL, "Executing killa");
            }
            List<String> members = gms.getGroupHandle().getAllCurrentMembers();
            List<String> failedToReceiveReplyFrom = gms.getGroupHandle().getAllCurrentMembers();
            // remove are selves from the lists
            members.remove(GMSAdminConstants.ADMINCLI);
            failedToReceiveReplyFrom.remove(GMSAdminConstants.ADMINCLI);
            if (members.size() > 0) {
                if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
                    myLogger.log(TESTDEFAULTLOGLEVEL, "Current members are:" + members.toString());
                }
                String groupLeader = gms.getGroupHandle().getGroupLeader();
                for (String _memberName : members) {
                    if (!_memberName.equals(GMSAdminConstants.ADMINCLI)) {
                        // do everyone else first then the groupLeader
                        if (!_memberName.equals(groupLeader)) {
                            replyMsg = GMSAdminConstants.KILLINSTANCEREPLY;
                            replyFrom = _memberName;
                            boolean sendWorked = true;
                            do {
                                try {
                                    if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
                                        myLogger.log(TESTDEFAULTLOGLEVEL, "Sending killinstance message to:" + _memberName);
                                    }
                                    gms.getGroupHandle().sendMessage(_memberName, GMSAdminConstants.ADMINAGENT, GMSAdminConstants.KILLINSTANCE.getBytes());
                                    if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
                                        myLogger.log(TESTDEFAULTLOGLEVEL, "Done sending killmember message to:" + memberName);
                                    }
                                } catch (GMSException e) {
                                    myLogger.warning("Exception occurred while sending killmember message:" + e);
                                    //retry the send up to 3 times
                                    for (int i = 1; i <= 3; i++) {
                                        try {
                                            sleep(3);
                                            myLogger.warning("Retry [" + i + "] time(s) to send message (" + GMSAdminConstants.KILLINSTANCE + ")");
                                            gms.getGroupHandle().sendMessage(GMSAdminConstants.ADMINAGENT, GMSAdminConstants.KILLINSTANCE.getBytes());
                                            if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
                                                myLogger.log(TESTDEFAULTLOGLEVEL, "Done sending killmember message to:" + memberName);
                                            }
                                            sendWorked = true;
                                            break; // if successful
                                        } catch (GMSException ge1) {
                                            myLogger.warning("Exception occurred while resending message: retry (" + i + ") for (" + GMSAdminConstants.KILLINSTANCE + ") : " + ge1);
                                        }
                                        sendWorked = false;
                                    }
                                }
                                if (sendWorked) {
                                    synchronized (receivedReply) {
                                        try {
                                            receivedReply.wait(10000); // wait till we receive reply OR 10 seconds
                                        } catch (InterruptedException ie) {
                                        }
                                    }
                                }
                                resendCount++;
                                if (sendWorked && resend && resendCount <= 2 && receivedReply.get() == false) {
                                    myLogger.info("No Reply received, retry enabled so trying again");
                                }
                            } while (sendWorked && resend && resendCount <= 2 && receivedReply.get() == false);
                            if (receivedReply.get()) {
                                failedToReceiveReplyFrom.remove(replyFrom);
                            } else {
                                if (resend) {
                                    myLogger.severe(replyMsg + " was never received even with retry enabled from:" + replyFrom);
                                } else {
                                    myLogger.severe(replyMsg + " was never received from:" + replyFrom);
                                }
                            }
                        }
                    }
                }
                // now do group leader
                replyMsg = GMSAdminConstants.KILLINSTANCEREPLY;
                replyFrom = groupLeader;
                boolean sendWorked = true;
                do {
                    try {
                        if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
                            myLogger.log(TESTDEFAULTLOGLEVEL, "Sending killinstance message to:" + groupLeader);
                        }
                        gms.getGroupHandle().sendMessage(groupLeader, GMSAdminConstants.ADMINAGENT, GMSAdminConstants.KILLINSTANCE.getBytes());
                        if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
                            myLogger.log(TESTDEFAULTLOGLEVEL, "Done sending killmember message to:" + memberName);
                        }
                    } catch (GMSException e) {
                        myLogger.warning("Exception occurred while sending killmember message:" + e);
                        //retry the send up to 3 times
                        for (int i = 1; i <= 3; i++) {
                            try {
                                sleep(3);
                                myLogger.warning("Retry [" + i + "] time(s) to send message (" + GMSAdminConstants.KILLINSTANCE + ")");
                                gms.getGroupHandle().sendMessage(groupLeader, GMSAdminConstants.ADMINAGENT, GMSAdminConstants.KILLINSTANCE.getBytes());
                                if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
                                    myLogger.log(TESTDEFAULTLOGLEVEL, "Done sending killmember message to:" + memberName);
                                }
                                sendWorked = true;
                                break; // if successful
                            } catch (GMSException ge1) {
                                myLogger.warning("Exception occurred while resending message: retry (" + i + ") for (" + GMSAdminConstants.KILLINSTANCE + ") : " + ge1);
                            }
                            sendWorked = false;
                        }
                    }
                    if (sendWorked) {
                        synchronized (receivedReply) {
                            try {
                                receivedReply.wait(10000); // wait till we receive reply OR 10 seconds
                            } catch (InterruptedException ie) {
                            }
                        }
                    }
                    resendCount++;
                    if (sendWorked && resend && resendCount <= 2 && receivedReply.get() == false) {
                        myLogger.info("No Reply received, retry enabled so trying again");
                    }
                } while (sendWorked && resend && resendCount <= 2 && receivedReply.get() == false);
                if (receivedReply.get()) {
                    failedToReceiveReplyFrom.remove(replyFrom);
                } else {
                    if (resend) {
                        myLogger.severe(replyMsg + " was never received even with retry enabled from:" + replyFrom);
                    } else {
                        myLogger.severe(replyMsg + " was never received from:" + replyFrom);
                    }
                }
                if (failedToReceiveReplyFrom.size() > 0) {
                    myLogger.severe("The members that did not stop or did not reply in time are:" + failedToReceiveReplyFrom.toString());
                    displayUnsuccessful();
                } else {
                    displaySuccessful();
                }
            } else {
                if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
                    myLogger.log(TESTDEFAULTLOGLEVEL, "No members exist to kill");
                }
                displaySuccessful();
            }
        } else if (command.equals("state")) {
            // all members in the group
            int count = 0;
            List<String> members = gms.getGroupHandle().getAllCurrentMembers();
            StringBuffer sb = new StringBuffer();
            for (String _memberName : members) {
                if (!_memberName.equals(GMSAdminConstants.ADMINCLI)) {
                    if (gms.getGroupHandle().getMemberState(_memberName).equals(whichState)) {
                        sb.append(" ");
                        sb.append(_memberName);
                        sb.append(":");
                        sb.append(gms.getGroupHandle().getMemberState(_memberName));
                        count++;
                    }
                }
            }
            if (sb.length() > 0) {
                String s = sb.toString();
                s = s.replace(' ', ',');
                if (s.charAt(0) == ',') {
                    s = s.substring(1);
                }
                displayMembers(s);
                displayCount(count);
                displaySuccessful();
            } else {
                displayCount(count);
                displaySuccessful();
            }
        } else if (command.equals("waits")) {
            // all members in the group
            boolean sendWorked = true;
            int count = 0;
            List<String> members = gms.getGroupHandle().getAllCurrentMembers();
            // if there is someone else in the cluster besides ourselves
            if (members.size() > 1) {
                replyMsg = GMSAdminConstants.ISSTARTUPCOMPLETEREPLY;
                replyFrom = GMSAdminConstants.ADMINNAME;
                try {
                    if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
                        myLogger.log(TESTDEFAULTLOGLEVEL, "Sending isstartupcomplete message to:" + GMSAdminConstants.ADMINNAME);
                    }
                    gms.getGroupHandle().sendMessage(GMSAdminConstants.ADMINNAME, GMSAdminConstants.ADMINAGENT, GMSAdminConstants.ISSTARTUPCOMPLETE.getBytes());
                    if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
                        myLogger.log(TESTDEFAULTLOGLEVEL, "Done sending isstartupcomplete message to:" + GMSAdminConstants.ADMINNAME);
                    }
                } catch (GMSException e) {
                    myLogger.warning("Exception occurred while sending isstartupcomplete message:" + e);
                    //retry the send up to 3 times
                    for (int i = 1; i <= 3; i++) {
                        try {
                            sleep(3);
                            myLogger.warning("Retry [" + i + "] time(s) to send message (" + GMSAdminConstants.ISSTARTUPCOMPLETE + ")");
                            gms.getGroupHandle().sendMessage(GMSAdminConstants.ADMINNAME, GMSAdminConstants.ADMINAGENT, GMSAdminConstants.ISSTARTUPCOMPLETE.getBytes());
                            if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
                                myLogger.log(TESTDEFAULTLOGLEVEL, "Done sending isstartupcomplete message to:" + GMSAdminConstants.ADMINNAME);
                            }
                            sendWorked = true;
                            break; // if successful
                        } catch (GMSException ge1) {
                            myLogger.warning("Exception occurred while resending message: retry (" + i + ") for (" + GMSAdminConstants.ISSTARTUPCOMPLETE + ") : " + ge1);
                        }
                        sendWorked = false;
                    }
                }
                if (sendWorked) {
                    synchronized (receivedReply) {
                        try {
                            receivedReply.wait(0); // wait till we receive reply
                        } catch (InterruptedException ie) {
                        }
                    }
                    displaySuccessful();
                } else {
                    myLogger.severe(replyMsg + " was never received from:" + replyFrom);
                    displayUnsuccessful();
                }
            } else {
                myLogger.severe("No members exist in cluster:" + groupName);
                displayUnsuccessful();
            }
        } else if (command.equals(
                "test")) {
            try {
                if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
                    myLogger.log(TESTDEFAULTLOGLEVEL, "Broadcast start testing message to " + GMSAdminConstants.TESTCOORDINATOR);
                } // broadcast the start testing message to all members
                gms.getGroupHandle().sendMessage(GMSAdminConstants.TESTCOORDINATOR, GMSAdminConstants.STARTTESTING.getBytes());
                if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
                    myLogger.log(TESTDEFAULTLOGLEVEL, "Done broadcasting start testing message to cluster");
                }
            } catch (GMSException e) {
                myLogger.warning("Exception occurred with broadcasting start testing message:" + e);
                //retry the send up to 3 times
                for (int i = 1; i
                        <= 3; i++) {
                    try {
                        sleep(3);
                        myLogger.warning("Retry [" + i + "] time(s) to send message (" + GMSAdminConstants.STARTTESTING + ")");
                        gms.getGroupHandle().sendMessage(GMSAdminConstants.TESTCOORDINATOR, GMSAdminConstants.STARTTESTING.getBytes());
                        break; // if successful
                    } catch (GMSException ge1) {
                        myLogger.warning("Exception occurred while resending message: retry (" + i + ") for (" + GMSAdminConstants.STARTTESTING + ") : " + ge1);
                    }
                }
            }
            replyMsg = GMSAdminConstants.TESTINGCOMPLETE;
            replyFrom = GMSAdminConstants.ADMINNAME;
            synchronized (receivedReply) {
                try {
                    receivedReply.wait(0); // wait till we receive reply
                } catch (InterruptedException ie) {
                }
            }
            displaySuccessful();
        }
    }

    private void displayMembers(String s) {
        System.out.println("\nMembers are:[" + s + "]\n");
    }

    private void displayCount(int count) {
        System.out.println("\nNumber of members:" + count + "\n");
    }

    private void displaySuccessful() {
        if (memberName == null) {
            System.out.println(command + " of group:" + groupName + " WAS SUCCESSFUL");
        } else {
            System.out.println(command + " of group:" + groupName + " member:" + memberName + " WAS SUCCESSFUL");
        }
    }

    private void displayUnsuccessful() {
        if (memberName == null) {
            System.out.println(command + " of group:" + groupName + " WAS UNSUCCESSFUL");
        } else {
            System.out.println(command + " of group:" + groupName + " member:" + memberName + " WAS UNSUCCESSFUL");
        }
    }

    private GroupManagementService initializeGMS(String memberID, String groupName, GroupManagementService.MemberType mType) {
        myLogger.fine("entering initializeGMS");
        Properties configProps = new Properties();
        String nomcast = System.getProperty("GMS_DISCOVERY_URI_LIST");
        if (nomcast != null) {
            configProps.put("DISCOVERY_URI_LIST", nomcast);
        }
        String ma = System.getProperty("MULTICASTADDRESS", "229.9.1.1");
        myLogger.config("MULTICASTADDRESS=" + ma);
        configProps.put(ServiceProviderConfigurationKeys.MULTICASTADDRESS.toString(), ma);
        String mp = System.getProperty("MULTICASTPORT", "2299");
        myLogger.config("MULTICASTPORT=" + mp);
        configProps.put(ServiceProviderConfigurationKeys.MULTICASTPORT.toString(), mp);
        myLogger.config("IS_BOOTSTRAPPING_NODE=false");
        configProps.put(ServiceProviderConfigurationKeys.IS_BOOTSTRAPPING_NODE.toString(), "false");
        String mmh = System.getProperty("MAX_MISSED_HEARTBEATS", "3");
        myLogger.config("MAX_MISSED_HEARTBEATS=" + mp);
        configProps.put(ServiceProviderConfigurationKeys.FAILURE_DETECTION_RETRIES.toString(), mmh);
        String hf = System.getProperty("HEARTBEAT_FREQUENCY", "2000");
        myLogger.config("HEARTBEAT_FREQUENCY=" + hf);
        configProps.put(ServiceProviderConfigurationKeys.FAILURE_DETECTION_TIMEOUT.toString(), hf);
        final String bindInterfaceAddress = System.getProperty("BIND_INTERFACE_ADDRESS");
        myLogger.config("BIND_INTERFACE_ADDRESS=" + bindInterfaceAddress);
        if (bindInterfaceAddress != null) {
            configProps.put(ServiceProviderConfigurationKeys.BIND_INTERFACE_ADDRESS.toString(), bindInterfaceAddress);
        }
        if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
            myLogger.log(TESTDEFAULTLOGLEVEL, "leaving initializeGMS");
        }
        return (GroupManagementService) GMSFactory.startGMSModule(
                memberID,
                groupName,
                mType,
                configProps);
    }

    private static void leaveGroupAndShutdown() {
        if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
            myLogger.log(TESTDEFAULTLOGLEVEL, "Shutting down gms " + gms + "for member: " + memberID);
        }
        gms.shutdown(GMSConstants.shutdownType.INSTANCE_SHUTDOWN);
    }

    public static void sleep(int i) {
        try {
            if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
                myLogger.log(TESTDEFAULTLOGLEVEL, "start sleeping");
            }
            Thread.sleep(i * 1000);
            if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
                myLogger.log(TESTDEFAULTLOGLEVEL, "done sleeping");
            }
        } catch (InterruptedException ex) {
        }
    }

    public synchronized void processNotification(final Signal notification) {
        final String from = notification.getMemberToken();
        if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
            myLogger.log(TESTDEFAULTLOGLEVEL, "Received a NOTIFICATION from member " + from);
        }
        if (notification instanceof MessageSignal) {
            MessageSignal messageSignal = (MessageSignal) notification;
            String msgString = new String(messageSignal.getMessage());
            if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
                myLogger.log(TESTDEFAULTLOGLEVEL, "Message received was:" + msgString);
            }
            if (from.equals(replyFrom) && msgString.equals(replyMsg)) {
                receivedReply.set(true);
                synchronized (receivedReply) {
                    receivedReply.notifyAll();
                }
            }
        }
    }

    public static void setupLogHandler() {
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
        //myLogger.setLevel(Level.parse("INFO"));
    }
}



