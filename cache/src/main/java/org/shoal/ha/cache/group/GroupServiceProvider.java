/*
 * Copyright (c) 1997, 2018 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022 Contributors to the Eclipse Foundation. All rights reserved.
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

package org.shoal.ha.cache.group;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.shoal.gms.api.core.AliveAndReadyView;
import org.glassfish.shoal.gms.api.core.CallBack;
import org.glassfish.shoal.gms.api.core.FailureNotificationSignal;
import org.glassfish.shoal.gms.api.core.GMSException;
import org.glassfish.shoal.gms.api.core.GMSFactory;
import org.glassfish.shoal.gms.api.core.GroupHandle;
import org.glassfish.shoal.gms.api.core.GroupManagementService;
import org.glassfish.shoal.gms.api.core.JoinedAndReadyNotificationSignal;
import org.glassfish.shoal.gms.api.core.MemberNotInViewException;
import org.glassfish.shoal.gms.api.core.PlannedShutdownSignal;
import org.glassfish.shoal.gms.api.core.ServiceProviderConfigurationKeys;
import org.glassfish.shoal.gms.api.core.Signal;
import org.glassfish.shoal.gms.client.FailureNotificationActionFactoryImpl;
import org.glassfish.shoal.gms.client.JoinNotificationActionFactoryImpl;
import org.glassfish.shoal.gms.client.JoinedAndReadyNotificationActionFactoryImpl;
import org.glassfish.shoal.gms.client.MessageActionFactoryImpl;
import org.glassfish.shoal.gms.client.PlannedShutdownActionFactoryImpl;
import org.glassfish.shoal.gms.logging.GMSLogDomain;
import org.shoal.ha.cache.util.MessageReceiver;

/**
 * @author Mahesh Kannan
 */
public class GroupServiceProvider implements GroupService, CallBack {

    private static final Logger logger = GMSLogDomain.getLogger(GMSLogDomain.GMS_LOGGER);

    private String myName;

    private String groupName;

    private Properties configProps = new Properties();

    private GroupManagementService gms;

    private GroupHandle groupHandle;

    private List<GroupMemberEventListener> listeners = new ArrayList<GroupMemberEventListener>();

    private boolean createdAndJoinedGMSGroup;

    private AtomicLong previousViewId = new AtomicLong(-100);

    private volatile AliveAndReadyView arView;

    private ConcurrentHashMap<String, Long> lastSendMsgFailNotification = new ConcurrentHashMap<String, Long>();

    public GroupServiceProvider(String myName, String groupName, boolean startGMS) {
        init(myName, groupName, startGMS);
    }

    public void processNotification(Signal notification) {
        boolean isJoin = true;
        if ((notification instanceof JoinedAndReadyNotificationSignal) || (notification instanceof FailureNotificationSignal)
                || (notification instanceof PlannedShutdownSignal)) {

            isJoin = notification instanceof JoinedAndReadyNotificationSignal;

            checkAndNotifyAboutCurrentAndPreviousMembers(notification.getMemberToken(), isJoin, true);
        }
    }

