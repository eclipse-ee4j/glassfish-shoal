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

/* this program is run using the runSimplePutGetRemove.sh */
package com.sun.enterprise.ee.tests.DataStore;

import org.glassfish.shoal.gms.api.core.CallBack;
import org.glassfish.shoal.gms.api.core.GMSConstants;
import org.glassfish.shoal.gms.api.core.GMSException;
import org.glassfish.shoal.gms.api.core.GMSFactory;
import org.glassfish.shoal.gms.api.core.GroupManagementService;
import org.glassfish.shoal.gms.api.core.JoinNotificationSignal;
import org.glassfish.shoal.gms.api.core.JoinedAndReadyNotificationSignal;
import org.glassfish.shoal.gms.api.core.MessageSignal;
import org.glassfish.shoal.gms.api.core.PlannedShutdownSignal;
import org.glassfish.shoal.gms.api.core.ServiceProviderConfigurationKeys;
import org.glassfish.shoal.gms.api.core.Signal;
import org.glassfish.shoal.gms.base.Utility;
import org.glassfish.shoal.gms.client.JoinNotificationActionFactoryImpl;
import org.glassfish.shoal.gms.client.JoinedAndReadyNotificationActionFactoryImpl;
import org.glassfish.shoal.gms.client.MessageActionFactoryImpl;
import org.glassfish.shoal.gms.client.PlannedShutdownActionFactoryImpl;
import org.glassfish.shoal.gms.logging.GMSLogDomain;
//import com.sun.enterprise.ee.cms.tests.GMSAdminConstants;
import org.glassfish.shoal.gms.logging.NiceLogFormatter;
import com.sun.enterprise.ee.cms.tests.GMSAdminAgent;
import com.sun.enterprise.ee.cms.tests.GMSAdminConstants;
import org.glassfish.shoal.gms.mgmt.transport.grizzly.GrizzlyUtil;
import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.ConsoleHandler;
import java.util.logging.ErrorManager;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.shoal.ha.cache.api.DataStore;
import org.shoal.ha.cache.api.DataStoreFactory;

public class PutGetRemoveTest implements CallBack {

    private int lifeTime = 0;
    private final String OKTOPROCEED = "ok_to_proceed";
    private final String WAITINGTOPROCEED = "waiting_to_proceed";
    private static final String CACHE_STORE_NAME = "cachestore";
    private DataStore<String, Serializable> ds = null;
    private GroupManagementService gms = null;
    private static final Logger gmsLogger = GMSLogDomain.getLogger(GMSLogDomain.GMS_LOGGER);
    private static final Level GMSDEFAULTLOGLEVEL = Level.WARNING;
    private static final Logger myLogger = java.util.logging.Logger.getLogger("PutGetRemoveTest");
    private static final Level TESTDEFAULTLOGLEVEL = Level.INFO;
    static String memberID = null;
    static int numberOfInstances = 0;
    static int payloadSize = 0;
    static int numberOfObjects = 0;
    static AtomicInteger numberOfMembers = new AtomicInteger(0);
    static String groupName = null;
    static AtomicBoolean startupComplete = new AtomicBoolean(false);
    static AtomicBoolean testingStarted = new AtomicBoolean(false);
    static GMSAdminAgent gaa;
    private static boolean isAdmin = false;
    private static Thread testThread;
    private AtomicInteger numberOfWAITINGTOPROCEED = new AtomicInteger(0);
    private AtomicInteger numberOfTestingComplete = new AtomicInteger(0);

