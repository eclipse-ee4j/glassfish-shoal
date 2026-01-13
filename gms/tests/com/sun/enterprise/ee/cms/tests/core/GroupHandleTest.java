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

package com.sun.enterprise.ee.cms.tests.core;

import com.sun.enterprise.ee.cms.impl.base.Utility;
import com.sun.enterprise.ee.cms.core.*;
import com.sun.enterprise.ee.cms.impl.client.JoinNotificationActionFactoryImpl;
import com.sun.enterprise.ee.cms.impl.client.JoinedAndReadyNotificationActionFactoryImpl;
import com.sun.enterprise.ee.cms.impl.client.PlannedShutdownActionFactoryImpl;
import com.sun.enterprise.ee.cms.impl.client.MessageActionFactoryImpl;
import com.sun.enterprise.ee.cms.logging.GMSLogDomain;
import com.sun.enterprise.ee.cms.spi.MemberStates;
import com.sun.enterprise.mgmt.transport.MessageIOException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class GroupHandleTest {

    static final String GROUPHANDLE = "GroupHandle";
    private GroupManagementService gms = null;
    private static final Logger gmsLogger = GMSLogDomain.getLogger(GMSLogDomain.GMS_LOGGER);
    static AtomicBoolean completedCheck = new AtomicBoolean(false);
    static String memberID = null;
    static Integer memberIDNum = new Integer(0);
    static int numberOfInstances = 0;
    static ConcurrentHashMap<String, String> numberOfPlannedShutdownReceived = new ConcurrentHashMap<String, String>();
    static AtomicInteger numberOfJoinAndReady = new AtomicInteger(0);
    static List<String> members;
    static List<GMSMember> GMSMembers;
    static int exceedTimeoutLimit = 300; // 5 minutes
    static String groupName = null;
    static String logDir = null;
    static int numOfTests = 0;

    public static void main(String[] args) {

        //for (int z = 0; z < args.length; z++) {
        //    gmsLogger.log(Level.INFO,(z + "=" + args[z]);
        //}

        if (args[0].equalsIgnoreCase("-h")) {
            usage();
        }

        if (args[0].equalsIgnoreCase("master")) {
            if (args.length == 4) {
                memberID = args[0];
                gmsLogger.log(Level.INFO, ("memberID=" + memberID));
                groupName = args[1];
                gmsLogger.log(Level.INFO, ("groupName=" + groupName));
                numberOfInstances = Integer.parseInt(args[2]);
                gmsLogger.log(Level.INFO, ("numberOfInstances=" + numberOfInstances));
                logDir = args[3];
                gmsLogger.log(Level.INFO, ("logDirectory=" + logDir));
            } else {
                System.err.println("Arguements passed in does not equal 4: [" + args.length + "]");
                int i = 0;
                for (String arg : args) {
                    System.err.println("Arg[" + (i++) + "]=" + arg);
                }
                usage();
            }
        } else if (args[0].contains("core")) {
            if (args.length == 3) {
                memberID = args[0];
                gmsLogger.log(Level.INFO, ("memberID=" + memberID));
                if (!memberID.startsWith("core")) {
                    System.err.println("ERROR: The member name must be in the format 'corexxx'");
                    System.exit(1);
                }
                groupName = args[1];
                gmsLogger.log(Level.INFO, ("groupName=" + groupName));
                logDir = args[2];
                gmsLogger.log(Level.INFO, ("logDirectory=" + logDir));
            } else {
                System.err.println("Arguements passed in does not equal 3: [" + args.length + "]");
                int i = 0;
                for (String arg : args) {
                    System.err.println("Arg[" + (i++) + "]=" + arg);
                }
                usage();
            }

        } else {
            usage();
        }
        Utility.setLogger(gmsLogger);
        Utility.setupLogHandler();

        GroupHandleTest sender = new GroupHandleTest();
        if (memberID.equalsIgnoreCase("core101")) {
            sender.testExecutionCoreMember();
        } else if (memberID.equalsIgnoreCase("master")) {
            sender.masterSpectatorMember();
        } else {
            sender.otherCoreMembers();
        }

        System.out.println("================================================================");
        if (memberID.equalsIgnoreCase("core101")) {
            if (numOfTests > 0) {
               gmsLogger.log(Level.INFO, GROUPHANDLE + " Testing Complete for " + numOfTests + " tests");
            } else {
               gmsLogger.log(Level.SEVERE, "Testing NOT Complete only " + numOfTests + " tests run");
            }
        } else {
               gmsLogger.log(Level.INFO, GROUPHANDLE + " Testing Complete");
        }
        System.out.println(memberID+" exiting");
        gmsLogger.log(Level.INFO, memberID+" exiting");

    }

    public static void usage() {

        System.out.println(" For master:");
        System.out.println("    <memberid(master)> <groupName> <number_of_cores> <logDir>");
        System.out.println(" For cores:");
        System.out.println("    <memberid(corexxx)> <groupName> <logDir>");
        System.exit(0);
    }

    private void testExecutionCoreMember() {

        //******************************
        // NEXT TEST ID = TEST74
        //******************************


        gmsLogger.log(Level.INFO, "Testing Started");

        sleep(5000);

        gmsLogger.log(Level.INFO, "Starting Positive Testing");


        //initialize Group Management Service and register for Group Events

        gmsLogger.log(Level.INFO, "Registering for group event notifications");
        gms = initializeGMS(memberID, groupName, GroupManagementService.MemberType.CORE);
        gms.addActionFactory(new JoinedAndReadyNotificationActionFactoryImpl(new JoinAndReadyNotificationCallBack(memberID)));
        gms.addActionFactory(new MessageActionFactoryImpl(new MessageCallBack(memberID, logDir)), "TestComponent");

        gmsLogger.log(Level.INFO, "Joining Group " + groupName);
        try {
            gms.join();
        } catch (GMSException e) {
            gmsLogger.log(Level.SEVERE, "Exception occured :" + e, e);
            System.exit(1);
        }

        sleep(5000);

        GroupHandle gh = gms.getGroupHandle();

        // Get the list of members that are alive or ready. Master and core102 are already
        // in the READY state.  We are only in the joined state.
        numOfTests++;
        gmsLogger.log(Level.INFO, "Executing TEST1");
        members = gh.getCurrentAliveOrReadyMembers();
        List<String> sList = new ArrayList<String>();
        sList.add("core101");
        sList.add("core102");
        sList.add("core103");
        if (!members.equals(sList)) {
            gmsLogger.log(Level.SEVERE, "TEST1: getCurrentAliveOrReadyMembers() [before gms.reportJoinedAndReadyState()]: expected result:" + sList.toString() + ", actual result:" + members.toString());
        }

        gms.reportJoinedAndReadyState();
        sleep(5000);

        // Get the list of members that are alive or ready. Master and core102 are already
        // in the READY state.  We are now in the READY state.
        numOfTests++;
        gmsLogger.log(Level.INFO, "Executing TEST2");
        members = gh.getCurrentAliveOrReadyMembers();
        sList = new ArrayList<String>();
        sList.add("core101");
        sList.add("core102");
        sList.add("core103");
        if (!members.equals(sList)) {
            gmsLogger.log(Level.SEVERE, "TEST2: getCurrentAliveOrReadyMembers() [after gms.reportJoinedAndReadyState()]: expected result:" + sList.toString() + ", actual result:" + members.toString());
        }


        // Get the list of members that are alive or ready. Master and core102 are already
        // in the READY state.  We are now in the READY state.
        numOfTests++;
        gmsLogger.log(Level.INFO, "Executing TEST3");
        members = gh.getAllCurrentMembers();
        sList = new ArrayList<String>();
        sList.add("core101");
        sList.add("core102");
        sList.add("core103");
        sList.add("master");
        if (!members.equals(sList)) {
            gmsLogger.log(Level.SEVERE, "TEST3: getAllCurrentMembers(): expected result:" + sList.toString() + ", actual result:" + members.toString());
        }


        // Get the list of members that are alive or ready. Master and core102 are already
        // in the READY state.  We are now in the READY state.
        numOfTests++;
        gmsLogger.log(Level.INFO, "Executing TEST6");
        members = gh.getAllCurrentMembersWithStartTimes();
        sList = new ArrayList<String>();
        sList.add("core101");
        sList.add("core102");
        sList.add("core103");
        sList.add("master");
        ArrayList<String> tList = new ArrayList<String>();
        for (String member : members) {
            // get the time piece
            int delim = member.indexOf("::");
            // get memberid
            tList.add(member.substring(0, delim));
            // get the time piece
            long lActual = Long.parseLong(member.substring(delim + 2));
            long lTime = System.currentTimeMillis();
            long lExpected = lTime - 30000;
            // check if the value is within 30 seconds of the current time
            if ((lActual < lExpected) || (lActual > lTime)) {
                gmsLogger.log(Level.SEVERE, "TEST6: getAllCurrentMembersWithStartTimes() [for: " + member.toString() + "]: Time value was not within 30 seconds [" + lExpected + "] of the current time [" + lTime + "] or it was greater than the current time");
            }
        }
        if (!tList.equals(sList)) {
            gmsLogger.log(Level.SEVERE, "TEST6: getAllCurrentMembersWithStartTimes(): expected result:" + sList.toString() + ", actual result:" + tList.toString());
        }


        // Get the list of members that are alive or ready. Master and core102 are already
        // in the READY state.  We are now in the READY state.
        numOfTests++;
        gmsLogger.log(Level.INFO, "Executing TEST7");
        members = gh.getCurrentCoreMembers();
        sList = new ArrayList<String>();
        sList.add("core101");
        sList.add("core102");
        sList.add("core103");
        if (!members.equals(sList)) {
            gmsLogger.log(Level.SEVERE, "TEST7: getCurrentCoreMembers(): expected result:" + sList.toString() + ", actual result:" + members.toString());
        }


        // Get the list of members that are alive or ready. Master and core102 are already
        // in the READY state.  We are now in the READY state.
        numOfTests++;
        gmsLogger.log(Level.INFO, "Executing TEST8");
        members = gh.getCurrentCoreMembersWithStartTimes();
        sList = new ArrayList<String>();
        sList.add("core101");
        sList.add("core102");
        sList.add("core103");
        tList = new ArrayList<String>();
        for (String member : members) {
            // get the time piece
            int delim = member.indexOf("::");
            // get memberid
            tList.add(member.substring(0, delim));
            // get the time piece
            long lActual = Long.parseLong(member.substring(delim + 2));
            long lTime = System.currentTimeMillis();
            long lExpected = lTime - 30000;
            // check if the value is within 30 seconds of the current time
            if ((lActual < lExpected) || (lActual > lTime)) {
                gmsLogger.log(Level.SEVERE, "TEST8: getCurrentCoreMembersWithStartTimes() [for: " + member.toString() + "]: Time value was not within 30 seconds [" + lExpected + "] of the current time [" + lTime + "] or it was greater than the current time");

            }
        }
        if (!tList.equals(sList)) {
            gmsLogger.log(Level.SEVERE, "TEST8: getCurrentCoreMembersWithStartTimes(): expected result:" + sList.toString() + ", actual result:" + tList.toString());
        }

        numOfTests++;
        gmsLogger.log(Level.INFO, "Executing TEST10");
        DistributedStateCache dsc = gh.getDistributedStateCache();
        if (dsc == null) {
            gmsLogger.log(Level.SEVERE, "TEST10: getDistributedStateCache(): returned a null value");
        }


        // Get the current leader. Master was the first member that was started.
        numOfTests++;
        gmsLogger.log(Level.INFO, "Executing TEST11");
        String sActual = gh.getGroupLeader();
        String sExpected = "master";
        if (!sExpected.equalsIgnoreCase(sActual)) {
            gmsLogger.log(Level.SEVERE, "TEST11: getGroupLeader(): expected result: " + sExpected + ", actual result:" + sActual);
        }

        // member is alive and ready
        numOfTests++;
        gmsLogger.log(Level.INFO, "Executing TEST12");
        MemberStates msActual = gh.getMemberState("master");
        MemberStates msExpected = MemberStates.ALIVEANDREADY;
        if (!msActual.equals(msExpected)) {
            gmsLogger.log(Level.SEVERE, "TEST12: getMemberState(master): expected result: " + msExpected + ", actual result:" + msActual);
        }

        // member is alive and ready
        numOfTests++;
        gmsLogger.log(Level.INFO, "Executing TEST13");
        msActual = gh.getMemberState("core101");
        msExpected = MemberStates.ALIVEANDREADY;
        if (!msActual.equals(msExpected)) {
            gmsLogger.log(Level.SEVERE, "TEST13: getMemberState(core101): expected result: " + msExpected + ", actual result:" + msActual);
        }

        // member is alive and ready
        numOfTests++;
        gmsLogger.log(Level.INFO, "Executing TEST14");
        msActual = gh.getMemberState("core102");
        msExpected = MemberStates.ALIVEANDREADY;
        if (!msActual.equals(msExpected)) {
            gmsLogger.log(Level.SEVERE, "TEST14: getMemberState(core102): expected result: " + msExpected + ", actual result:" + msActual);
        }

        // member is alive and ready
        numOfTests++;
        gmsLogger.log(Level.INFO, "Executing TEST15");
        msActual = gh.getMemberState("master", 0, 0);
        msExpected = MemberStates.ALIVEANDREADY;
        if (!msActual.equals(msExpected)) {
            gmsLogger.log(Level.SEVERE, "TEST15: getMemberState(master, 0, 0): expected result: " + msExpected + ", actual result:" + msActual);
        }

        // member is alive and ready
        numOfTests++;
        gmsLogger.log(Level.INFO, "Executing TEST16");
        msActual = gh.getMemberState("core101", 0, 0);
        msExpected = MemberStates.ALIVEANDREADY;
        if (!msActual.equals(msExpected)) {
            gmsLogger.log(Level.SEVERE, "TEST16: getMemberState(core101, 0, 0): expected result: " + msExpected + ", actual result:" + msActual);
        }

        // member is alive and ready
        numOfTests++;
        gmsLogger.log(Level.INFO, "Executing TEST17");
        msActual = gh.getMemberState("core102", 0, 0);
        msExpected = MemberStates.ALIVEANDREADY;
        if (!msActual.equals(msExpected)) {
            gmsLogger.log(Level.SEVERE, "TEST17: getMemberState(core102, 0, 0): expected result: " + msExpected + ", actual result:" + msActual);
        }

        // member is alive and ready
        numOfTests++;
        gmsLogger.log(Level.INFO, "Executing TEST22");
        boolean b = gh.isMemberAlive("master");
        if (!b) {
            gmsLogger.log(Level.SEVERE, "TEST22: isMemberAlive(master): expected result: true, actual result: " + b);
        }

        // member is alive and ready
        numOfTests++;
        gmsLogger.log(Level.INFO, "Executing TEST23");
        b = gh.isMemberAlive("core101");
        if (!b) {
            gmsLogger.log(Level.SEVERE, "TEST23: isMemberAlive(core101): expected result: true, actual result: " + b);
        }

        // member is alive and ready
        numOfTests++;
        gmsLogger.log(Level.INFO, "Executing TEST24");
        b = gh.isMemberAlive("core102");
        if (!b) {
            gmsLogger.log(Level.SEVERE, "TEST24: isMemberAlive(core102): expected result: true, actual result: " + b);
        }

        // send a message to the TestComponent component of all members
        numOfTests++;
        gmsLogger.log(Level.INFO, "Executing TEST29");
        sExpected = "TEST29:";
        try {
            gh.sendMessage("TestComponent", sExpected.getBytes());
        } catch (GMSException ge) {
            gmsLogger.log(Level.SEVERE, "TEST29: sendMessage(TestComponent, " + sExpected + ".getBytes()): resulted in a GMSException:" + ge);
        }
        String fileName = logDir + File.separator + GROUPHANDLE + "_core102_TestComponent_TEST29";
        String doneFileName = fileName + ".done";
        waitForDoneFile(doneFileName);
        String outFileName = fileName + ".out";
        sActual = readDataFromFile(outFileName,"TEST29");
        if (sActual != null){
            if (!sActual.equals(sExpected)) {
                gmsLogger.log(Level.SEVERE, "TEST29: The msg sent does not equal the msg received, sent=(" + sExpected.length() + ")|" + sExpected + "|, received=(" + sActual.length() + ")|" + sActual + "|, file=" + outFileName);
            }
        }
        removeFile(doneFileName);

        fileName = logDir + File.separator + GROUPHANDLE + "_core103_TestComponent_TEST29";
        doneFileName = fileName + ".done";
        waitForDoneFile(doneFileName);
        outFileName = fileName + ".out";
        sActual = readDataFromFile(outFileName,"TEST29");
        if (sActual != null){
            if (!sActual.equals(sExpected)) {
                gmsLogger.log(Level.SEVERE, "TEST29: The msg sent does not equal the msg received, sent=(" + sExpected.length() + ")|" + sExpected + "|, received=(" + sActual.length() + ")|" + sActual + "|, file=" + outFileName);
            }
        }
        removeFile(doneFileName);

        fileName = logDir + File.separator + GROUPHANDLE + "_master_TestComponent_TEST29";
        doneFileName = fileName + ".done";
        waitForDoneFile(doneFileName);
        outFileName = fileName + ".out";
        sActual = readDataFromFile(outFileName,"TEST29");
        if (sActual != null){
            if (!sActual.equals(sExpected)) {
                gmsLogger.log(Level.SEVERE, "TEST29: The msg sent does not equal the msg received, sent=(" + sExpected.length() + ")|" + sExpected + "|, received=(" + sActual.length() + ")|" + sActual + "|, file=" + outFileName);
            }
        }
        removeFile(doneFileName);

        // send a message to the TestComponent component of core102
        numOfTests++;
        gmsLogger.log(Level.INFO, "Executing TEST30");
        sExpected = "TEST30:";
        try {
            gh.sendMessage("core102", "TestComponent", sExpected.getBytes());
        } catch (GMSException ge) {
            gmsLogger.log(Level.SEVERE, "TEST30: sendMessage(core102,TestComponent, " + sExpected + ".getBytes()): resulted in a GMSException:" + ge);
        }
        fileName = logDir + File.separator + GROUPHANDLE + "_core102_TestComponent_TEST30";
        doneFileName = fileName + ".done";
        waitForDoneFile(doneFileName);
        outFileName = fileName + ".out";
        sActual = readDataFromFile(outFileName,"TEST30");
        if (sActual != null){
            if (!sActual.equals(sExpected)) {
               gmsLogger.log(Level.SEVERE, "TEST30: The msg sent does not equal the msg received, sent=(" + sExpected.length() + ")|" + sExpected + "|, received=(" + sActual.length() + ")|" + sActual + "|, file=" + outFileName);
            }
        }
        removeFile(doneFileName);

        // send an empty message to the TestComponent component of all members
        numOfTests++;
        gmsLogger.log(Level.INFO, "Executing TEST31");
        int iExpected = 1;
        try {
            gh.sendMessage("TestComponent", "".getBytes());
        } catch (GMSException ge) {
            gmsLogger.log(Level.SEVERE, "TEST31: sendMessage(TestComponent,''.getBytes()): resulted in a GMSException:" + ge);
        }
        fileName = logDir + File.separator + GROUPHANDLE + "_master_TestComponent_EmptyMessage";
        doneFileName = fileName + ".done";
        waitForDoneFile(doneFileName);
        outFileName = fileName + ".out";
        int iActual=0;
        sActual = readDataFromFile(outFileName,"TEST31");
        if (sActual != null){
            iActual = Integer.parseInt(sActual);
            if (iActual != iExpected) {
                gmsLogger.log(Level.SEVERE, "TEST31: The number of zero length msgs sent does not equal the number of zero length msgs received, sent=(" + iExpected + "), received=(" + iActual + "), file=" + outFileName);
            }
        }
        removeFile(doneFileName);
        fileName = logDir + File.separator + GROUPHANDLE + "_core102_TestComponent_EmptyMessage";
        doneFileName = fileName + ".done";
        waitForDoneFile(doneFileName);
        outFileName = fileName + ".out";
        sActual = readDataFromFile(outFileName,"TEST31");
        if (sActual != null){
            iActual = Integer.parseInt(sActual);
            if (iActual != iExpected) {
                gmsLogger.log(Level.SEVERE, "TEST31: The number of zero length msgs sent does not equal the number of zero length msgs received, sent=(" + iExpected + "), received=(" + iActual + "), file=" + outFileName);
            }
        }
        removeFile(doneFileName);
        fileName = logDir + File.separator + GROUPHANDLE + "_core103_TestComponent_EmptyMessage";
        doneFileName = fileName + ".done";
        waitForDoneFile(doneFileName);
        outFileName = fileName + ".out";
        sActual = readDataFromFile(outFileName,"TEST31");
        if (sActual != null){
            iActual = Integer.parseInt(sActual);
            if (iActual != iExpected) {
                gmsLogger.log(Level.SEVERE, "TEST31: The number of zero length msgs sent does not equal the number of zero length msgs received, sent=(" + iExpected + "), received=(" + iActual + "), file=" + outFileName);
            }
        }
        removeFile(doneFileName);

        // send a small message to all components of all members
        numOfTests++;
        gmsLogger.log(Level.INFO, "Executing TEST32");
        sExpected = "TEST32:";
        try {
            gh.sendMessage(null, sExpected.getBytes());
            gmsLogger.log(Level.SEVERE, "TEST32: sendMessage(null,TEST32:.getBytes()): did not result in expected exception IllegalArgumentException");
        } catch (IllegalArgumentException iae) {
        } catch (GMSException he) {
        }

        /*
        int size = 8200;
        byte[] bArray = new byte[size];
        bArray[0] = 'a';
        int k = 1;
        for (; k < size - 1; k++) {
        bArray[k] = 'X';
        }
        bArray[k] = 'z';

        gmsLogger.log(Level.INFO, "Executing TEST4a_"+jj);
        try {
        gmsLogger.log(Level.INFO, "TEST4a_"+jj+": payload=aXX...XXz[" + bArray.length + "]");
        gh.sendMessage("core102", "TestComponent", bArray);
        } catch (GMSException ge) {
        gmsLogger.log(Level.SEVERE, "TEST4a_"+jj+": sendMessage(core102,TestComponent, aXX...XXz[" + bArray.length + "]): resulted in a GMSException:" + ge);
        }
        sleep(10);
        }
         */

        // send a large message to the TestComponent component of core102
        int size = 5000;
        byte[] bArray = new byte[size];
        bArray[0] = 'T';
        bArray[1] = 'E';
        bArray[2] = 'S';
        bArray[3] = 'T';
        bArray[4] = '4';
        bArray[5] = ':';
        bArray[6] = 'a';
        int k = 7;
        for (; k < size - 1; k++) {
            bArray[k] = 'X';
        }
        bArray[k] = 'z';
        numOfTests++;
        gmsLogger.log(Level.INFO, "Executing TEST4");
        sExpected = new String(bArray);
        try {
            gmsLogger.log(Level.INFO, "TEST4:aXX...XXz[" + bArray.length + "]");
            gh.sendMessage("core102", "TestComponent", bArray);
        } catch (GMSException ge) {
            gmsLogger.log(Level.SEVERE, "TEST4: sendMessage(core102,TestComponent, TEST4:aXX...XXz[" + bArray.length + "]): resulted in a GMSException:" + ge);
        }
        fileName = logDir + File.separator + GROUPHANDLE + "_core102_TestComponent_TEST4";
        doneFileName = fileName + ".done";
        waitForDoneFile(doneFileName);
        outFileName = fileName + ".out";
        sActual = readDataFromFile(outFileName,"TEST4");
        if (sActual != null){
            if (!sActual.equals(sExpected)) {
                gmsLogger.log(Level.SEVERE, "TEST4: The msg sent does not equal the msg received, sent=(" + sExpected.length() + ")|" + sExpected + "|, received=(" + sActual.length() + ")|" + sActual + "|, file=" + outFileName);
            }
        }
        removeFile(doneFileName);


        // send a message to the TestComponent component of all members
        size = 5000;
        bArray = new byte[size];
        bArray[0] = 'T';
        bArray[1] = 'E';
        bArray[2] = 'S';
        bArray[3] = 'T';
        bArray[4] = '3';
        bArray[5] = '8';
        bArray[6] = ':';
        bArray[7] = 'a';
        k = 8;
        for (; k < size - 1; k++) {
            bArray[k] = 'X';
        }
        bArray[k] = 'z';

        numOfTests++;
        gmsLogger.log(Level.INFO, "Executing TEST38");
        sExpected = new String(bArray);
        try {
            gmsLogger.log(Level.INFO, "TEST38:aXX...XXz[" + bArray.length + "]");
            gh.sendMessage("TestComponent", bArray);
        } catch (GMSException ge) {
            gmsLogger.log(Level.SEVERE, "TEST38: sendMessage(TestComponent, TEST38:aXX...XXz[" + bArray.length + "]): resulted in a GMSException:" + ge);
        }
        fileName = logDir + File.separator + GROUPHANDLE + "_core102_TestComponent_TEST38";
        doneFileName = fileName + ".done";
        waitForDoneFile(doneFileName);
        outFileName = fileName + ".out";
        sActual = readDataFromFile(outFileName,"TEST38");
        if (sActual != null){
            if (!sActual.equals(sExpected)) {
                gmsLogger.log(Level.SEVERE, "TEST38: The msg sent does not equal the msg received, sent=(" + sExpected.length() + ")|" + sExpected + "|, received=(" + sActual.length() + ")|" + sActual + "|, file=" + outFileName);
            }
        }
        removeFile(doneFileName);

        fileName = logDir + File.separator + GROUPHANDLE + "_core103_TestComponent_TEST38";
        doneFileName = fileName + ".done";
        waitForDoneFile(doneFileName);
        outFileName = fileName + ".out";
        sActual = readDataFromFile(outFileName,"TEST38");
        if (sActual != null){
            if (!sActual.equals(sExpected)) {
                gmsLogger.log(Level.SEVERE, "TEST38: The msg sent does not equal the msg received, sent=(" + sExpected.length() + ")|" + sExpected + "|, received=(" + sActual.length() + ")|" + sActual + "|, file=" + outFileName);
            }
        }
        removeFile(doneFileName);

        fileName = logDir + File.separator + GROUPHANDLE + "_master_TestComponent_TEST38";
        doneFileName = fileName + ".done";
        waitForDoneFile(doneFileName);
        outFileName = fileName + ".out";
        sActual = readDataFromFile(outFileName,"TEST38");
        if (sActual != null){
            if (!sActual.equals(sExpected)) {
                gmsLogger.log(Level.SEVERE, "TEST38: The msg sent does not equal the msg received, sent=(" + sExpected.length() + ")|" + sExpected + "|, received=(" + sActual.length() + ")|" + sActual + "|, file=" + outFileName);
            }
        }
        removeFile(doneFileName);

        // send a message to the TestComponent component of all members
        size = 20;
        bArray = new byte[size];
        bArray[0] = 'T';
        bArray[1] = 'E';
        bArray[2] = 'S';
        bArray[3] = 'T';
        bArray[4] = '3';
        bArray[5] = '9';
        bArray[6] = ':';
        bArray[7] = 'a';
        k = 8;
        for (; k < size - 1; k++) {
            bArray[k] = 'X';
        }
        bArray[k] = 'z';

        numOfTests++;
        gmsLogger.log(Level.INFO, "Executing TEST39");
        try {
            gmsLogger.log(Level.INFO, "TEST39: send message to doesnotexist member");
            gh.sendMessage("doesnotexist", "TestComponent", bArray);
            gmsLogger.log(Level.SEVERE, "TEST39: sendMessage to doesnotexist member did not result in a MemberNotInViewException");
        } catch (MemberNotInViewException mnive) {
        } catch (GMSException ge) {

        }

            // send a message to the TestComponent component of all members
        size = 20;
        bArray = new byte[size];
        bArray[0] = 'T';
        bArray[1] = 'E';
        bArray[2] = 'S';
        bArray[3] = 'T';
        bArray[4] = '3';
        bArray[5] = '9';
        bArray[6] = ':';
        bArray[7] = 'a';
        k = 8;
        for (; k < size - 1; k++) {
            bArray[k] = 'X';
        }
        bArray[k] = 'z';

        numOfTests++;
        gmsLogger.log(Level.INFO, "Executing TEST73");
        try {
            gmsLogger.log(Level.INFO, "TEST73: send message to doesnotexist target component");
            gh.sendMessage("DoesNotExistTestComponent", bArray);
        } catch (GMSException ge) {

        }

        // send a message that exceeds MAX length
        size = 1500000;
        bArray = new byte[size];
        bArray[0] = 'T';
        bArray[1] = 'E';
        bArray[2] = 'S';
        bArray[3] = 'T';
        bArray[4] = '7';
        bArray[5] = '2';
        bArray[6] = ':';
        bArray[7] = 'a';
        k = 7;
        for (; k < size - 1; k++) {
            bArray[k] = 'X';
        }
        bArray[k] = 'z';
        numOfTests++;
        gmsLogger.log(Level.INFO, "Executing TEST69");
        try {
            gmsLogger.log(Level.INFO, "TEST69: asynchronous UDP broadcast of too large message aXX...XXz[" + bArray.length + "]");
            gh.sendMessage((String)null, "TestComponent", bArray);
            gmsLogger.log(Level.SEVERE, "TEST69: sendMessage(null, TestComponent, TEST69:aXX...XXz[" + bArray.length + "]): did not result in a GMS Exception");
        } catch (GMSException ge) {
            gmsLogger.log(Level.INFO, "TEST69: handled expected exception " + ge.getMessage());
        }





        // send a message that exceeds MAX length
        size = 1500000;
        bArray = new byte[size];
        bArray[0] = 'T';
        bArray[1] = 'E';
        bArray[2] = 'S';
        bArray[3] = 'T';
        bArray[4] = '7';
        bArray[5] = '1';
        bArray[6] = ':';
        bArray[7] = 'a';
        k = 7;
        for (; k < size - 1; k++) {
            bArray[k] = 'X';
        }
        bArray[k] = 'z';
        numOfTests++;
        gmsLogger.log(Level.INFO, "Executing TEST71");
        try {
            gmsLogger.log(Level.INFO, "TEST71:synchronous broadcast (TCP to each member)aXX...XXz[" + bArray.length + "]");
            gh.sendMessage("TestComponent", bArray);
            gmsLogger.log(Level.SEVERE, "TEST71: sendMessage(TestComponent, TEST71:aXX...XXz[" + bArray.length + "]): did not result in a GMS Exception");
        } catch (GMSException ge) {
            gmsLogger.log(Level.INFO, "TEST71: handled expected GMSException when sending a message that is too large", ge);
        }

        // send a message that exceeds MAX length
        size = 1500000;
        bArray = new byte[size];
        bArray[0] = 'T';
        bArray[1] = 'E';
        bArray[2] = 'S';
        bArray[3] = 'T';
        bArray[4] = '7';
        bArray[5] = '2';
        bArray[6] = ':';
        bArray[7] = 'a';
        k = 7;
        for (; k < size - 1; k++) {
            bArray[k] = 'X';
        }
        bArray[k] = 'z';
        numOfTests++;
        gmsLogger.log(Level.INFO, "Executing TEST72");
        try {
            gmsLogger.log(Level.INFO, "TEST72: send to core102 aXX...XXz[" + bArray.length + "]");
            gh.sendMessage("core102", "TestComponent", bArray);
            gmsLogger.log(Level.SEVERE, "TEST72: sendMessage(core102, TestComponent, TEST72:aXX...XXz[" + bArray.length + "]): did not result in a GMS Exception");
        } catch (GMSException ge) {
        }





        // send a message to the all components of core102
        numOfTests++;
        gmsLogger.log(Level.INFO, "Executing TEST34");
        sExpected = "TEST34:";
        try {
            gh.sendMessage("core102", null, sExpected.getBytes());
        } catch (GMSException ge) {
            // expect this for null target component.
        }

        // send a message to the TestComponent component of all members
        numOfTests++;
        gmsLogger.log(Level.INFO, "Executing TEST35");
        sExpected = "TEST35:";
        try {
            gh.sendMessage(new ArrayList<String>(), "TestComponent", sExpected.getBytes());
        } catch (GMSException ge) {
            gmsLogger.log(Level.SEVERE, "TEST35: sendMessage(new ArrayList<String>(),TestComponent,TEST35:.getBytes()): resulted in a GMSException:" + ge);
        }
        fileName = logDir + File.separator + GROUPHANDLE + "_core102_TestComponent_TEST35";
        doneFileName = fileName + ".done";
        waitForDoneFile(doneFileName);
        outFileName = fileName + ".out";
        sActual = readDataFromFile(outFileName,"TEST35");
        if (sActual != null){
            if (!sActual.equals(sExpected)) {
                gmsLogger.log(Level.SEVERE, "TEST35: The msg sent does not equal the msg received, sent=(" + sExpected.length() + ")|" + sExpected + "|, received=(" + sActual.length() + ")|" + sActual + "|, file=" + outFileName);
            }
        }
        removeFile(doneFileName);
        fileName = logDir + File.separator + GROUPHANDLE + "_core103_TestComponent_TEST35";
        doneFileName = fileName + ".done";
        waitForDoneFile(doneFileName);
        outFileName = fileName + ".out";
        sActual = readDataFromFile(outFileName,"TEST35");
        if (sActual != null){
            if (!sActual.equals(sExpected)) {
                gmsLogger.log(Level.SEVERE, "TEST35: The msg sent does not equal the msg received, sent=(" + sExpected.length() + ")|" + sExpected + "|, received=(" + sActual.length() + ")|" + sActual + "|, file=" + outFileName);
            }
        }
        removeFile(doneFileName);
        fileName = logDir + File.separator + GROUPHANDLE + "_master_TestComponent_TEST35";
        doneFileName = fileName + ".done";
        waitForDoneFile(doneFileName);
        outFileName = fileName + ".out";
        sActual = readDataFromFile(outFileName,"TEST35");
        if (sActual != null){
            if (!sActual.equals(sExpected)) {
                gmsLogger.log(Level.SEVERE, "TEST35: The msg sent does not equal the msg received, sent=(" + sExpected.length() + ")|" + sExpected + "|, received=(" + sActual.length() + ")|" + sActual + "|, file=" + outFileName);
            }
        }
        removeFile(doneFileName);

        // send a message to the TestComponent component of core102 using ArrayList of members
        numOfTests++;
        gmsLogger.log(Level.INFO, "Executing TEST62");
        sExpected = "TEST62:";
        sList = new ArrayList<String>();
        sList.add("core102");
        try {
            gh.sendMessage(sList, "TestComponent", sExpected.getBytes());
        } catch (GMSException ge) {
            gmsLogger.log(Level.SEVERE, "TEST62: sendMessage(ArrayList<String>(core102),TestComponent,TEST62:.getBytes()): resulted in a GMSException:" + ge);
        }
        fileName = logDir + File.separator + GROUPHANDLE + "_core102_TestComponent_TEST62";
        doneFileName = fileName + ".done";
        waitForDoneFile(doneFileName);
        outFileName = fileName + ".out";
        sActual = readDataFromFile(outFileName,"TEST62");
        if (sActual != null){
            if (!sActual.equals(sExpected)) {
                gmsLogger.log(Level.SEVERE, "TEST62: The msg sent does not equal the msg received, sent=(" + sExpected.length() + ")|" + sExpected + "|, received=(" + sActual.length() + ")|" + sActual + "|, file=" + outFileName);
            }
        }
        removeFile(doneFileName);

        // send a message to the TestComponent component of master and core102 using ArrayList of members
        numOfTests++;
        gmsLogger.log(Level.INFO, "Executing TEST63");
        sExpected = "TEST63:";
        sList = new ArrayList<String>();
        sList.add("master");
        sList.add("core102");
        try {
            gh.sendMessage(sList, "TestComponent", sExpected.getBytes());
        } catch (GMSException ge) {
            gmsLogger.log(Level.SEVERE, "TEST63: sendMessage(ArrayList<String>(master,core102),TestComponent,TEST63:.getBytes()): resulted in a GMSException:" + ge);
        }
        fileName = logDir + File.separator + GROUPHANDLE + "_core102_TestComponent_TEST63";
        doneFileName = fileName + ".done";
        waitForDoneFile(doneFileName);
        outFileName = fileName + ".out";
        sActual = readDataFromFile(outFileName,"TEST63");
        if (sActual != null){
            if (!sActual.equals(sExpected)) {
               gmsLogger.log(Level.SEVERE, "TEST63: The msg sent does not equal the msg received, sent=(" + sExpected.length() + ")|" + sExpected + "|, received=(" + sActual.length() + ")|" + sActual + "|, file=" + outFileName);
            }
        }
        removeFile(doneFileName);
        fileName = logDir + File.separator + GROUPHANDLE + "_master_TestComponent_TEST63";
        doneFileName = fileName + ".done";
        waitForDoneFile(doneFileName);
        outFileName = fileName + ".out";
        sActual = readDataFromFile(outFileName,"TEST63");
        if (sActual != null){
            if (!sActual.equals(sExpected)) {
               gmsLogger.log(Level.SEVERE, "TEST63: The msg sent does not equal the msg received, sent=(" + sExpected.length() + ")|" + sExpected + "|, received=(" + sActual.length() + ")|" + sActual + "|, file=" + outFileName);
            }
        }
        removeFile(doneFileName);

        // send a message to the TestComponent component of core102 and a member that does not exist
        // using ArrayList of members
        numOfTests++;
        gmsLogger.log(Level.INFO, "Executing TEST64");
        sExpected = "TEST64:";
        sList = new ArrayList<String>();
        sList.add("doesnotexistmember");
        sList.add("core102");
        try {
            gh.sendMessage(sList, "TestComponent", sExpected.getBytes());
        } catch (GMSException ge) {
            // expect exception to be thrown after all sends completed. Should indicate last member failed to send message to.
        }
        fileName = logDir + File.separator + GROUPHANDLE + "_core102_TestComponent_TEST64";
        doneFileName = fileName + ".done";
        waitForDoneFile(doneFileName);
        outFileName = fileName + ".out";
        sActual = readDataFromFile(outFileName,"TEST64");
        if (sActual != null){
            if (!sActual.equals(sExpected)) {
                gmsLogger.log(Level.SEVERE, "TEST64: The msg sent does not equal the msg received, sent=(" + sExpected.length() + ")|" + sExpected + "|, received=(" + sActual.length() + ")|" + sActual + "|, file=" + outFileName);
            }
        }
        removeFile(doneFileName);

        // send a message to a component that does not exist of core102, there is nothing
        // to verify in this case other than that sendMessage did not throw an exception
        numOfTests++;
        gmsLogger.log(Level.INFO, "Executing TEST65");
        sExpected = "TEST65:";
        try {
            gh.sendMessage("core102", "doesnotexistTestComponent", sExpected.getBytes());

        } catch (Exception ex) {
            gmsLogger.log(Level.SEVERE, "TEST65: sendMessage(core102,doesnotexist,TEST65:.getBytes()): resulted in an Exception:" + ex);
        }


        // send a message to a component that does not exist of core102, there is nothing
        // to verify in this case other than that sendMessage did not throw an exception
        numOfTests++;
        gmsLogger.log(Level.INFO, "Executing TEST66");
        sExpected = "TEST66:";
        try {
            gh.sendMessage("doesnotexistTargetComponent", sExpected.getBytes());
        } catch (Exception ex) {
            gmsLogger.log(Level.SEVERE, "TEST65: sendMessage(doesnotexist,TEST65:.getBytes()): resulted in an Exception:" + ex);
        }

        numOfTests++;
        gmsLogger.log(Level.INFO, "Executing TEST18");
        b = gh.isFenced("TestComponent", "master");
        if (b) {
            gmsLogger.log(Level.SEVERE, "TEST18: isFenced(TestComponent,master): expected result: false, actual result: " + b);
        }

        numOfTests++;
        gmsLogger.log(Level.INFO, "Executing TEST19");
        b = gh.isFenced("TestComponent", "core101");
        if (b) {
            gmsLogger.log(Level.SEVERE, "TEST19: isFenced(TestComponent,core101): expected result: false, actual result: " + b);
        }

        numOfTests++;
        gmsLogger.log(Level.INFO, "Executing TEST20");
        b = gh.isFenced("TestComponent", "core102");
        if (b) {
            gmsLogger.log(Level.SEVERE, "TEST20: isFenced(TestComponent,core102): expected result: false, actual result: " + b);
        }


        numOfTests++;
        gmsLogger.log(Level.INFO, "Executing TEST26");
        try {
            gh.raiseFence("TestComponent", "core102");
            b = gh.isFenced("TestComponent", "core102");
            if (!b) {
                gmsLogger.log(Level.SEVERE, "TEST26: after raiseFence(), isFenced(TestComponent,core102): expected result: true, actual result: " + b);
            }
        } catch (GMSException ge) {
            gmsLogger.log(Level.SEVERE, "TEST26: raiseFence(TestComponent,core102): resulted in a GMSException:" + ge);
        }

        // verify fence was lowered
        numOfTests++;
        gmsLogger.log(Level.INFO, "Executing TEST27");
        try {
            gh.lowerFence("TestComponent", "core102");
            b = gh.isFenced("TestComponent", "core102");
            if (b) {
                gmsLogger.log(Level.SEVERE, "TEST27: after lowerFence(), isFenced(TestComponent,core102): expected result: false, actual result: " + b);
            }
        } catch (GMSException ge) {
            gmsLogger.log(Level.SEVERE, "TEST27: lowerFence(TestComponent,core102): resulted in a GMSException:" + ge);
        } catch (Exception ex) {
            gmsLogger.log(Level.SEVERE, "TEST27: lowerFence(TestComponent,core102): resulted in a non GMSException:" + ex);
        }



//        numOfTests++;
//        gmsLogger.log(Level.INFO, "Executing TEST67");
//        try {
//            gh.announceWatchdogObservedFailure("core102");
//            gmsLogger.log(Level.SEVERE, "TEST67: announceWatchdogObservedFailure(core102): did not resulted in a GMSException:");
//        } catch (GMSException ge) {
//        } catch (Exception ex) {
//            gmsLogger.log(Level.SEVERE, "TEST67: announceWatchdogObservedFailure(core102): resulted in a non GMSException:" + ex);
//        }


        //====================================================================================
        gmsLogger.log(Level.INFO, "Sending SHUTDOWN to core102 member");
        try {
            gh.sendMessage("core102", "TestComponent", "SHUTDOWN".getBytes());
        } catch (GMSException e) {
            gmsLogger.log(Level.SEVERE, "Exception occured sending SHUTDOWN message to member core102:" + e);
        }
        sleep(10000); // wait 10 seconds

        //====================================================================================

        gmsLogger.log(Level.INFO, "Sending HALT to core103 member");
        try {
            gh.sendMessage("core103", "TestComponent", "HALT".getBytes());
        } catch (GMSException e) {
            gmsLogger.log(Level.SEVERE, "Exception occured sending HALT message to member core103:" + e);
        }
        sleep(15000); // wait 15 seconds

        //====================================================================================


        // core102 has been shutdown and core103 has been halted, only master and core101 should be in view
        numOfTests++;
        gmsLogger.log(Level.INFO, "Executing TEST36");
        List<GMSMember> gmsMemList = gh.getCurrentView();
        if (gmsMemList == null) {
            gmsLogger.log(Level.SEVERE, "TEST36: getCurrentView() : returned a null value");
        } else {
             iExpected = 2;
             iActual = gmsMemList.size();
            if (iActual != iExpected) {
                gmsLogger.log(Level.SEVERE, "TEST36: getCurrentView(): expected result: " + iExpected + ", actual result:" + iActual + ", GMSMembers are: " + gmsMemList.toString());
            } else {
                for (GMSMember gmsMember : gmsMemList) {
                    gmsLogger.log(Level.INFO, "TEST36: GMSMember:" + gmsMember.toString());
                    if (gmsMember == null) {
                        gmsLogger.log(Level.SEVERE, "TEST36: getCurrentView() : returned a null GMSMember, GMSMembers are: " + gmsMemList.toString());
                    }
                    if (gmsMember.getMemberToken().equalsIgnoreCase("master")) {
                        Enum expectedMemberType = GroupManagementService.MemberType.SPECTATOR;
                        String s = gmsMember.getMemberType();
                        if (s != null) {
                            Enum actualMemberType = GroupManagementService.MemberType.valueOf(s);
                            if (!expectedMemberType.equals(actualMemberType)) {
                                gmsLogger.log(Level.SEVERE, "TEST36: getCurrentView():gmsMember.getMemberToken(): for memberToken master, expected result :" + expectedMemberType + ", actual result: " + actualMemberType + ", GMSMembers are: " + gmsMember.toString());
                            }
                        }
                    } else if (gmsMember.getMemberToken().equalsIgnoreCase("core101")) {
                        Enum expectedMemberType = GroupManagementService.MemberType.CORE;
                        String s = gmsMember.getMemberType();
                        if (s != null) {
                            Enum actualMemberType = GroupManagementService.MemberType.valueOf(s);
                            if (!expectedMemberType.equals(actualMemberType)) {
                                gmsLogger.log(Level.SEVERE, "TEST36: getCurrentView():gmsMember.getMemberToken(): for memberToken core101, expected result :" + expectedMemberType + ", actual result: " + actualMemberType + ", GMSMembers are: " + gmsMember.toString());
                            }
                        }
                    } else {
                        gmsLogger.log(Level.SEVERE, "TEST36: getCurrentView():gmsMember.getMemberToken(): returned a result for memberToken " + gmsMember.getMemberToken() + ", GMSMembers are: " + gmsMember.toString());
                    }
                }
            }
        }

        // core102 has been shutdown and core103 has been halted, all members except core102 should still be in view
        numOfTests++;
        gmsLogger.log(Level.INFO, "Executing TEST37");
        gmsMemList = gh.getPreviousView();
        if (gmsMemList == null) {
            gmsLogger.log(Level.SEVERE, "TEST37: getPreviousView() : returned a null value");
        } else {
            iExpected = 3;
            iActual = gmsMemList.size();
            if (iActual != iExpected) {
                gmsLogger.log(Level.SEVERE, "TEST37: getPreviousView(): expected result: " + iExpected + ", actual result:" + iActual + ", GMSMembers are: " + gmsMemList.toString());
            } else {

                for (GMSMember gmsMember : gmsMemList) {
                    gmsLogger.log(Level.INFO, "TEST37: GMSMember:" + gmsMember.toString());
                    if (gmsMember == null) {
                        gmsLogger.log(Level.SEVERE, "TEST37: getPreviousView() : returned a null GMSMeber, GMSMembers are: " + gmsMemList.toString());
                    }
                    if (gmsMember.getMemberToken().equalsIgnoreCase("master")) {
                        Enum expectedMemberType = GroupManagementService.MemberType.SPECTATOR;
                        String s = gmsMember.getMemberType();
                        if (s != null) {
                            Enum actualMemberType = GroupManagementService.MemberType.valueOf(s);
                            if (!expectedMemberType.equals(actualMemberType)) {
                                gmsLogger.log(Level.SEVERE, "TEST37: getPreviousView():gmsMember.getMemberToken(): for memberToken master, expected result :" + expectedMemberType + ", actual result: " + actualMemberType + ", GMSMembers are: " + gmsMember.toString());
                            }
                        }
                    } else if (gmsMember.getMemberToken().equalsIgnoreCase("core101")) {
                        Enum expectedMemberType = GroupManagementService.MemberType.CORE;
                        String s = gmsMember.getMemberType();
                        if (s != null) {
                            Enum actualMemberType = GroupManagementService.MemberType.valueOf(s);
                            if (!expectedMemberType.equals(actualMemberType)) {
                                gmsLogger.log(Level.SEVERE, "TEST37: getPreviousView():gmsMember.getMemberToken(): for memberToken core101, expected result :" + expectedMemberType + ", actual result: " + actualMemberType + ", GMSMembers are: " + gmsMember.toString());
                            }
                        }
                    } else if (gmsMember.getMemberToken().equalsIgnoreCase("core103")) {
                        Enum expectedMemberType = GroupManagementService.MemberType.CORE;
                        String s = gmsMember.getMemberType();
                        if (s != null) {
                            Enum actualMemberType = GroupManagementService.MemberType.valueOf(s);
                            if (!expectedMemberType.equals(actualMemberType)) {
                                gmsLogger.log(Level.SEVERE, "TEST37: getPreviousView():gmsMember.getMemberToken(): for memberToken core103, expected result :" + expectedMemberType + ", actual result: " + actualMemberType + ", GMSMembers are: " + gmsMember.toString());
                            }
                        }
                    } else {
                        gmsLogger.log(Level.SEVERE, "TEST37: getPreviousView():gmsMember.getMemberToken(): returned a result for memberToken " + gmsMember.getMemberToken() + ", GMSMembers are: " + gmsMember.toString());
                    }
                }
            }
        }

        // core102 is shutdown

        // JMF: COMMENT OUT all getMemberState test for STOPPED for now.
        // getMemberState works of hb cache. however, test sends message and then
        // sleeps for 10 seconds to ensure instance is shutdown.  hb cache is stale by then.  no way to ask an instance
        // if it is stopped when it is stopped. Relying on GMS Shutdown/Failure notification is proper way to figure
        // out an instance is down.  getMemberState is more an API for discovering other instance state when an instance first
        // joins gms group.

//        numOfTests++;
//        gmsLogger.log(Level.INFO, "Executing TEST41");
//        msActual = gh.getMemberState("core102", 120000, 0);
//        msExpected = MemberStates.STOPPED;
//        if (!msActual.equals(msExpected)) {
//            gmsLogger.log(Level.SEVERE, "TEST41: getMemberState(core102, 0, 0): expected result: " + msExpected + ", actual result:" + msActual);
//        }


        // master is still alive and should still be leader not us.
        numOfTests++;
        gmsLogger.log(Level.INFO, "Executing TEST21");
        b = gh.isGroupLeader();
        if (b) {
            gmsLogger.log(Level.SEVERE, "TEST21: isGroupLeader(): expected result: false, actual result: " + b);
        }

        //====================================================================================
        gmsLogger.log(Level.INFO, "Sending SHUTDOWN Message to master");
        try {
            gh.sendMessage("master", "TestComponent", "SHUTDOWN".getBytes());
        } catch (GMSException e) {
            gmsLogger.log(Level.SEVERE, "Exception occured sending SHUTDOWN message to member master:" + e);
        }

        sleep(10000); // wait 10 seconds

        //====================================================================================

        // master and core102 are now shutdown so we should be leader
        numOfTests++;
        gmsLogger.log(Level.INFO, "Executing TEST40");
        b = gh.isGroupLeader();
        if (!b) {
            gmsLogger.log(Level.SEVERE, "TEST40: isGroupLeader(): expected result: true, actual result: " + b);
        }

        // master and core102 are now shutdown so we should be leader
        numOfTests++;
        gmsLogger.log(Level.INFO, "Executing TEST70");
        sActual = gh.getGroupLeader();
        sExpected = "core101";
        if (!sExpected.equalsIgnoreCase(sActual)) {
            gmsLogger.log(Level.SEVERE, "TEST11: getGroupLeader(): expected result: " + sExpected + ", actual result:" + sActual);
        }



        // master is shutdown
        numOfTests++;
        gmsLogger.log(Level.INFO, "Executing TEST61");
        msActual = gh.getMemberState("master", 1200000, 0);
        msExpected = MemberStates.STOPPED;
        if (!msActual.equals(msExpected) && !msActual.equals(MemberStates.UNKNOWN)) {
            gmsLogger.log(Level.SEVERE, "TEST61: getMemberState(master, 1200000, 0): expected result: " + msExpected + ", actual result:" + msActual);
        }

        // try to lower fence for a member that is null
        numOfTests++;
        gmsLogger.log(Level.INFO, "Executing TEST44");
        try {
            gh.lowerFence("TestComponent", null);
            gmsLogger.log(Level.SEVERE, "TEST44: lowerFence(TestComponent, null): did not resulted in a GMSException:");
        } catch (IllegalArgumentException iae) {
        } catch (GMSException ge) {
        } catch (Exception ex) {
            gmsLogger.log(Level.SEVERE, "TEST43: lowerFence(TestComponent,null): resulted in a non GMSException:" + ex);
        }

        // try to lower fence for a null component of a member that does exist
        numOfTests++;
        gmsLogger.log(Level.INFO, "Executing TEST45");
        try {
            gh.lowerFence(null, "core102");
            gmsLogger.log(Level.SEVERE, "TEST45: lowerFence(null,core102): did not resulted in a GMSException:");
        } catch (IllegalArgumentException iae) {
        } catch (GMSException ge) {
        } catch (Exception ex) {
            gmsLogger.log(Level.SEVERE, "TEST45: lowerFence(null,core102): resulted in a non GMSException:" + ex);
        }

        // try to lower fence for a null component of a null member
        numOfTests++;
        gmsLogger.log(Level.INFO, "Executing TEST46");
        try {
            gh.lowerFence(null, null);
            gmsLogger.log(Level.SEVERE, "TEST46: lowerFence(null, null): did not resulted in a GMSException:");
        } catch (IllegalArgumentException iae) {
        } catch (GMSException ge) {
        } catch (Exception ex) {
            gmsLogger.log(Level.SEVERE, "TEST46: lowerFence(null, null): resulted in a non GMSException:" + ex);
        }

        // JMF: COMMENT OUT all getMemberState test for STOPPED for now.

        // try to get member state of core102 using negative threshold value
//        numOfTests++;
//        gmsLogger.log(Level.INFO, "Executing TEST47");
//        msActual = gh.getMemberState("core102", 12000000, 0);
//        msExpected = MemberStates.STOPPED;
//        if (!msActual.equals(msExpected)) {
//            gmsLogger.log(Level.SEVERE, "TEST47: getMemberState(core102, 1200000, 0): expected result: " + msExpected + ", actual result:" + msActual);
//        }

        // try to get member state of core102 using negative timeout value
//        numOfTests++;
//        gmsLogger.log(Level.INFO, "Executing TEST49");
//        msActual = gh.getMemberState("core102", 0, -1);
//        msExpected = MemberStates.STOPPED;
//        if (!msActual.equals(msExpected)) {
//            gmsLogger.log(Level.SEVERE, "TEST49: getMemberState(core102, 0, -1): expected result: " + msExpected + ", actual result:" + msActual);
//        }

        // try to get member state of core102 using negative threshold and timeout value
//        numOfTests++;
//        gmsLogger.log(Level.INFO, "Executing TEST50");
//        msActual = gh.getMemberState("core102", -1, -1);
//        msExpected = MemberStates.STOPPED;
//        if (!msActual.equals(msExpected)) {
//            gmsLogger.log(Level.SEVERE, "TEST50: getMemberState(core102, -1, -1): expected result: " + msExpected + ", actual result:" + msActual);
//        }

        // try to get member state of a member that does not exist
        numOfTests++;
        gmsLogger.log(Level.INFO, "Executing TEST51");
        msActual = gh.getMemberState("doesnotexist", 0, 0);
        msExpected = MemberStates.UNKNOWN;
        if (!msActual.equals(msExpected)) {
            gmsLogger.log(Level.SEVERE, "TEST51: getMemberState(doesnotexist, 0, 0): expected result: " + msExpected + ", actual result:" + msActual);
        }

        // try to get member state of a null member that does not exist
        numOfTests++;
        gmsLogger.log(Level.INFO, "Executing TEST70");
        try {
            msActual = gh.getMemberState(null, 0, 0);
            msExpected = MemberStates.UNKNOWN;
            if (!msActual.equals(msExpected)) {
                gmsLogger.log(Level.SEVERE, "TEST70: getMemberState(null, 0, 0): expected result: " + msExpected + ", actual result:" + msActual);
            }
        } catch (Exception ex) {
            gmsLogger.log(Level.SEVERE, "TEST70: getMemberState(null, 0, 0): resulted in an Exception:" + ex);

        }


        // try to raise fence of a null member

        numOfTests++;
        gmsLogger.log(Level.INFO, "Executing TEST53");
        try {
            gh.raiseFence("TestComponent", null);
            gmsLogger.log(Level.SEVERE, "TEST53: raiseFence(TestComponent, null): did not resulted in a GMSException:");
        } catch (IllegalArgumentException iae) {
        } catch (GMSException ge) {
        } catch (Exception ex) {
            gmsLogger.log(Level.SEVERE, "TEST53: raiseFence(TestComponent, null): resulted in a non GMSException:" + ex);
        }

        // try to raise fence of a null component of member core102
        numOfTests++;
        gmsLogger.log(Level.INFO, "Executing TEST54");
        try {
            gh.raiseFence(null, "core102");
            gmsLogger.log(Level.SEVERE, "TEST54: raiseFence(null,core102): did not resulted in a GMSException:");
        } catch (IllegalArgumentException iae) {
        } catch (GMSException ge) {
        } catch (Exception ex) {
            gmsLogger.log(Level.SEVERE, "TEST54: raiseFence(null,core102): resulted in a non GMSException:" + ex);
        }

        // try to raise fence of a null component of a nul member
        numOfTests++;
        gmsLogger.log(Level.INFO, "Executing TEST55");
        try {
            gh.raiseFence(null, null);
            gmsLogger.log(Level.SEVERE, "TEST55: raiseFence(null, null): did not resulted in a IllegalArgumentException:");
        } catch (IllegalArgumentException iae) {

        } catch (Exception ex) {
            gmsLogger.log(Level.SEVERE, "TEST55: raiseFence(null, null): resulted in a non GMSException:" + ex);
        }

        // member is shutdown
        numOfTests++;
        gmsLogger.log(Level.INFO, "Executing TEST56");
        b = gh.isMemberAlive("master");
        if (b) {
            gmsLogger.log(Level.SEVERE, "TEST56: isMemberAlive(master): expected result: false, actual result: " + b);
        }

        // member is shutdown
        numOfTests++;
        gmsLogger.log(Level.INFO, "Executing TEST57");
        b = gh.isMemberAlive("core102");
        if (b) {
            gmsLogger.log(Level.SEVERE, "TEST57: isMemberAlive(core102): expected result: false, actual result: " + b);
        }

        // member does not exist
        numOfTests++;
        gmsLogger.log(Level.INFO, "Executing TEST58");
        b = gh.isMemberAlive("doesnotexist");
        if (b) {
            gmsLogger.log(Level.SEVERE, "TEST58: isMemberAlive(doesnotexist): expected result: false, actual result: " + b);
        }

        // try to check null member
        try {
            numOfTests++;
            gmsLogger.log(Level.INFO, "Executing TEST59");
            b = gh.isMemberAlive(null);
            if (b) {
                gmsLogger.log(Level.SEVERE, "TEST59: isMemberAlive(null): expected result: false, actual result: " + b);
            }
        } catch (IllegalArgumentException iae) {
        } catch (Exception ex) {
            gmsLogger.log(Level.SEVERE, "TEST59: isMemberAlive(null): resulted in an Exception:" + ex);
        }

        // try to convert Group Handle object to a String and make sure it is not null or empty
        numOfTests++;
        gmsLogger.log(Level.INFO, "Executing TEST60");
        String string = gh.toString();
        if (string == null) {
            gmsLogger.log(Level.SEVERE, "TEST60: toString(): returned a null value");
        } else {
//            numOfTests++;
//            gmsLogger.log(Level.INFO, "Executing TEST61");
//            string = gh.toString();
//            if (string.equals("")) {
//                gmsLogger.log(Level.SEVERE, "TEST61: toString(): returned an empty string value");
//            }
        }


        leaveGroupAndShutdown(memberID, gms);
    }

    private void masterSpectatorMember() {

        long waitForStartTime = 0;
        long exceedTimeout = 0;
        boolean firstTime = true;
        long currentTime = 0;
        System.out.println("Starting master");


        //initialize Group Management Service and register for Group Events
        gms = initializeGMS(memberID, groupName, GroupManagementService.MemberType.SPECTATOR);
        gms.addActionFactory(new JoinedAndReadyNotificationActionFactoryImpl(new JoinAndReadyNotificationCallBack(memberID)));
        gms.addActionFactory(new PlannedShutdownActionFactoryImpl(new PlannedShutdownCallBack(memberID)));
        gms.addActionFactory(new MessageActionFactoryImpl(new MessageCallBack(memberID, logDir)), "TestComponent");

        gmsLogger.log(Level.INFO, "Joining Group " + groupName);
        try {
            gms.join();
        } catch (GMSException e) {
            gmsLogger.log(Level.SEVERE, "Exception occured :" + e, e);
            System.exit(1);
        }
        sleep(2000);

        gms.reportJoinedAndReadyState();
        gmsLogger.log(Level.INFO, "===================================================");
        gmsLogger.log(Level.INFO, "Waiting for all CORE members to send PLANNEDSHUTDOWN");


        // wait for testing to complete

        boolean started = false;
        while (true) {
            sleep(5000); // 5 second
            gmsLogger.log(Level.INFO, "===================================================");
            gmsLogger.log(Level.INFO, "CORE members who have sent PLANNEDSHUTDOWN are: (" + numberOfPlannedShutdownReceived.size() + ") " + numberOfPlannedShutdownReceived.toString());
            int currentCoreMembers = gms.getGroupHandle().getCurrentCoreMembers().size();
            if (numberOfPlannedShutdownReceived.size() == 2) {
                gmsLogger.log(Level.INFO, "===================================================");
                gmsLogger.log(Level.INFO, "PLANNEDSHUTDOWN received from all CORE members");
                gmsLogger.log(Level.INFO, "===================================================");
                break;
            } else if (started && currentCoreMembers == 0) {
                gmsLogger.log(Level.INFO, "===================================================");
                gmsLogger.log(Level.INFO, "Missed a PLANNEDSHUTDOWN. All core members have stopped. Only received PLANNEDSHUTDOWN from CORE members : (" + numberOfPlannedShutdownReceived.size() + ") " + numberOfPlannedShutdownReceived.toString());
                gmsLogger.log(Level.INFO, "===================================================");
                break;
            } else if (completedCheck.get()) {
                break;
            } else {
                if (!started && currentCoreMembers > 0) {
                    started = true;
                }

                // set a timeout of 5 minutes to wait once the first core reports plannedshutdown
                gmsLogger.log(Level.INFO, "Number of CORE members that still exist are:" + currentCoreMembers);
                if (firstTime) {
                    waitForStartTime = System.currentTimeMillis();
                    firstTime = false;
                }
                currentTime = System.currentTimeMillis();
                exceedTimeout = ((currentTime - waitForStartTime) / 1000);
                gmsLogger.log(Level.INFO, ("current timeout value=" + exceedTimeout+", Max="+exceedTimeoutLimit));
                if (exceedTimeout > exceedTimeoutLimit) {
                    gmsLogger.log(Level.SEVERE, memberID + " EXCEEDED " + (exceedTimeoutLimit/60) + " minute test timeout before we received all PLANNEDSHUTDOWN messages");
                    break;
                }
            }
        }

        leaveGroupAndShutdown(memberID, gms);
    }

    private void otherCoreMembers() {

        System.out.println("Starting core instance");

        //initialize Group Management Service and register for Group Events

        gmsLogger.log(Level.INFO, "Registering for group event notifications");
        gms = initializeGMS(memberID, groupName, GroupManagementService.MemberType.CORE);
        gms.addActionFactory(new JoinedAndReadyNotificationActionFactoryImpl(new JoinAndReadyNotificationCallBack(memberID)));
        gms.addActionFactory(new JoinNotificationActionFactoryImpl(new JoinNotificationCallBack(memberID)));
        gms.addActionFactory(new PlannedShutdownActionFactoryImpl(new PlannedShutdownCallBack(memberID)));

        gms.addActionFactory(new MessageActionFactoryImpl(new MessageCallBack(memberID, logDir)), "TestComponent");
        gms.addActionFactory(new MessageActionFactoryImpl(new MessageCallBack2(memberID, logDir)), "TestComponent2");

        gmsLogger.log(Level.INFO, "Joining Group " + groupName);
        try {
            gms.join();
        } catch (GMSException e) {
            gmsLogger.log(Level.SEVERE, "Exception occured :" + e, e);
            System.exit(1);
        }
        sleep(5000);

        while (!completedCheck.get()) {
            long waitTime = 2000; // 2 seconds

            gmsLogger.log(Level.INFO, ("Waiting " + waitTime / 1000 + " second in order to receive SHUTDOWN msg"));
            //gmsLogger.log(Level.INFO, ("members=" + gms.getGroupHandle().getCurrentCoreMembers().toString()));
            synchronized (completedCheck) {
                try {
                    completedCheck.wait(waitTime);
                } catch (InterruptedException ie) {
                }
            }
            System.out.println("members=" + gms.getGroupHandle().getCurrentCoreMembers().toString());
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
            gmsLogger.log(Level.INFO, "***JoinNotification received from: " + notification.getMemberToken());
            if (!(notification instanceof JoinNotificationSignal)) {
                gmsLogger.log(Level.SEVERE, "received unknown notification type:" + notification + " from:" + notification.getMemberToken());
            } else {
                if (notification.getMemberToken().equals("core101")) {
                    gms.reportJoinedAndReadyState();
                    gmsLogger.log(Level.INFO, memberID + " setting state to JoinedAndReady");
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
            gmsLogger.log(Level.INFO, "***JoinAndReadyNotification received from: " + notification.getMemberToken());
            if (!(notification instanceof JoinedAndReadyNotificationSignal)) {
                gmsLogger.log(Level.SEVERE, "received unknown notification type:" + notification + " from:" + notification.getMemberToken());
            } else {
                if (!notification.getMemberToken().equals("master")) {

                    // determine how many core members are ready to begin testing
                    JoinedAndReadyNotificationSignal readySignal = (JoinedAndReadyNotificationSignal) notification;
                    List<String> currentCoreMembers = readySignal.getCurrentCoreMembers();
                    numberOfJoinAndReady.set(0);
                    for (String coreName : currentCoreMembers) {
                        MemberStates state = gms.getGroupHandle().getMemberState(coreName, 6000, 3000);
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
                if (!notification.getMemberToken().equals("master")) {
                    // if we are core102 and we received a plannedshutdown then
                    // something went wrong and the testing is done so terminate
                    if (memberID.equalsIgnoreCase("core102") && notification.getMemberToken().equalsIgnoreCase("core101")) {
                        gmsLogger.log(Level.INFO, "Received PLANNEDSHUTDOWN message from " + notification.getMemberToken() + ", testing must have completed so exit");
                        completedCheck.set(true);
                        synchronized (completedCheck) {
                            completedCheck.notify();
                        }
                    }
                    numberOfPlannedShutdownReceived.put(member, "");
                    gmsLogger.log(Level.INFO, "CORE members who we have received PLANNEDSHUTDOWN from are: (" + numberOfPlannedShutdownReceived.size() + ") " + numberOfPlannedShutdownReceived.toString());
                }
            }

        }
    }

    private class MessageCallBack implements CallBack {

        private String memberID;
        private String logDir;

        public MessageCallBack(String memberID, String logDir) {
            this.memberID = memberID;
            this.logDir = logDir;
        }

        public void processNotification(Signal notification) {
            // gmsLogger.log(Level.INFO, "***Message received from: " + notification.getMemberToken());
            if (!(notification instanceof MessageSignal)) {
                gmsLogger.log(Level.SEVERE, "received unknown notification type:" + notification + " from:" + notification.getMemberToken());
            } else {
                try {
                    notification.acquire();

                    MessageSignal messageSignal = (MessageSignal) notification;
                    if ((messageSignal.getTargetComponent() == null)
                            || (messageSignal.getTargetComponent().equals("TestComponent"))) {

                        gmsLogger.log(Level.FINE, "TestComponent received msg from:" + notification.getMemberToken());

                        if (messageSignal.getTargetComponent() == null) {
                            gmsLogger.log(Level.FINE, "TestComponent: msg with Null TestComponent received from:" + notification.getMemberToken());
                        }
                        String msgString = null;
                        if (messageSignal.getMessage() != null) {
                            msgString = new String(messageSignal.getMessage());
                        }
                        if (msgString == null) {
                            gmsLogger.log(Level.SEVERE, "TestComponent: Received null msg");
                        } else {
                            gmsLogger.log(Level.FINE, "Comparing message |" + msgString + "| to see if it is a SHUTDOWN command");
                            if (msgString.contains("SHUTDOWN")) {
                                gmsLogger.log(Level.INFO, "Received SHUTDOWN message from " + notification.getMemberToken() + " !!!!!!!!");
                                completedCheck.set(true);
                                synchronized (completedCheck) {
                                    completedCheck.notify();
                                }
                            } else if (msgString.contains("HALT")) {
                                // this simulates a core member that terminates unexpectedly
                                gmsLogger.log(Level.INFO, "Received HALT message from " + notification.getMemberToken() + " !!!!!!!!");
                                System.exit(1);
                            } else {
                                if (msgString.length() > 0) {
                                    if (msgString.length() < 25) {
                                        gmsLogger.log(Level.INFO, "TestComponent: Received msg from:" + notification.getMemberToken() + "|" + msgString + "|, msgLength=[" + msgString.length() + "]|");
                                    } else {
                                        gmsLogger.log(Level.INFO, "TestComponent: Received msg from:" + notification.getMemberToken() + "|" + msgString.substring(0, 10) + "..." + msgString.substring(msgString.length() - 10, msgString.length()) + "|, msgLength=[" + msgString.length() + "]|");
                                    }
                                    int start = msgString.indexOf("TEST");
                                    int colon = msgString.indexOf(":");
                                    if (start >= 0) {
                                        String doneFileName = logDir + File.separator + GROUPHANDLE + "_" + memberID + "_TestComponent_" + msgString.substring(start, colon) + ".done";
                                        removeFile(doneFileName);

                                        String dataFileName = logDir + File.separator + GROUPHANDLE + "_" + memberID + "_TestComponent_" + msgString.substring(start, colon) + ".out";
                                        removeFile(dataFileName);

                                        gmsLogger.log(Level.INFO, "Writing msg to: " + dataFileName);

                                        File f = new File(dataFileName);
                                        PrintWriter p = null;
                                        try {
                                            p = new PrintWriter(f);
                                            p.print(msgString);
                                            p.close();
                                        } catch (FileNotFoundException fnfe) {
                                            gmsLogger.log(Level.SEVERE, "Exception opening file: " + dataFileName, fnfe);
                                        }
                                        createDoneFile(doneFileName);
                                    }
                                } else {
                                    // Zero length message received
                                    gmsLogger.log(Level.INFO, "Received zero length message from " + notification.getMemberToken() + " !!!!!!!!");

                                    String doneFileName = logDir + File.separator + GROUPHANDLE + "_" + memberID + "_TestComponent_EmptyMessage.done";
                                    removeFile(doneFileName);

                                    String dataFileName = logDir + File.separator + GROUPHANDLE + "_" + memberID + "_TestComponent_EmptyMessage.out";
                                    Integer value = 1;
                                    File f = new File(dataFileName);
                                    if (f.exists()) {
                                        String s = readDataFromFile(dataFileName,"processNotification");
                                        value = new Integer(s);
                                        value++;
                                    }
                                    PrintWriter p = null;
                                    try {
                                        p = new PrintWriter(f);
                                        p.print(value);
                                        p.close();
                                    } catch (FileNotFoundException fnfe) {
                                        gmsLogger.log(Level.SEVERE, "Exception opening file: " + dataFileName, fnfe);
                                    }
                                    createDoneFile(doneFileName);

                                }
                            }
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

    private class MessageCallBack2 implements CallBack {

        private String memberID;
        private String logDir;

        public MessageCallBack2(String memberID, String logDir) {
            this.memberID = memberID;
            this.logDir = logDir;
        }

        public void processNotification(Signal notification) {
            // gmsLogger.log(Level.INFO, "***Message received from: " + notification.getMemberToken());
            if (!(notification instanceof MessageSignal)) {
                gmsLogger.log(Level.SEVERE, "TestComponent2 received unknown notification type:" + notification + " from:" + notification.getMemberToken());
            } else {
                try {
                    notification.acquire();

                    MessageSignal messageSignal = (MessageSignal) notification;

                    if ((messageSignal.getTargetComponent() == null)
                            || (messageSignal.getTargetComponent().equals("TestComponent2"))) {
                        gmsLogger.log(Level.INFO, "TestComponent2 received msg from:" + notification.getMemberToken());

                        if (messageSignal.getTargetComponent() == null) {
                            gmsLogger.log(Level.FINE, "TestComponent2: msg with Null TestComponent received from:" + notification.getMemberToken());
                        }
                        String msgString = null;
                        if (messageSignal.getMessage() != null) {
                            msgString = new String(messageSignal.getMessage());
                        }
                        if (msgString == null) {
                            gmsLogger.log(Level.SEVERE, "TestComponent2: Received null msg");
                        } else {
                            if (msgString.length() < 25) {
                                gmsLogger.log(Level.INFO, "TestComponent2: Received msg from:" + notification.getMemberToken() + "|" + msgString + "|, msgLength=[" + msgString.length() + "]|");
                            } else {
                                gmsLogger.log(Level.INFO, "TestComponent2: Received msg from:" + notification.getMemberToken() + "|" + msgString.substring(0, 10) + "..." + msgString.substring(msgString.length() - 10, msgString.length()) + "|, msgLength=[" + msgString.length() + "]|");
                            }
                            int start = msgString.indexOf("TEST");
                            int colon = msgString.indexOf(":");
                            if (start >= 0) {

                                String doneFileName = logDir + File.separator + GROUPHANDLE + "_" + memberID + "_TestComponent2_" + msgString.substring(start, colon) + ".done";
                                removeFile(doneFileName);

                                String dataFileName = logDir + File.separator + GROUPHANDLE + "_" + memberID + "_TestComponent2_" + msgString.substring(start, colon) + ".out";
                                removeFile(dataFileName);

                                gmsLogger.log(Level.INFO, "Writing msg to: " + dataFileName);

                                File f = new File(dataFileName);
                                PrintWriter p = null;
                                try {
                                    p = new PrintWriter(f);
                                    p.print(msgString);
                                    p.close();
                                } catch (FileNotFoundException fnfe) {
                                    gmsLogger.log(Level.SEVERE, "Exception opening file: " + dataFileName, fnfe);
                                }

                                createDoneFile(doneFileName);

                            }
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

    public static void sleep(long i) {
        try {
            Thread.sleep(i);


        } catch (InterruptedException ex) {
        }
    }

    public static void waitForDoneFile(String fileName) {
        File f = new File(fileName);
        int count = 1;
        while (!f.exists()) {
            sleep(1000);
            // every 10 seconds spit out a message
            if (count%10 == 0) {
                gmsLogger.log(Level.INFO, "Waited "+count+" seconds for file:" + fileName + " to be created and it wasn't");
            }
            if (count++ >= 30) {
                gmsLogger.log(Level.SEVERE, "Waited 30 seconds for file:" + fileName + " to be created and it wasn't");
                break;
            }
        }
    }

    public static void createDoneFile(String fileName) {
        File f = new File(fileName);
        try {
            f.createNewFile();
        } catch (IOException ex) {
            gmsLogger.log(Level.SEVERE, "Could not create the file:" + fileName);
        }
    }

    public static void removeFile(String fileName) {
        File f = new File(fileName);
        if (f.exists()) {
            if (!f.delete()) {
                gmsLogger.log(Level.SEVERE, "Could not delete the file:" + fileName);
            }
        }
    }

    public static String readDataFromFile(String fileName, String testname) {
        String result = null;
        try {
            BufferedReader in = new BufferedReader(new FileReader(fileName));
            result = in.readLine();
        } catch (FileNotFoundException fnfe) {
            gmsLogger.log(Level.SEVERE, testname+":Could not access file:" + fileName, fnfe);
        } catch (IOException ioe) {
            gmsLogger.log(Level.SEVERE, testname+ ":Could not read from file:" + fileName, ioe);
        }
        return result;
    }
}