    private synchronized void checkAndNotifyAboutCurrentAndPreviousMembers(String memberName, boolean isJoinEvent, boolean triggeredByGMS) {

        SortedSet<String> currentAliveAndReadyMembers = gms.getGroupHandle().getCurrentAliveAndReadyCoreView().getMembers();
        AliveAndReadyView aView = gms.getGroupHandle().getPreviousAliveAndReadyCoreView();
        SortedSet<String> previousAliveAndReadyMembers = new TreeSet<String>();

        if (aView == null) { // Possible during unit tests when listeners are registered before GMS is started
            return;
        }

        long arViewId = aView.getViewId();
        long knownId = previousViewId.get();
        Signal sig = aView.getSignal();

//        System.out.println("**GroupServiceProvider:checkAndNotifyAboutCurrentAndPreviousMembers: previous viewID: " + knownId
//                + "; current viewID: " + arViewId + "; " + aView.getSignal());
        if (knownId < arViewId) {
            if (previousViewId.compareAndSet(knownId, arViewId)) {
                this.arView = aView;
                sig = this.arView.getSignal();
                previousAliveAndReadyMembers = this.arView.getMembers();
            } else {
                previousAliveAndReadyMembers = this.arView.getMembers();
//                System.out.println("**GroupServiceProvider:checkAndNotifyAboutCurrentAndPreviousMembers.  Entered ELSE 1");
            }
        } else {
            previousAliveAndReadyMembers = this.arView.getMembers();
//            System.out.println("**GroupServiceProvider:checkAndNotifyAboutCurrentAndPreviousMembers.  Entered ELSE 2");
        }

        // Listeners must be notified even if view has not changed.
        // This is because this method is called when a listener
        // is registered
        for (GroupMemberEventListener listener : listeners) {
            listener.onViewChange(memberName, currentAliveAndReadyMembers, previousAliveAndReadyMembers, isJoinEvent);
        }

        if (triggeredByGMS) {
            StringBuilder sb = new StringBuilder("**VIEW: ");
            sb.append("prevViewId: " + knownId).append("; curViewID: ").append(arViewId).append("; signal: ").append(sig).append(" ");
            sb.append("[current: ");
            String delim = "";
            for (String member : currentAliveAndReadyMembers) {
                sb.append(delim).append(member);
                delim = ", ";
            }
            sb.append("]  [previous: ");
            delim = "";

            for (String member : previousAliveAndReadyMembers) {
                sb.append(delim).append(member);
                delim = ", ";
            }
            sb.append("]");
            logger.log(Level.INFO, sb.toString());
            logger.log(Level.INFO, "**********************************************************************");
        }

    }

    private void init(String myName, String groupName, boolean startGMS) {
        try {
            gms = GMSFactory.getGMSModule(groupName);
        } catch (Exception e) {
            logger.severe("GMS module for group " + groupName + " not enabled");
        }

        if (gms == null) {
            if (startGMS) {
                logger.info("GroupServiceProvider *CREATING* gms module for group " + groupName);
                GroupManagementService.MemberType memberType = myName.startsWith("monitor-") ? GroupManagementService.MemberType.SPECTATOR
                        : GroupManagementService.MemberType.CORE;

                configProps.put(ServiceProviderConfigurationKeys.MULTICASTADDRESS.toString(), System.getProperty("MULTICASTADDRESS", "229.9.1.1"));
                configProps.put(ServiceProviderConfigurationKeys.MULTICASTPORT.toString(), 2299);
                logger.info("Is initial host=" + System.getProperty("IS_INITIAL_HOST"));
                configProps.put(ServiceProviderConfigurationKeys.IS_BOOTSTRAPPING_NODE.toString(), System.getProperty("IS_INITIAL_HOST", "false"));
                if (System.getProperty("INITIAL_HOST_LIST") != null) {
                    configProps.put(ServiceProviderConfigurationKeys.VIRTUAL_MULTICAST_URI_LIST.toString(), myName.equals("DAS"));
                }
                configProps.put(ServiceProviderConfigurationKeys.FAILURE_DETECTION_RETRIES.toString(), System.getProperty("MAX_MISSED_HEARTBEATS", "3"));
                configProps.put(ServiceProviderConfigurationKeys.FAILURE_DETECTION_TIMEOUT.toString(), System.getProperty("HEARTBEAT_FREQUENCY", "2000"));
                // added for junit testing of send and receive to self.
                // these settings are not used in glassfish config of gms anyways.
                configProps.put(ServiceProviderConfigurationKeys.LOOPBACK.toString(), "true");
                final String bindInterfaceAddress = System.getProperty("BIND_INTERFACE_ADDRESS");
                if (bindInterfaceAddress != null) {
                    configProps.put(ServiceProviderConfigurationKeys.BIND_INTERFACE_ADDRESS.toString(), bindInterfaceAddress);
                }

                gms = (GroupManagementService) GMSFactory.startGMSModule(myName, groupName, memberType, configProps);

                createdAndJoinedGMSGroup = true;
            } else {
                logger.fine(
                        "**GroupServiceProvider:: Will not start GMS module for group " + groupName + ". It should have been started by now. But GMS: " + gms);
            }
        } else {
            logger.fine("**GroupServiceProvider:: GMS module for group " + groupName + " should have been started by now GMS: " + gms);
        }

        if (gms != null) {
            this.groupHandle = gms.getGroupHandle();
            this.myName = myName;
            this.groupName = groupName;

            gms.addActionFactory(new JoinNotificationActionFactoryImpl(this));
            gms.addActionFactory(new JoinedAndReadyNotificationActionFactoryImpl(this));
            gms.addActionFactory(new FailureNotificationActionFactoryImpl(this));
            gms.addActionFactory(new PlannedShutdownActionFactoryImpl(this));

            logger.info("**GroupServiceProvider:: REGISTERED member event listeners for <group, instance> => <" + groupName + ", " + myName + ">");

        } else {
            throw new IllegalStateException("GMS has not been started yet for group name: " + groupName + ". Is the cluster up and running");
        }

        if (createdAndJoinedGMSGroup) {
            try {
                gms.join();
                Thread.sleep(3000);
                gms.reportJoinedAndReadyState();
            } catch (Exception ex) {
                logger.log(Level.WARNING, "Got an exception during reportJoinedAndReadyState?", ex);
            }
        }
    }