    public static void main(String[] args) {
        if (args.length > 0) {
            if (args[0].equalsIgnoreCase("-h")) {
                usage();
            }
        }
        memberID = System.getProperty("INSTANCEID");
        groupName = System.getProperty("CLUSTERNAME");
        numberOfObjects = Integer.parseInt(System.getProperty("NUMOBJECTS", "0"));
        payloadSize = Integer.parseInt(System.getProperty("PAYLOADSIZE", "0"));
        if (memberID == null || groupName == null) {
            System.err.println("Missing either memberid or groupname");
            usage();
        }
        if (memberID.contains("instance")) {
            if (numberOfObjects == 0 || payloadSize == 0) {
                System.err.println("Missing either numberOfObjects groupname or payloadSize");
                usage();
            }
        } else if (memberID.equals("server")) {
            isAdmin = true;
        } else {
            System.err.println("ERROR: Invalid  memberid specfied [" + memberID + "]");
            usage();
        }
        // this configures the formatting of the gms log output
        Utility.setLogger(gmsLogger);
        Utility.setupLogHandler();
        // this sets the grizzly log level
        GrizzlyUtil.getLogger().setLevel(Level.WARNING);
        // this configures the formatting of the myLogger output
        PutGetRemoveTest.setupLogHandler();
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
            myLogger.log(Level.INFO, ("memberID=" + memberID));
            myLogger.log(Level.INFO, ("GroupName=" + groupName));
            myLogger.log(Level.INFO, ("numberOfObjects=" + numberOfObjects));
            myLogger.log(Level.INFO, ("payloadSize=" + payloadSize));
        }
        PutGetRemoveTest spgrt = new PutGetRemoveTest();
        spgrt.execute();
        System.out.println("================================================================");
        myLogger.log(Level.INFO, "Testing Complete");
    }

    public static void usage() {
        System.out.println(" For server:");
        System.out.println("    <memberid(server)> <groupName>");
        System.out.println(" For instances:");
        System.out.println("    <memberid(instancexxx)> <groupName>  <number_of_objects>  <payloadsize>");
        System.exit(0);
    }

    private void execute() {
        initialize();
        startGMS();
        gaa.waitTillNotified();
        leaveGroupAndShutdown(memberID, gms);
    }

    private void initialize() {
        if (isAdmin) {
            gms = initializeGMS(memberID, groupName, GroupManagementService.MemberType.SPECTATOR);
        } else {
            gms = initializeGMS(memberID, groupName, GroupManagementService.MemberType.CORE);
            //
            //register DataStore GMS handlers before joining GMS group so datastore receives GMS Joined and
            //Ready notifications
            //
            ds = DataStoreFactory.createDataStore(CACHE_STORE_NAME, memberID, groupName);
        }
        gaa = new GMSAdminAgent(gms, groupName, memberID, lifeTime);
        if (!isAdmin) {
            testThread = new DoTest(ds, gms);
        }
    }

    private void startGMS() {
        myLogger.log(Level.INFO, "Registering for group event notifications");
        gms.addActionFactory(new JoinedAndReadyNotificationActionFactoryImpl(this));
        gms.addActionFactory(new JoinNotificationActionFactoryImpl(this));
        gms.addActionFactory(new PlannedShutdownActionFactoryImpl(this));
        gms.addActionFactory(new MessageActionFactoryImpl(this), GMSAdminConstants.ADMINNAME);
        gms.addActionFactory(new MessageActionFactoryImpl(this), GMSAdminConstants.TESTCOORDINATOR);
        try {
            gms.join();
        } catch (GMSException e) {
            myLogger.log(Level.SEVERE, e.getLocalizedMessage());
        }
        sleep(3);
        myLogger.log(Level.INFO, "Reporting Joined and Ready");
        gms.reportJoinedAndReadyState(groupName);
    }

    private GroupManagementService initializeGMS(String memberID, String groupName, GroupManagementService.MemberType mType) {
        myLogger.log(Level.INFO, "Initializing Shoal for member: " + memberID + " group:" + groupName);
        Properties configProps = new Properties();
        configProps.put(ServiceProviderConfigurationKeys.MULTICASTADDRESS.toString(),
                System.getProperty("MULTICASTADDRESS", "229.9.1.2"));
        configProps.put(ServiceProviderConfigurationKeys.MULTICASTPORT.toString(), 2299);
//        myLogger.FINE("Is initial host="+System.getProperty("IS_INITIAL_HOST"));
        configProps.put(ServiceProviderConfigurationKeys.IS_BOOTSTRAPPING_NODE.toString(),
                System.getProperty("IS_INITIAL_HOST", "false"));
        configProps.put(ServiceProviderConfigurationKeys.FAILURE_DETECTION_RETRIES.toString(),
                System.getProperty("MAX_MISSED_HEARTBEATS", "3"));
        configProps.put(ServiceProviderConfigurationKeys.FAILURE_DETECTION_TIMEOUT.toString(),
                System.getProperty("HEARTBEAT_FREQUENCY", "2000"));
        //Uncomment this to receive loop back messages
        //configProps.put(ServiceProviderConfigurationKeys.LOOPBACK.toString(), "true");
        final String bindInterfaceAddress = System.getProperty("BIND_INTERFACE_ADDRESS");
        if (bindInterfaceAddress != null) {
            configProps.put(ServiceProviderConfigurationKeys.BIND_INTERFACE_ADDRESS.toString(), bindInterfaceAddress);
        }
        GMSFactory.setGMSEnabledState(groupName, Boolean.TRUE);
        return (GroupManagementService) GMSFactory.startGMSModule(
                memberID,
                groupName,
                mType,
                configProps);
    }

    private void leaveGroupAndShutdown(String memberID, GroupManagementService gms) {
        myLogger.log(Level.INFO, "Shutting down gms " + gms + "for member: " + memberID);
        gms.shutdown(GMSConstants.shutdownType.INSTANCE_SHUTDOWN);
    }

    public synchronized void processNotification(final Signal notification) {
        final String from = notification.getMemberToken();
        if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
            myLogger.log(TESTDEFAULTLOGLEVEL, "Received a NOTIFICATION from : " + from + ", " + notification);
        } // PLANNEDSHUTDOWN  HANDLING
        if (notification instanceof PlannedShutdownSignal) {
            // don't processess gmsadmincli shutdown messages
            if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
                myLogger.log(TESTDEFAULTLOGLEVEL, "Received PlannedShutdownNotification from member " + from);
            }
            // }
            // MESSAGE HANDLING
        } else if (notification instanceof MessageSignal) {
            MessageSignal messageSignal = (MessageSignal) notification;
            String msgString = new String(messageSignal.getMessage());
            //if (myLogger.isLoggable(DEFAULTTESTLOGLEVEL)) {
            //myLogger.log(DEFAULTTESTLOGLEVEL, "Message received was:" + msgString);
            myLogger.info("Message received from [" + from + "] was:" + msgString);
            //}
           /* if (msgString.equals(GMSAdminConstants.STARTUPCOMPLETE)) {
            if (!isAdmin) {
            if (!testingStarted.get()) {
            testingStarted.set(true);
            myLogger.info("Executing testing");
            testThread.start();
            }
            }
            } else */
            if (msgString.equals(GMSAdminConstants.STARTTESTING)) {
                numberOfMembers.set(gms.getGroupHandle().getCurrentCoreMembers().size());
                if (!isAdmin) {
                    if (!testingStarted.get()) {
                        testingStarted.set(true);
                        myLogger.info("Executing testing");
                        testThread.start();
                    }
                }
            } else if (msgString.equals(WAITINGTOPROCEED)) {
                if (isAdmin) {
                    numberOfWAITINGTOPROCEED.getAndIncrement();
                    myLogger.info("numberOfWAITINGTOPROCEED=" + numberOfWAITINGTOPROCEED.get());
                    myLogger.info("numberOfMembers=" + numberOfMembers.get());
                    if (numberOfWAITINGTOPROCEED.get() == numberOfMembers.get()) {
                        // if everyone has reported WAITINGTOPROCEED then broadcast OKToProcced
                        numberOfWAITINGTOPROCEED.set(0);
                        try {
                            myLogger.info("Broadcasting " + OKTOPROCEED + " to the cluster");
                            gms.getGroupHandle().sendMessage(GMSAdminConstants.TESTEXECUTOR, OKTOPROCEED.getBytes());
                        } catch (GMSException ge1) {
                            myLogger.log(Level.SEVERE, "Exception occurred while broadcasting message: " + OKTOPROCEED + ge1, ge1);
                        }
                    }
                }
            } else if (msgString.equals(GMSAdminConstants.TESTINGCOMPLETE)) {
                if (isAdmin) {
                    numberOfTestingComplete.getAndIncrement();
                    myLogger.info("numberOfTestingComplete=" + numberOfTestingComplete.get());
                    myLogger.info("numberOfMembers=" + numberOfMembers.get());
                    if (numberOfTestingComplete.get() == numberOfMembers.get()) {
                        // if everyone has reported TestingComplete then broadcast TestingComplete to CLI
                        numberOfTestingComplete.set(0);
                        try {
                            myLogger.info("Broadcasting " + GMSAdminConstants.TESTINGCOMPLETE + " to the " + GMSAdminConstants.ADMINCLI);
                            gms.getGroupHandle().sendMessage(GMSAdminConstants.ADMINCLI, GMSAdminConstants.TESTINGCOMPLETE.getBytes());
                            gms.getGroupHandle().sendMessage(GMSAdminConstants.ADMINCLI, GMSAdminConstants.TESTINGCOMPLETE.getBytes());
                            gms.getGroupHandle().sendMessage(GMSAdminConstants.ADMINCLI, GMSAdminConstants.TESTINGCOMPLETE.getBytes());
                        } catch (GMSException ge1) {
                            myLogger.log(Level.SEVERE, "Exception occurred while broadcasting message: " + GMSAdminConstants.TESTINGCOMPLETE + ge1, ge1);
                        }
                    }
                }
            }
        } else if (notification instanceof JoinNotificationSignal) {
            myLogger.info("Received Join Notification from:" + from);
        } else if (notification instanceof JoinedAndReadyNotificationSignal) {
            myLogger.info("Received JoinedAndReady Notification from:" + from);
        }
    }

    public static byte[] createPayload(int size, byte startNum) {
        byte[] bArray = new byte[size];
        byte j = startNum;
        int k = 0;
        for (int i = 0; i < size; i++) {
            bArray[i] = j;
            k++;
            if (k > 9) {
                k = 0;
                j++;
                if (j > 9) {
                    j = 0;
                }
            }
        }
        return bArray;
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
        myLogger.setLevel(Level.parse("INFO"));
    }

    public static void sleep(int i) {
        try {
            Thread.sleep(i * 1000);
        } catch (InterruptedException ex) {
        }
    }

    public class DoTest extends Thread implements CallBack {

        private GroupManagementService gms;
        private Thread thread;
        private byte[][] persistedData = new byte[numberOfObjects][payloadSize];
        private DataStore<String, Serializable> ds = null;
        String onceExisted = "_ONCEEXISTED";
        String onceExistedId = memberID + onceExisted;
        String onceExistedValue = memberID + "_onceexisted";

        public DoTest(DataStore<String, Serializable> ds, GroupManagementService gms) {
            this.ds = ds;
            this.gms = gms;
            myLogger.info("Registering " + GMSAdminConstants.TESTEXECUTOR);
            gms.addActionFactory(new MessageActionFactoryImpl(this), GMSAdminConstants.TESTEXECUTOR);
        }

        @Override
        public void start() {
            thread = new Thread(this, "DOTEST");
            thread.start();
        }

        public synchronized void processNotification(final Signal notification) {
            final String from = notification.getMemberToken();
            myLogger.info("Received a NOTIFICATION from member: " + from);
            if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
                myLogger.log(TESTDEFAULTLOGLEVEL, "Received a NOTIFICATION from member: " + from);
            }
            if (notification instanceof MessageSignal) {
                MessageSignal messageSignal = (MessageSignal) notification;
                String msgString = new String(messageSignal.getMessage());
                //if (myLogger.isLoggable(DEFAULTTESTLOGLEVEL)) {
                //myLogger.log(DEFAULTTESTLOGLEVEL, "Message received was:" + msgString);
                myLogger.info("Message received from [" + from + "] was:" + msgString);
                //}
                if (msgString.equals(OKTOPROCEED)) {
                    synchronized (numberOfWAITINGTOPROCEED) {
                        numberOfWAITINGTOPROCEED.notifyAll();
                    }
                }
            }
        }

        public void run() {
            // do not move the location of this test (id being the first test)
            CreateOnceExistedTest();
            CreateMultiPutRemoveTest();
            ObjectsTest();
            PersistLoadSelfDataTest();
            SendNotificationAndWait();
            // LoadOthersDataTest();
            //SendNotificationAndWait();
            RemoveTest();
            // do not move the location of this test (id being the last test)
            AccessOnceExistedFromOtherMembersTest();


            SendTestingComplete();
        }

        private void SendNotificationAndWait() {
            try {
                myLogger.log(Level.INFO, "Sending " + WAITINGTOPROCEED + " msg to " + GMSAdminConstants.ADMINNAME);
                gms.getGroupHandle().sendMessage(GMSAdminConstants.ADMINNAME, GMSAdminConstants.TESTCOORDINATOR, WAITINGTOPROCEED.getBytes());
            } catch (GMSException ge1) {
                myLogger.log(Level.SEVERE, "Exception occurred while broadcasting message: " + WAITINGTOPROCEED + ge1, ge1);
            }
            synchronized (numberOfWAITINGTOPROCEED) {
                try {
                    numberOfWAITINGTOPROCEED.wait(0);
                    myLogger.log(Level.INFO, "Resuming Testing");
                } catch (InterruptedException ie) {
                }
            }
        }

        private void SendTestingComplete() {
            try {
                myLogger.log(Level.INFO, "Broadcasting " + GMSAdminConstants.TESTINGCOMPLETE + " msg to " + GMSAdminConstants.TESTCOORDINATOR);
                gms.getGroupHandle().sendMessage(GMSAdminConstants.TESTCOORDINATOR, GMSAdminConstants.TESTINGCOMPLETE.getBytes());
            } catch (GMSException ge1) {
                myLogger.log(Level.SEVERE, "Exception occurred while broadcasting message: " + GMSAdminConstants.TESTINGCOMPLETE + ge1, ge1);
            }
        }

        private void CreateOnceExistedTest() {
            myLogger.log(Level.INFO, "Starting CreateOnceExistedTest");
            if (ds != null) {
                myLogger.log(Level.INFO, "Persisting " + onceExistedId + "," + onceExistedValue);
                try {
                    ds.put(onceExistedId, onceExistedValue);
                } catch (Exception e) {
                    myLogger.log(Level.SEVERE, "ERROR: occurred while saving [" + onceExistedId + "] - " + e.getLocalizedMessage(), e);
                }
                // give time for the data to persist
//                PutGetRemoveTest.sleep(1);
                try {
                    myLogger.log(Level.INFO, "Accessing " + onceExistedId);
                    String tmp = (String) ds.get(onceExistedId);
                    if (tmp != null) {
                        if (!tmp.equals(onceExistedValue)) {
                            myLogger.log(Level.SEVERE, "ERROR: " + onceExistedValue + " != " + tmp);
                        }
                    } else {
                        myLogger.log(Level.SEVERE, "ERROR: get of " + onceExistedId + " resulted in a null return value");
                    }
                } catch (Exception e) {
                    myLogger.log(Level.SEVERE, "ERROR: occurred while accessing [" + onceExistedId + "] - " + e.getLocalizedMessage(), e);
                }
                myLogger.log(Level.INFO, "Removing " + onceExistedId);
                try {
                    ds.remove(onceExistedId);
                } catch (Exception e) {
                    myLogger.log(Level.SEVERE, "ERROR: occurred while removing [" + onceExistedId + "] - " + e.getLocalizedMessage(), e);
                }
                // give time for the data to be removed
//                PutGetRemoveTest.sleep(1);
                //
                // this might need to be modified the wait until remove has been sucessfully accomplished before
                // moving on
                //
                try {
                    myLogger.log(Level.INFO, "Trying to access " + onceExistedId + " after remove");
                    String tmp = (String) ds.get(onceExistedId);
                    if (tmp != null) {
                        myLogger.log(Level.SEVERE, "ERROR: get of " + onceExistedId + " resulted in non-null return value[" + tmp + "]");
                    }
                } catch (Exception e) {
                    myLogger.log(Level.SEVERE, "ERROR: occurred while accessing removed [" + onceExistedId + "] - " + e.getLocalizedMessage(), e);
                }
            } else {
                myLogger.log(Level.SEVERE, "ERROR: DataStore is null");
            }
        }

        private void AccessOnceExistedFromOtherMembersTest() {
            // Attempt to access the first data value that each member persisted and removed.
            // The value should not be available.
            myLogger.log(Level.INFO, "Starting AccessOnceExistedFromOtherMembersTest");
            if (ds != null) {
                List<String> members = gms.getGroupHandle().getCurrentCoreMembers();
                members.remove(memberID);
                for (String member : members) {
                    try {
                        String id = member + onceExisted;
                        myLogger.log(Level.INFO, "Trying to access " + id);
                        String tmp = (String) ds.get(id);
                        if (tmp != null) {
                            myLogger.log(Level.SEVERE, "ERROR: get of " + id + " resulted in non-null return value[" + tmp + "]");
                        }
                    } catch (Exception e) {
                        myLogger.log(Level.SEVERE, "ERROR: occurred while accessing removed [ONCEEXISTED] - " + e.getLocalizedMessage(), e);
                    }
                }
            } else {
                myLogger.log(Level.SEVERE, "ERROR: DataStore is null");
            }
        }

        private void CreateMultiPutRemoveTest() {
            myLogger.log(Level.INFO, "Starting CreateMultiPutRemoveTest");
            myLogger.log(Level.INFO, "Persisting Data");
            if (ds != null) {
                String id = memberID + "cmprt";
                int value = 0;
                for (; value <= 9; value++) {
                    try {
                        ds.put(id, value);
                    } catch (Exception e) {
                        myLogger.log(Level.SEVERE, "ERROR: occurred while saving [" + onceExistedId + "] - " + e.getLocalizedMessage(), e);
                    }
                }
                value--;
                // give time for the data to be persisted
//                PutGetRemoveTest.sleep(4);
                try {
                    myLogger.log(Level.INFO, "Accessing " + id);
                    Integer tmp = (Integer) ds.get(id);
                    if (tmp != null) {
                        if (tmp.intValue() != value) {
                            myLogger.log(Level.SEVERE, "ERROR: " + tmp.intValue() + " != " + value);
                        }
                    } else {
                        myLogger.log(Level.SEVERE, "ERROR: get of " + id + " resulted in a null return value");
                    }
                } catch (Exception e) {
                    myLogger.log(Level.SEVERE, "ERROR: occurred while accessing [" + id + "] - " + e.getLocalizedMessage(), e);
                }
                myLogger.log(Level.INFO, "Removing " + id);
                try {
                    ds.remove(id);
                } catch (Exception e) {
                    myLogger.log(Level.SEVERE, "ERROR: occurred while removing [" + id + "] - " + e.getLocalizedMessage(), e);
                }
                // give time for the data to be removed
//                PutGetRemoveTest.sleep(4);
                try {
                    myLogger.log(Level.INFO, "Trying to access " + id + " after remove");
                    String tmp = (String) ds.get(id);
                    if (tmp != null) {
                        myLogger.log(Level.SEVERE, "ERROR: get of " + id + " resulted in non-null return value[" + tmp + "]");
                    }
                } catch (Exception e) {
                    myLogger.log(Level.SEVERE, "ERROR: occurred while accessing removed [" + id + "] - " + e.getLocalizedMessage(), e);
                }
            } else {
                myLogger.log(Level.SEVERE, "ERROR: DataStore is null");
            }
        }

        private void ObjectsTest() {
            myLogger.log(Level.INFO, "Starting ObjectsTest");
            if (ds != null) {
                myLogger.log(Level.INFO, "Persisting Data");
                Date _date = new Date();

                try {
                    ds.put(memberID + ":TRUE", Boolean.valueOf(true));
                    ds.put(memberID + ":FALSE", Boolean.valueOf(false));
                    ds.put(memberID + ":STRING", "String");
                    ds.put(memberID + ":DATE", _date);
                    ds.put(memberID + ":Character_MIN", Character.MIN_VALUE);
                    ds.put(memberID + ":Character_MAX", Character.MAX_VALUE);
                    ds.put(memberID + ":BYTE_MIN", Byte.MIN_VALUE);
                    ds.put(memberID + ":BYTE_MAX", Byte.MAX_VALUE);
                    ds.put(memberID + ":SHORT_MIN", Short.MIN_VALUE);
                    ds.put(memberID + ":SHORT_MAX", Short.MAX_VALUE);
                    ds.put(memberID + ":INTEGER_MIN", Integer.MIN_VALUE);
                    ds.put(memberID + ":INTEGER_MAX", Integer.MAX_VALUE);
                    ds.put(memberID + ":LONG_MIN", Long.MIN_VALUE);
                    ds.put(memberID + ":LONG_MAX", Long.MAX_VALUE);
                    ds.put(memberID + ":FLOAT_MIN", Float.MIN_VALUE);
                    ds.put(memberID + ":FLOAT_MAX", Float.MAX_VALUE);
                    ds.put(memberID + ":DOUBLE_MIN", Double.MIN_VALUE);
                    ds.put(memberID + ":DOUBLE_MAX", Double.MAX_VALUE);
                } catch (Exception e) {
                    myLogger.log(Level.SEVERE, "ERROR: occurred while accessing saving [" + memberID + ":type] - " + e.getLocalizedMessage(), e);
                }
                // do this to ensure that last item has persisted until we go forward.
                // this is only temporary
              /*  while (true) {
                Double tmp = (Double) ds.get("DOUBLE_MAX");
                if (tmp != null) {
                myLogger.log(Level.INFO, "STOPGAP - DOUBLE_MAX exists, no need to wait");
                break;
                }
//                PutGetRemoveTest.sleep(2);
                myLogger.log(Level.INFO, "STOPGAP - Waiting 2 seconds for DOUBLE_MAX to not return null before continuing");
                }
                 */

                // give time for the data to be persisted
//                PutGetRemoveTest.sleep(4);
                myLogger.log(Level.INFO, "Accessing persisted data");
                try {
                    myLogger.log(Level.INFO, "Accessing TRUE");
                    Boolean tmp = (Boolean) ds.get(memberID + ":TRUE");
                    if (tmp != null) {
                        if (tmp.booleanValue() != true) {
                            myLogger.log(Level.SEVERE, "ERROR: true != " + tmp.booleanValue());
                        }
                    } else {
                        myLogger.log(Level.SEVERE, "ERROR: get of TRUE resulted in a null return value");
                    }
                } catch (Exception e) {
                    myLogger.log(Level.SEVERE, "ERROR: occurred while accessing [true] - " + e.getLocalizedMessage(), e);
                }
                try {
                    myLogger.log(Level.INFO, "Accessing FALSE");
                    Boolean tmp = (Boolean) ds.get(memberID + ":FALSE");
                    if (tmp != null) {
                        if (tmp.booleanValue() != false) {
                            myLogger.log(Level.SEVERE, "ERROR: true != " + tmp.booleanValue());
                        }
                    } else {
                        myLogger.log(Level.SEVERE, "ERROR: get of TRUE resulted in a null return value");
                    }
                } catch (Exception e) {
                    myLogger.log(Level.SEVERE, "ERROR: occurred while accessing [false] - " + e.getLocalizedMessage(), e);
                }
                try {
                    myLogger.log(Level.INFO, "Accessing STRING");
                    String tmp = (String) ds.get(memberID + ":STRING");
                    if (tmp != null) {
                        if (!tmp.equals("String")) {
                            myLogger.log(Level.SEVERE, "ERROR: STRING != " + tmp);
                        }
                    } else {
                        myLogger.log(Level.SEVERE, "ERROR: get of STRING resulted in a null return value");
                    }
                } catch (Exception e) {
                    myLogger.log(Level.SEVERE, "ERROR: occurred while accessing [STRING] - " + e.getLocalizedMessage(), e);
                }
                try {
                    myLogger.log(Level.INFO, "Accessing DATE");
                    Date tmp = (Date) ds.get(memberID + ":DATE");
                    if (tmp != null) {
                        if (!tmp.equals(_date)) {
                            myLogger.log(Level.SEVERE, "ERROR: DATE[" + tmp + "] != [" + _date + ":]");
                        }
                    } else {
                        myLogger.log(Level.SEVERE, "ERROR: get of DATE resulted in a null return value");
                    }
                } catch (Exception e) {
                    myLogger.log(Level.SEVERE, "ERROR: occurred while accessing [DATE] - " + e.getLocalizedMessage(), e);
                }
                try {
                    myLogger.log(Level.INFO, "Accessing Character_MIN");
                    Character tmp = (Character) ds.get(memberID + ":Character_MIN");
                    if (tmp != null) {
                        if (tmp.charValue() != Character.MIN_VALUE) {
                            myLogger.log(Level.SEVERE, "ERROR: Character.MIN_VALUE != " + tmp.charValue());
                        }
                    } else {
                        myLogger.log(Level.SEVERE, "ERROR: get of Character_MIN resulted in a null return value ");
                    }
                } catch (Exception e) {
                    myLogger.log(Level.SEVERE, "ERROR: occurred while accessing [Character.MIN_VALUE] - " + e.getLocalizedMessage(), e);
                }
                try {
                    myLogger.log(Level.INFO, "Accessing Character_MAX");
                    Character tmp = (Character) ds.get(memberID + ":Character_MAX");
                    if (tmp != null) {
                        if (tmp.charValue() != Character.MAX_VALUE) {
                            myLogger.log(Level.SEVERE, "ERROR: Character.MAX_VALUE != " + tmp.charValue());
                        }
                    } else {
                        myLogger.log(Level.SEVERE, "ERROR: get of Character_MAX resulted in a null return value");
                    }
                } catch (Exception e) {
                    myLogger.log(Level.SEVERE, "ERROR: occurred while accessing [Character.MAX_VALUE] - " + e.getLocalizedMessage(), e);
                }
                try {
                    myLogger.log(Level.INFO, "Accessing BYTE_MIN");
                    Byte tmp = (Byte) ds.get(memberID + ":BYTE_MIN");
                    if (tmp != null) {
                        if (tmp.byteValue() != Byte.MIN_VALUE) {
                            myLogger.log(Level.SEVERE, "ERROR: BYTE.MIN_VALUE != " + tmp);
                        }
                    } else {
                        myLogger.log(Level.SEVERE, "ERROR: get of BYTE_MIN resulted in a null return value");
                    }
                } catch (Exception e) {
                    myLogger.log(Level.SEVERE, "ERROR: occurred while accessing [BYTE.MIN_VALUE] - " + e.getLocalizedMessage(), e);
                }
                try {
                    myLogger.log(Level.INFO, "Accessing BYTE_MAX");
                    Byte tmp = (Byte) ds.get(memberID + ":BYTE_MAX");
                    if (tmp != null) {
                        if (tmp.byteValue() != Byte.MAX_VALUE) {
                            myLogger.log(Level.SEVERE, "ERROR: BYTE.MAX_VALUE != " + tmp);
                        }
                    } else {
                        myLogger.log(Level.SEVERE, "ERROR: get of BYTE_MAX resulted in a null return value");
                    }
                } catch (Exception e) {
                    myLogger.log(Level.SEVERE, "ERROR: occurred while accessing [BYTE.MAX_VALUE] - " + e.getLocalizedMessage(), e);
                }
                try {
                    myLogger.log(Level.INFO, "Accessing SHORT_MIN");
                    Short tmp = (Short) ds.get(memberID + ":SHORT_MIN");
                    if (tmp != null) {
                        if (tmp.shortValue() != Short.MIN_VALUE) {
                            myLogger.log(Level.SEVERE, "ERROR: SHORT.MIN_VALUE != " + tmp.shortValue());
                        }
                    } else {
                        myLogger.log(Level.SEVERE, "ERROR: get of SHORT_MIN resulted in a null return value");
                    }
                } catch (Exception e) {
                    myLogger.log(Level.SEVERE, "ERROR: occurred while accessing [SHORT.MIN_VALUE] - " + e.getLocalizedMessage(), e);
                }
                try {
                    myLogger.log(Level.INFO, "Accessing SHORT_MAX");
                    Short tmp = (Short) ds.get(memberID + ":SHORT_MAX");
                    if (tmp != null) {
                        if (tmp.shortValue() != Short.MAX_VALUE) {
                            myLogger.log(Level.SEVERE, "ERROR: SHORT.MAX_VALUE != " + tmp.shortValue());
                        }
                    } else {
                        myLogger.log(Level.SEVERE, "ERROR: get of SHORT_MAX resulted in a null return value");
                    }
                } catch (Exception e) {
                    myLogger.log(Level.SEVERE, "ERROR: occurred while accessing [SHORT.MAX_VALUE] - " + e.getLocalizedMessage(), e);
                }
                try {
                    myLogger.log(Level.INFO, "Accessing INTEGER_MIN");
                    Integer tmp = (Integer) ds.get(memberID + ":INTEGER_MIN");
                    if (tmp != null) {
                        if (tmp.intValue() != Integer.MIN_VALUE) {
                            myLogger.log(Level.SEVERE, "ERROR: INTEGER.MIN_VALUE != " + tmp.intValue());
                        }
                    } else {
                        myLogger.log(Level.SEVERE, "ERROR: get of INTEGER_MIN resulted in a null return value");
                    }
                } catch (Exception e) {
                    myLogger.log(Level.SEVERE, "ERROR: occurred while accessing [INTEGER.MIN_VALUE] - " + e.getLocalizedMessage(), e);
                }
                try {
                    myLogger.log(Level.INFO, "Accessing INTEGER_MAX");
                    Integer tmp = (Integer) ds.get(memberID + ":INTEGER_MAX");
                    if (tmp != null) {
                        if (tmp.intValue() != Integer.MAX_VALUE) {
                            myLogger.log(Level.SEVERE, "ERROR: INTEGER.MAX_VALUE != " + tmp.intValue());
                        }
                    } else {
                        myLogger.log(Level.SEVERE, "ERROR: get of INTEGER_MAX resulted in a null return value");
                    }
                } catch (Exception e) {
                    myLogger.log(Level.SEVERE, "ERROR: occurred while accessing [INTEGER.MAX_VALUE] - " + e.getLocalizedMessage(), e);
                }
                try {
                    myLogger.log(Level.INFO, "Accessing LONG_MIN");
                    Long tmp = (Long) ds.get(memberID + ":LONG_MIN");
                    if (tmp != null) {
                        if (tmp.longValue() != Long.MIN_VALUE) {
                            myLogger.log(Level.SEVERE, "ERROR: LONG.MIN_VALUE != " + tmp.longValue());
                        }
                    } else {
                        myLogger.log(Level.SEVERE, "ERROR: get of LONG_MIN resulted in a null return value");
                    }
                } catch (Exception e) {
                    myLogger.log(Level.SEVERE, "ERROR: occurred while accessing [LONG.MIN_VALUE] - " + e.getLocalizedMessage(), e);
                }
                try {
                    myLogger.log(Level.INFO, "Accessing LONG_MAX");
                    Long tmp = (Long) ds.get(memberID + ":LONG_MAX");
                    if (tmp != null) {
                        if (tmp.longValue() != Long.MAX_VALUE) {
                            myLogger.log(Level.SEVERE, "ERROR: LONG.MAX_VALUE != " + tmp.longValue());
                        }
                    } else {
                        myLogger.log(Level.SEVERE, "ERROR: get of LONG_MAX resulted in a null return value");
                    }
                } catch (Exception e) {
                    myLogger.log(Level.SEVERE, "ERROR: occurred while accessing [LONG.MAX_VALUE] - " + e.getLocalizedMessage(), e);
                }
                try {
                    myLogger.log(Level.INFO, "Accessing FLOAT_MIN");
                    Float tmp = (Float) ds.get(memberID + ":FLOAT_MIN");
                    if (tmp != null) {
                        if (tmp.floatValue() != Float.MIN_VALUE) {
                            myLogger.log(Level.SEVERE, "ERROR: FLOAT.MIN_VALUE != " + tmp.floatValue());
                        }
                    } else {
                        myLogger.log(Level.SEVERE, "ERROR: get of FLOAT_MIN resulted in a null return value");
                    }
                } catch (Exception e) {
                    myLogger.log(Level.SEVERE, "ERROR: occurred while accessing [FLOAT.MIN_VALUE] - " + e.getLocalizedMessage(), e);
                }
                try {
                    myLogger.log(Level.INFO, "Accessing FLOAT_MAX");
                    Float tmp = (Float) ds.get(memberID + ":FLOAT_MAX");
                    if (tmp != null) {
                        if (tmp.floatValue() != Float.MAX_VALUE) {
                            myLogger.log(Level.SEVERE, "ERROR: FLOAT.MAX_VALUE != " + tmp.floatValue());
                        }
                    } else {
                        myLogger.log(Level.SEVERE, "ERROR: get of FLOAT_MAX resulted in a null return value");
                    }
                } catch (Exception e) {
                    myLogger.log(Level.SEVERE, "ERROR: occurred while accessing [FLOAT.MAX_VALUE] - " + e.getLocalizedMessage(), e);
                }
                try {
                    myLogger.log(Level.INFO, "Accessing DOUBLE_MIN");
                    Double tmp = (Double) ds.get(memberID + ":DOUBLE_MIN");
                    if (tmp != null) {
                        if (tmp.doubleValue() != Double.MIN_VALUE) {
                            myLogger.log(Level.SEVERE, "ERROR: DOUBLE.MIN_VALUE != " + tmp.doubleValue());
                        }
                    } else {
                        myLogger.log(Level.SEVERE, "ERROR: get of DOUBLE_MIN resulted in a null return value");
                    }
                } catch (Exception e) {
                    myLogger.log(Level.SEVERE, "ERROR: occurred while accessing [DOUBLE.MIN_VALUE] - " + e.getLocalizedMessage(), e);
                }
                try {
                    myLogger.log(Level.INFO, "Accessing DOUBLE_MAX");
                    Double tmp = (Double) ds.get(memberID + ":DOUBLE_MAX");
                    if (tmp != null) {
                        if (tmp.doubleValue() != Double.MAX_VALUE) {
                            myLogger.log(Level.SEVERE, "ERROR: DOUBLE.MAX_VALUE != " + tmp.doubleValue());
                        }
                    } else {
                        myLogger.log(Level.SEVERE, "ERROR: get of DOUBLE_MAX resulted in a null return value");
                    }
                } catch (Exception e) {
                    myLogger.log(Level.SEVERE, "ERROR: occurred while accessing [DOUBLE.MAX_VALUE] - " + e.getLocalizedMessage(), e);
                }
                myLogger.log(Level.INFO, "Removing persisted data");
                try {
                    ds.remove(memberID + ":TRUE");
                    ds.remove(memberID + ":FALSE");
                    ds.remove(memberID + ":STRING");
                    ds.remove(memberID + ":DATE");
                    ds.remove(memberID + ":Character_MIN");
                    ds.remove(memberID + ":Character_MAX");
                    ds.remove(memberID + ":BYTE_MIN");
                    ds.remove(memberID + ":BYTE_MAX");
                    ds.remove(memberID + ":SHORT_MIN");
                    ds.remove(memberID + ":SHORT_MAX");
                    ds.remove(memberID + ":INTEGER_MIN");
                    ds.remove(memberID + ":INTEGER_MAX");
                    ds.remove(memberID + ":LONG_MIN");
                    ds.remove(memberID + ":LONG_MAX");
                    ds.remove(memberID + ":FLOAT_MIN");
                    ds.remove(memberID + ":FLOAT_MAX");
                    ds.remove(memberID + ":DOUBLE_MIN");
                    ds.remove(memberID + ":DOUBLE_MAX");
                } catch (Exception e) {
                    myLogger.log(Level.SEVERE, "ERROR: occurred while removing  [" + memberID + ":type] - " + e.getLocalizedMessage(), e);
                }
                // give time for the data to be removed
//                PutGetRemoveTest.sleep(4);
                myLogger.log(Level.INFO, "Verify removed data");
                try {
                    myLogger.log(Level.INFO, "Accessing TRUE");
                    Boolean tmp = (Boolean) ds.get(memberID + ":TRUE");
                    if (tmp != null) {
                        myLogger.log(Level.SEVERE, "ERROR: get of TRUE resulted in a non-null return value" + tmp);
                    }
                } catch (Exception e) {
                    myLogger.log(Level.SEVERE, "ERROR: occurred while accessing removed [true] - " + e.getLocalizedMessage(), e);
                }
                try {
                    myLogger.log(Level.INFO, "Accessing FALSE");
                    Boolean tmp = (Boolean) ds.get(memberID + ":FALSE");
                    if (tmp != null) {
                        myLogger.log(Level.SEVERE, "ERROR: get of TRUE resulted in a non-null return value" + tmp);
                    }
                } catch (Exception e) {
                    myLogger.log(Level.SEVERE, "ERROR: occurred while accessing removed [false] - " + e.getLocalizedMessage(), e);
                }
                try {
                    myLogger.log(Level.INFO, "Accessing STRING");
                    String tmp = (String) ds.get(memberID + ":STRING");
                    if (tmp != null) {
                        myLogger.log(Level.SEVERE, "ERROR: get of STRING resulted in non-null return value" + tmp);
                    }
                } catch (Exception e) {
                    myLogger.log(Level.SEVERE, "ERROR: occurred while accessing removed [STRING] - " + e.getLocalizedMessage(), e);
                }
                try {
                    myLogger.log(Level.INFO, "Accessing DATE");
                    Date tmp = (Date) ds.get(memberID + ":DATE");
                    if (tmp != null) {
                        myLogger.log(Level.SEVERE, "ERROR: get of DATE resulted in non-null return value" + tmp);
                    }
                } catch (Exception e) {
                    myLogger.log(Level.SEVERE, "ERROR: occurred while accessing removed [DATE] - " + e.getLocalizedMessage(), e);
                }
                try {
                    myLogger.log(Level.INFO, "Accessing Character_MIN");
                    Character tmp = (Character) ds.get(memberID + ":Character_MIN");
                    if (tmp != null) {
                        myLogger.log(Level.SEVERE, "ERROR: get of Character_MIN resulted in non-null return value" + tmp);
                    }
                } catch (Exception e) {
                    myLogger.log(Level.SEVERE, "ERROR: occurred while accessing removed [Character.MIN_VALUE] - " + e.getLocalizedMessage(), e);
                }
                try {
                    myLogger.log(Level.INFO, "Accessing Character_MAX");
                    Character tmp = (Character) ds.get(memberID + ":Character_MAX");
                    if (tmp != null) {
                        myLogger.log(Level.SEVERE, "ERROR: get of Character_MAX resulted in non-null return value" + tmp);
                    }
                } catch (Exception e) {
                    myLogger.log(Level.SEVERE, "ERROR: occurred while accessing removed [Character.MAX_VALUE] - " + e.getLocalizedMessage(), e);
                }
                try {
                    myLogger.log(Level.INFO, "Accessing BYTE_MIN");
                    Byte tmp = (Byte) ds.get(memberID + ":BYTE_MIN");
                    if (tmp != null) {
                        myLogger.log(Level.SEVERE, "ERROR: get of BYTE_MIN resulted in non-null return value" + tmp);
                    }
                } catch (Exception e) {
                    myLogger.log(Level.SEVERE, "ERROR: occurred while accessing removed [BYTE.MIN_VALUE] - " + e.getLocalizedMessage(), e);
                }
                try {
                    myLogger.log(Level.INFO, "Accessing BYTE_MAX");
                    Byte tmp = (Byte) ds.get(memberID + ":BYTE_MAX");
                    if (tmp != null) {
                        myLogger.log(Level.SEVERE, "ERROR: get of BYTE_MAX resulted in non-null return value" + tmp);
                    }
                } catch (Exception e) {
                    myLogger.log(Level.SEVERE, "ERROR: occurred while accessing removed [BYTE.MAX_VALUE] - " + e.getLocalizedMessage(), e);
                }
                try {
                    myLogger.log(Level.INFO, "Accessing SHORT_MIN");
                    Short tmp = (Short) ds.get(memberID + ":SHORT_MIN");
                    if (tmp != null) {
                        myLogger.log(Level.SEVERE, "ERROR: get of SHORT_MIN resulted in non-null return value" + tmp);
                    }
                } catch (Exception e) {
                    myLogger.log(Level.SEVERE, "ERROR: occurred while accessing removed [SHORT.MIN_VALUE] - " + e.getLocalizedMessage(), e);
                }
                try {
                    myLogger.log(Level.INFO, "Accessing SHORT_MAX");
                    Short tmp = (Short) ds.get(memberID + ":SHORT_MAX");
                    if (tmp != null) {
                        myLogger.log(Level.SEVERE, "ERROR: get of SHORT_MAX resulted in non-null return value" + tmp);
                    }
                } catch (Exception e) {
                    myLogger.log(Level.SEVERE, "ERROR: occurred while accessing removed [SHORT.MAX_VALUE] - " + e.getLocalizedMessage(), e);
                }
                try {
                    myLogger.log(Level.INFO, "Accessing INTEGER_MIN");
                    Integer tmp = (Integer) ds.get(memberID + ":INTEGER_MIN");
                    if (tmp != null) {
                        myLogger.log(Level.SEVERE, "ERROR: get of INTEGER_MIN resulted in non-null return value" + tmp);
                    }
                } catch (Exception e) {
                    myLogger.log(Level.SEVERE, "ERROR: occurred while accessing removed [INTEGER.MIN_VALUE] - " + e.getLocalizedMessage(), e);
                }
                try {
                    myLogger.log(Level.INFO, "Accessing INTEGER_MAX");
                    Integer tmp = (Integer) ds.get(memberID + ":INTEGER_MAX");
                    if (tmp != null) {
                        myLogger.log(Level.SEVERE, "ERROR: get of INTEGER_MAX resulted in non-null return value" + tmp);
                    }
                } catch (Exception e) {
                    myLogger.log(Level.SEVERE, "ERROR: occurred while accessing removed [INTEGER.MAX_VALUE] - " + e.getLocalizedMessage(), e);
                }
                try {
                    myLogger.log(Level.INFO, "Accessing LONG_MIN");
                    Long tmp = (Long) ds.get(memberID + ":LONG_MIN");
                    if (tmp != null) {
                        myLogger.log(Level.SEVERE, "ERROR: get of LONG_MIN resulted in non-null return value" + tmp);
                    }
                } catch (Exception e) {
                    myLogger.log(Level.SEVERE, "ERROR: occurred while accessing removed [LONG.MIN_VALUE] - " + e.getLocalizedMessage(), e);
                }
                try {
                    myLogger.log(Level.INFO, "Accessing LONG_MAX");
                    Long tmp = (Long) ds.get(memberID + ":LONG_MAX");
                    if (tmp != null) {
                        myLogger.log(Level.SEVERE, "ERROR: get of LONG_MAX resulted in non-null return value" + tmp);
                    }
                } catch (Exception e) {
                    myLogger.log(Level.SEVERE, "ERROR: occurred while accessing removed [LONG.MAX_VALUE] - " + e.getLocalizedMessage(), e);
                }
                try {
                    myLogger.log(Level.INFO, "Accessing FLOAT_MIN");
                    Float tmp = (Float) ds.get(memberID + ":FLOAT_MIN");
                    if (tmp != null) {
                        myLogger.log(Level.SEVERE, "ERROR: get of FLOAT_MIN resulted in non-null return value" + tmp);
                    }
                } catch (Exception e) {
                    myLogger.log(Level.SEVERE, "ERROR: occurred while accessing removed [FLOAT.MIN_VALUE] - " + e.getLocalizedMessage(), e);
                }
                try {
                    myLogger.log(Level.INFO, "Accessing FLOAT_MAX");
                    Float tmp = (Float) ds.get(memberID + ":FLOAT_MAX");
                    if (tmp != null) {
                        myLogger.log(Level.SEVERE, "ERROR: get of FLOAT_MAX resulted in non-null return value" + tmp);
                    }
                } catch (Exception e) {
                    myLogger.log(Level.SEVERE, "ERROR: occurred while accessing removed [FLOAT.MAX_VALUE] - " + e.getLocalizedMessage(), e);
                }
                try {
                    myLogger.log(Level.INFO, "Accessing DOUBLE_MIN");
                    Double tmp = (Double) ds.get(memberID + ":DOUBLE_MIN");
                    if (tmp != null) {
                        myLogger.log(Level.SEVERE, "ERROR: get of DOUBLE_MIN resulted in non-null return value" + tmp);
                    }
                } catch (Exception e) {
                    myLogger.log(Level.SEVERE, "ERROR: occurred while accessing removed [DOUBLE.MIN_VALUE] - " + e.getLocalizedMessage(), e);
                }
                try {
                    myLogger.log(Level.INFO, "Accessing DOUBLE_MAX");
                    Double tmp = (Double) ds.get(memberID + ":DOUBLE_MAX");
                    if (tmp != null) {
                        myLogger.log(Level.SEVERE, "ERROR: get of DOUBLE_MAX resulted in non-null return value" + tmp);
                    }
                } catch (Exception e) {
                    myLogger.log(Level.SEVERE, "ERROR: occurred while accessing removed [DOUBLE.MAX_VALUE] - " + e.getLocalizedMessage(), e);
                }
            } else {
                myLogger.log(Level.SEVERE, "ERROR: DataStore is null");
            }
        }

        private void PersistLoadSelfDataTest() {
            myLogger.log(Level.INFO, "Starting PersistLoadSelfDataTest");
            if (ds != null) {
                myLogger.log(Level.INFO, "Persisting Data");
                byte count = 0;
                for (int num = 0; num < numberOfObjects; num++) {
                    String id = memberID + ":" + num;
                    myLogger.log(Level.INFO, "Persisting Object:" + id);
                    // create the message to be sent
                    persistedData[num] = createPayload(payloadSize, count);
                    count++;
                    if (count > 9) {
                        count = 0;
                    }
                    try {
                        ds.put(id, persistedData[num]);
                    } catch (Exception e) {
                        myLogger.log(Level.SEVERE, "ERROR: occurred while saving [" + id + "] - " + e.getLocalizedMessage(), e);
                    }
                }
                // give time for the data to be persisted
//                PutGetRemoveTest.sleep(4);
                myLogger.log(Level.INFO, "Accessing persisted data");
                for (int num = 0; num < numberOfObjects; num++) {
                    String id = memberID + ":" + num;
                    myLogger.info("Accessing persisted Object:" + id);
                    try {
                        Object o = ds.get(id);
                        if (o != null) {
                            byte[] restoredPayload = (byte[]) o;
                            if (restoredPayload != null) {
                                int persistedDataLength = persistedData[num].length;
                                if (persistedDataLength != restoredPayload.length) {
                                    myLogger.severe("Length of restored payload[" + restoredPayload.length + "] does not equal payload[" + persistedDataLength + "]");
                                }
                                for (int i = 0; i < persistedData[num].length; i++) {
                                    if (persistedData[num][i] != restoredPayload[i]) {
                                        System.out.println("testing[" + id + "," + i + "]");
                                        myLogger.severe("Data for ID[" + id + "starts failing at position " + i);
                                        myLogger.severe("expected=" + new String(persistedData[num]));
                                        myLogger.severe("actual=" + new String(restoredPayload));
                                    }
                                }
                            } else {
                                myLogger.log(Level.SEVERE, "ERROR: received null value for id  [" + id + "]");
                            }
                        } else {
                            myLogger.log(Level.SEVERE, "ERROR: get of id resulted in a null return value");
                        }
                    } catch (Exception e) {
                        myLogger.log(Level.SEVERE, "ERROR: occurred while accessing [" + id + "] - " + e.getLocalizedMessage(), e);
                    }
                }
            } else {
                myLogger.log(Level.SEVERE, "ERROR: DataStore is null");
            }
        }

        private void LoadOthersDataTest() {
            myLogger.log(Level.INFO, "Starting LoadOthersDataTest");
            if (ds != null) {
                myLogger.log(Level.INFO, "Accessing persisted data");
                List<String> members = gms.getGroupHandle().getCurrentCoreMembers();
                members.remove(memberID);
                for (String member : members) {
                    for (int num = 0; num < numberOfObjects; num++) {
                        String id = member + ":" + num;
                        myLogger.info("Accessing persisted Object:" + id);
                        try {
                            Object o = ds.get(id);
                            if (o != null) {
                                byte[] restoredPayload = (byte[]) o;
                                if (restoredPayload.length > 0) {
                                    int persistedDataLength = persistedData[num].length;
                                    if (persistedDataLength != restoredPayload.length) {
                                        myLogger.severe("Length of restored payload[" + restoredPayload.length + "] does not equal payload[" + persistedDataLength + "]");
                                    }
                                    boolean ok = true;
                                    for (int i = 0; i < persistedData[num].length; i++) {
                                        if (persistedData[num][i] != restoredPayload[i]) {
                                            ok = false;
                                            break;
                                        }
                                        if (!ok) {
                                            int start = 0;
                                            if ((i - 10) > 0) {
                                                start = i - 10;
                                            }
                                            int end = persistedData[num].length;
                                            if ((i + 20) < end) {
                                                end = i + 20;
                                            }
                                            System.out.println("testing[" + id + "," + i + "]");
                                            myLogger.severe("Data for ID[" + id + "starts failing at position " + i);
                                            myLogger.severe("expected[-10...+20]=" + (new String(persistedData[num])).substring(start, end));
                                            myLogger.severe("actual[-10...+20]=" + (new String(restoredPayload)).substring(start, end));
                                        }
                                    }
                                } else {
                                    myLogger.log(Level.SEVERE, "ERROR: received an empty value for id  [" + id + "]");
                                }
                            } else {
                                myLogger.log(Level.SEVERE, "ERROR: get of id [" + id + "] resulted in a null return value");
                            }
                        } catch (Exception e) {
                            myLogger.log(Level.SEVERE, "ERROR: occurred while accessing [" + id + "] - " + e.getLocalizedMessage(), e);
                        }
                    }
                }
            } else {
                myLogger.log(Level.SEVERE, "ERROR: DataStore is null");
            }
        }

        private void RemoveTest() {
            myLogger.log(Level.INFO, "Starting RemoveTest");
            if (ds != null) {
                myLogger.log(Level.INFO, "Removing persisted data for self");
                for (int num = 0; num < numberOfObjects; num++) {
                    String id = memberID + ":" + num;
                    myLogger.log(Level.INFO, "Removing Object:" + id);
                    try {
                        ds.remove(id);
                    } catch (Exception e) {
                        myLogger.log(Level.SEVERE, "ERROR: occurred while removing [" + id + "] - " + e.getLocalizedMessage(), e);
                    }
                }
                // give time for the data to be removed
//                PutGetRemoveTest.sleep(4);
                myLogger.log(Level.INFO, "Accessing persisted data");
                for (int num = 0; num < numberOfObjects; num++) {
                    String id = memberID + ":" + num;
                    myLogger.log(Level.INFO, "Verify removed data");
                    try {
                        Object o = ds.get(id);
                        if (o != null) {
                            myLogger.log(Level.SEVERE, "ERROR: get of id [" + id + "] resulted in a non-null return value");
                        }
                    } catch (Exception e) {
                        myLogger.log(Level.SEVERE, "ERROR: occurred while accessing [" + id + "] - " + e.getLocalizedMessage(), e);
                    }
                }
            } else {
                myLogger.log(Level.SEVERE, "ERROR: DataStore is null");
            }
        }
    }
}

