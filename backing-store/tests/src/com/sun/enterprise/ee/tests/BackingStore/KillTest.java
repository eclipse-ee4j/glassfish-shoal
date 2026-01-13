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

package com.sun.enterprise.ee.tests.BackingStore;

import org.glassfish.shoal.gms.api.core.CallBack;
import org.glassfish.shoal.gms.api.core.FailureNotificationSignal;
import org.glassfish.shoal.gms.api.core.FailureSuspectedSignal;
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
import org.glassfish.shoal.gms.client.FailureNotificationActionFactoryImpl;
import org.glassfish.shoal.gms.client.FailureSuspectedActionFactoryImpl;
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
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.ConsoleHandler;
import java.util.logging.ErrorManager;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.ha.store.api.BackingStoreConfiguration;
import org.glassfish.ha.store.api.BackingStore;
import org.glassfish.ha.store.api.BackingStoreFactory;
import org.glassfish.ha.store.spi.BackingStoreFactoryRegistry;
import org.glassfish.shoal.adapter.store.ReplicationBackingStoreFactory;

public class KillTest implements CallBack {

    private int lifeTime = 0;
    private final String OKTOPROCEED = "ok_to_proceed";
    private final String WAITINGTOPROCEED = "waiting_to_proceed";
    private final String WAITINGFORKILL = "waiting_for_kill";
    private static final String STORENAME = "KillTestBackingStore";
    static BackingStoreConfiguration<String, MyPojo> conf = new BackingStoreConfiguration<String, MyPojo>();
    static BackingStore<String, MyPojo> backingStore = null;
    static BackingStoreFactory factory = null;
    private GroupManagementService gms = null;
    private static final Logger gmsLogger = GMSLogDomain.getLogger(GMSLogDomain.GMS_LOGGER);
    private static final Level GMSDEFAULTLOGLEVEL = Level.WARNING;
    private static final Logger myLogger = java.util.logging.Logger.getLogger("PutGetRemoveTest");
    private static final Level TESTDEFAULTLOGLEVEL = Level.INFO;
    static String memberID = null;
    static int numberOfInstances = 0;
    static int payloadSize = 0;
    static int numberOfObjects = 0;
    static String instanceToKill = null;
    static AtomicInteger numberOfMembers = new AtomicInteger(0);
    static String groupName = null;
    static AtomicBoolean startupComplete = new AtomicBoolean(false);
    static AtomicBoolean testingStarted = new AtomicBoolean(false);
    static GMSAdminAgent gaa;
    private static boolean isAdmin = false;
    private static Thread testThread;
    private AtomicInteger numberOfWAITINGTOPROCEED = new AtomicInteger(0);
    private AtomicInteger numberOfTestingComplete = new AtomicInteger(0);
    private AtomicInteger numberOfWAITINGFORKILL = new AtomicInteger(0);

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
        instanceToKill = System.getProperty("INSTANCETOKILL");
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
        if (instanceToKill == null) {
            System.err.println("Missing instanceToKill");
            usage();
        }
        // this configures the formatting of the gms log output
        Utility.setLogger(gmsLogger);
        Utility.setupLogHandler();
        // this sets the grizzly log level
        GrizzlyUtil.getLogger().setLevel(Level.WARNING);
        // this configures the formatting of the myLogger output
        KillTest.setupLogHandler();
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
            myLogger.log(Level.INFO, ("instanceToKill=" + instanceToKill));
        }
        KillTest killTest = new KillTest();
        killTest.execute();
        System.out.println("================================================================");
        myLogger.log(Level.INFO, "Testing Complete");
    }

    public static void usage() {
        System.out.println(" For server:");
        System.out.println("    server groupName <-tl testloglevel> <-sl shoalgmsloglevel> -ts portnum -te portnum -ma address -mp pornum -itk instancename ");
        System.out.println(" For instances:");
        System.out.println("    instanceXXX groupName numberofobjects msgsize <-tl testloglevel> <-sl shoalgmsloglevel> -ts portnum -te portnum -ma address -mp pornum -itk instancename ");

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
            conf.setStoreName(STORENAME).setStoreType("replicated").setKeyClazz(String.class).setValueClazz(MyPojo.class);
            conf.setInstanceName(memberID).setClusterName(groupName);
            try {
                BackingStoreFactoryRegistry.register(MyPojo.class.getName(), ReplicationBackingStoreFactory.class, new Properties());
                // fix inner class name
                //String className = MyPojo.class.getName().replace('$','.');
                //factory = BackingStoreFactoryRegistry.getFactoryInstance(className);
                factory = BackingStoreFactoryRegistry.getFactoryInstance(MyPojo.class.getName());
            } catch (Exception e) {
                myLogger.log(Level.SEVERE, "Error occurred while creating BackingStoreFactory " + e.getLocalizedMessage(), e);
                System.exit(1);
            }
            try {
                backingStore = factory.createBackingStore(conf);
                if (backingStore == null) {
                    myLogger.log(Level.SEVERE, "Created BackingStore is null, STOPPING TEST");
                    System.exit(1);
                }
            } catch (Exception e) {
                myLogger.log(Level.SEVERE, "Error occurred while creating BackingStore " + e.getLocalizedMessage(), e);
                System.exit(1);
            }
        }
        gaa = new GMSAdminAgent(gms, groupName, memberID, lifeTime);
        if (!isAdmin) {
            testThread = new DoTest(backingStore, gms);
        }
    }

    private void startGMS() {
        myLogger.log(Level.INFO, "Registering for group event notifications");
        gms.addActionFactory(new JoinedAndReadyNotificationActionFactoryImpl(this));
        gms.addActionFactory(new JoinNotificationActionFactoryImpl(this));
        gms.addActionFactory(new PlannedShutdownActionFactoryImpl(this));
        gms.addActionFactory(new FailureNotificationActionFactoryImpl(this));
        gms.addActionFactory(new FailureSuspectedActionFactoryImpl(this));
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
        configProps.put(ServiceProviderConfigurationKeys.INCOMING_MESSAGE_QUEUE_SIZE.toString(),
                System.getProperty("INCOMING_MESSAGE_QUEUE_SIZE", "3000"));
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
        } else if (notification instanceof FailureNotificationSignal) {
            if (myLogger.isLoggable(TESTDEFAULTLOGLEVEL)) {
                myLogger.log(TESTDEFAULTLOGLEVEL, "Received FailureNotification from member " + from);
            }
            // reset the number of members
            numberOfMembers.set(gms.getGroupHandle().getCurrentCoreMembers().size());
            if (isAdmin) {
                // instance has been kill and detected, send OKTOPROCEED to the remaining members
                try {
                    myLogger.info("Server broadcasting " + OKTOPROCEED + " to the cluster");
                    gms.getGroupHandle().sendMessage(GMSAdminConstants.TESTEXECUTOR, OKTOPROCEED.getBytes());
                } catch (GMSException ge1) {
                    myLogger.log(Level.SEVERE, "Exception occurred while broadcasting message: " + OKTOPROCEED + ge1, ge1);
                }
            }
        } else if (notification instanceof MessageSignal) {
            MessageSignal messageSignal = (MessageSignal) notification;
            String msgString = new String(messageSignal.getMessage());
            myLogger.info("Message received from [" + from + "] was:" + msgString);

            if (msgString.equals(GMSAdminConstants.STARTTESTING)) {
                numberOfMembers.set(gms.getGroupHandle().getCurrentCoreMembers().size());
                if (!isAdmin) {
                    if (!testingStarted.get()) {
                        testingStarted.set(true);
                        myLogger.info("Executing testing");
                        testThread.start();
                    }
                }
            } else if (msgString.equals(WAITINGFORKILL)) {
                if (isAdmin) {
                    numberOfWAITINGFORKILL.getAndIncrement();
                    myLogger.info("numberOfWAITINGFORKILL=" + numberOfWAITINGFORKILL.get());
                    myLogger.info("numberOfMembers=" + numberOfMembers.get());
                    if (numberOfWAITINGFORKILL.get() == numberOfMembers.get()) {

                        // if everyone has reported WAITINGFORKILL then send kill message to instance
                        numberOfWAITINGFORKILL.set(0);
                        try {
                            myLogger.info("Broadcasting " + GMSAdminConstants.KILLINSTANCE + " to specific instance");
                            gms.getGroupHandle().sendMessage(instanceToKill, GMSAdminConstants.ADMINAGENT, GMSAdminConstants.KILLINSTANCE.getBytes());
                        } catch (GMSException ge1) {
                            myLogger.log(Level.SEVERE, "Exception occurred while broadcasting message: " + OKTOPROCEED + ge1, ge1);
                        }
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
        private BackingStore<String, MyPojo> bs = null;
        String onceExisted = "_ONCEEXISTED";
        String onceExistedId = memberID + onceExisted;
        String onceExistedValue = memberID + "_onceexisted";

        public DoTest(BackingStore<String, MyPojo> bs, GroupManagementService gms) {
            this.bs = bs;
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
            PersistInstanceData();
            SendNotificationToKillAndWait();
            LoadKilledInstancesDataTest();
            SendTestingComplete();
        }

        private void SendNotificationToKillAndWait() {
            try {
                myLogger.log(Level.INFO, "Sending " + WAITINGFORKILL + " msg to " + GMSAdminConstants.ADMINNAME);
                gms.getGroupHandle().sendMessage(GMSAdminConstants.ADMINNAME, GMSAdminConstants.TESTCOORDINATOR, WAITINGFORKILL.getBytes());
            } catch (GMSException ge1) {
                myLogger.log(Level.SEVERE, "Exception occurred while broadcasting message: " + WAITINGFORKILL + ge1, ge1);
            }
            synchronized (numberOfWAITINGTOPROCEED) {
                try {
                    numberOfWAITINGTOPROCEED.wait(0);
                    myLogger.log(Level.INFO, "Resuming Testing");
                } catch (InterruptedException ie) {
                }
            }
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

        private void PersistInstanceData() {
            myLogger.log(Level.INFO, "Starting PersistInstanceDataTest");
            if (bs != null) {
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
                    MyPojo myPojo = new MyPojo();
                    myPojo.setData(persistedData[num]);
                    try {
                        bs.save(id, myPojo, true);
                    } catch (Exception e) {
                        myLogger.log(Level.SEVERE, "Error occurred while saving object [" + id + "] - " + e.getLocalizedMessage(), e);
                    }
                }
            } else {
                myLogger.log(Level.SEVERE, "Error BackingStore is null");
            }
        }

        private void LoadKilledInstancesDataTest() {
            myLogger.log(Level.INFO, "Starting LoadKilledInstancesDataTest");
            if (bs != null) {
                myLogger.log(Level.INFO, "Accessing persisted data");
                for (int num = 0; num < numberOfObjects; num++) {
                    String id = instanceToKill + ":" + num;
                    myLogger.info("Accessing persisted Object:" + id);
                    try {
                        MyPojo mpj = bs.load(id, null);
                        if (mpj != null) {
                            byte[] restoredPayload = mpj.getData();
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
                                myLogger.log(Level.SEVERE, "Error received an empty value for id  [" + id + "]");
                            }
                        } else {
                            myLogger.log(Level.SEVERE, "Error: load of id [" + id + "] resulted in a null return value");
                        }
                    } catch (Exception e) {
                        myLogger.log(Level.SEVERE, "Error occurred while accessing [" + id + "] - " + e.getLocalizedMessage(), e);
                    }
                }
            } else {
                myLogger.log(Level.SEVERE, "Error BackingStore is null");
            }
        }
    }

    static public class MyPojo implements Serializable {

        byte[] data;

        public void setData(byte[] data) {
            this.data = data;
        }

        public byte[] getData() {
            return this.data;
        }
    }
}