    public List<String> getCurrentCoreMembers() {
        return groupHandle.getCurrentCoreMembers();
    }

    public void shutdown() {
        // gms.shutdown();
    }

    @Override
    public String getGroupName() {
        return groupName;
    }

    @Override
    public String getMemberName() {
        return myName;
    }

    @Override
    public boolean sendMessage(String targetMemberName, String token, byte[] data) {
        try {
            groupHandle.sendMessage(targetMemberName, token, data);
            return true;
        } catch (MemberNotInViewException memEx) {
            final String msg = "Error during groupHandle.sendMessage(" + targetMemberName + "," + token + ") failed because " + targetMemberName
                    + " is not alive?";
            logSendMsgFailure(memEx, targetMemberName, msg);
        } catch (GMSException gmsEx) {
            try {
                groupHandle.sendMessage(targetMemberName, token, data);
                return true;
            } catch (GMSException gmsEx2) {
                final String msg = "Error during groupHandle.sendMessage(" + targetMemberName + ", " + token + "; size=" + (data == null ? -1 : data.length)
                        + ")";
                logSendMsgFailure(gmsEx2, targetMemberName, msg);
            }
        }

        return false;
    }

    // ensure that log is not spammed with these messages.
    // package private so can call from junit test
    void logSendMsgFailure(GMSException t, String targetMemberName, String message) {
        final long SEND_FAILED_NOTIFICATION_PERIOD = 1000 * 60 * 60 * 12; // within a 12 hour period,only notify once.

        if (targetMemberName == null) {
            targetMemberName = "";
        }

        final Long lastNotify = lastSendMsgFailNotification.get(targetMemberName);
        final long currentTime = System.currentTimeMillis();
        if (lastNotify == null || currentTime > lastNotify + SEND_FAILED_NOTIFICATION_PERIOD) {
            lastSendMsgFailNotification.put(targetMemberName, new Long(currentTime));
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.WARNING, message, t);
            } else {
                Throwable causeT = t.getCause();
                String cause = causeT == null ? t.getMessage() : causeT.getMessage();
                logger.log(Level.WARNING, message + " Cause:" + cause);
            }
        }
    }

    @Override
    public void registerGroupMessageReceiver(String messageToken, MessageReceiver receiver) {
        logger.fine("[GroupServiceProvider]:  REGISTERED A MESSAGE LISTENER: " + receiver + "; for token: " + messageToken);
        gms.addActionFactory(new MessageActionFactoryImpl(receiver), messageToken);
    }

    @Override
    public void registerGroupMemberEventListener(GroupMemberEventListener listener) {
        listeners.add(listener);
        checkAndNotifyAboutCurrentAndPreviousMembers(myName, true, false);
    }

    @Override
    public void removeGroupMemberEventListener(GroupMemberEventListener listener) {
        listeners.remove(listener);
    }

    @Override
    public void close() {
        if (createdAndJoinedGMSGroup) {
            shutdown();
        }

    }
}
