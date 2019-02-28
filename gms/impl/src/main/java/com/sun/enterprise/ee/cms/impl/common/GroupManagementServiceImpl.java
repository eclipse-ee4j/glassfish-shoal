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

package com.sun.enterprise.ee.cms.impl.common;
/**
 * Provides API for joining, and leaving the group and to register Action Factories of
 * specific types for specific Group Event Signals.
 * @author Shreedhar Ganapathy
 * Date: June 10, 2006
 * @version $Revision$
 */

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.enterprise.ee.cms.core.FailureNotificationActionFactory;
import com.sun.enterprise.ee.cms.core.FailureRecoveryActionFactory;
import com.sun.enterprise.ee.cms.core.FailureSuspectedActionFactory;
import com.sun.enterprise.ee.cms.core.GMSCacheable;
import com.sun.enterprise.ee.cms.core.GMSConstants;
import com.sun.enterprise.ee.cms.core.GMSException;
import com.sun.enterprise.ee.cms.core.GMSFactory;
import com.sun.enterprise.ee.cms.core.GroupHandle;
import com.sun.enterprise.ee.cms.core.GroupLeadershipNotificationActionFactory;
import com.sun.enterprise.ee.cms.core.GroupManagementService;
import com.sun.enterprise.ee.cms.core.JoinNotificationActionFactory;
import com.sun.enterprise.ee.cms.core.JoinedAndReadyNotificationActionFactory;
import com.sun.enterprise.ee.cms.core.MessageActionFactory;
import com.sun.enterprise.ee.cms.core.PlannedShutdownActionFactory;
import com.sun.enterprise.ee.cms.logging.GMSLogDomain;

public class GroupManagementServiceImpl implements GroupManagementService, Runnable {
    private GMSContext ctx;
    private Router router;
    private String memberName = "";

    private AtomicBoolean initialized = new AtomicBoolean(false);
    private AtomicBoolean hasLeftGroup = new AtomicBoolean(false);
    private AtomicBoolean hasJoinedGroup = new AtomicBoolean(false);

    // Logging related stuff
    private static final Logger logger = GMSLogDomain.getLogger(GMSLogDomain.GMS_LOGGER);
    private static final String MEMBER_DETAILS = "MEMBERDETAILS";

    /**
     * Creates a GMSContext instance with the given paramters. GMSContext calls the underlying Group Communication Provider
     * to initialize it with these parameters.
     *
     * @param serverToken identity token of this member process
     * @param groupName name of the group
     * @param membertype Type of member as specified in GroupManagementService.MemberType
     * @param properties Configuration Properties
     */
    public GroupManagementServiceImpl(final String serverToken, final String groupName, final GroupManagementService.MemberType membertype,
            final Properties properties) {
        initialize(serverToken, groupName, membertype, properties);
        memberName = serverToken;
    }

    public GroupManagementServiceImpl() {
    }

    public void initialize(final String serverToken, final String groupName, final GroupManagementService.MemberType membertype, final Properties properties) {
        if (initialized.compareAndSet(false, true)) {
            ctx = GMSContextFactory.produceGMSContext(serverToken, groupName, membertype, properties);
            router = ctx.getRouter();
            memberName = serverToken;
        }
    }

    public void run() {
        startup();
    }

    private void startup() {
        try {
            logger.log(Level.INFO, "gms.joinMessage");
            join();
        } catch (GMSException e) {
            logger.log(Level.FINE, "gms.joinException", e);
        }
    }

    /**
     * Registers a FailureNotificationActionFactory instance. To add MessageActionFactory instance, use the method
     * addActionFactory(MessageActionFactory maf, String componentName);
     *
     * @param failureNotificationActionFactory implementation of this interface
     */
    public void addActionFactory(final FailureNotificationActionFactory failureNotificationActionFactory) {
        router.addDestination(failureNotificationActionFactory);
    }

    /**
     * Registers a FailureRecoveryActionFactory instance. To add MessageActionFactory instance, use the method
     * addActionFactory(MessageActionFactory maf, String componentName);
     *
     * @param componentName name of component
     * @param failureRecoveryActionFactory implmentation of this interface
     */
    public void addActionFactory(final String componentName, final FailureRecoveryActionFactory failureRecoveryActionFactory) {
        router.addDestination(componentName, failureRecoveryActionFactory);
    }

    /**
     * Registers a JoinedAndReadyNotificationActionFactory instance.
     *
     * @param joinedAndReadyNotificationActionFactory Implementation of this interface produces a
     * JoinedAndReadyNotificationAction instance which consumes the member joined and ready notification signal.
     */

    public void addActionFactory(final JoinedAndReadyNotificationActionFactory joinedAndReadyNotificationActionFactory) {
        router.addDestination(joinedAndReadyNotificationActionFactory);
    }

    /**
     * Registers a JoinNotificationActionFactory instance.
     *
     * @param joinNotificationActionFactory implementation of this interface
     */
    public void addActionFactory(final JoinNotificationActionFactory joinNotificationActionFactory) {
        router.addDestination(joinNotificationActionFactory);
    }

    /**
     * Registers a PlannedShuttdownActionFactory instance. To add MessageActionFactory instance, use the method
     * addActionFactory(MessageActionFactory maf, String componentName);
     *
     * @param plannedShutdownActionFactory implementation of this interface
     */
    public void addActionFactory(final PlannedShutdownActionFactory plannedShutdownActionFactory) {
        router.addDestination(plannedShutdownActionFactory);
    }

    /**
     * Registers a MessageActionFactory instance for the specified component name.
     *
     * @param messageActionFactory implementation of this interface
     * @param componentName name of component to identify target component for message delivery
     */
    public void addActionFactory(final MessageActionFactory messageActionFactory, final String componentName) {
        router.addDestination(messageActionFactory, componentName);
    }

    public void addActionFactory(final FailureSuspectedActionFactory failureSuspectedActionFactory) {
        router.addDestination(failureSuspectedActionFactory);
    }

    public void addActionFactory(GroupLeadershipNotificationActionFactory groupLeadershipNotificationActionFactory) {
        router.addDestination(groupLeadershipNotificationActionFactory);
    }

    /**
     * Removes a FailureNotificationActionFactory instance To remove a MessageActionFactory for a specific component, use
     * the method: removeActionFactory(String componentName);
     *
     * @param failureNotificationActionFactory implementation of this interface
     */
    public void removeActionFactory(final FailureNotificationActionFactory failureNotificationActionFactory) {
        router.removeDestination(failureNotificationActionFactory);
    }

    /**
     * Removes a FailureRecoveryActionFactory instance To remove a MessageActionFactory for a specific component, use the
     * method: removeActionFactory(String componentName);
     *
     * @param componentName name of component
     */
    public void removeFailureRecoveryActionFactory(final String componentName) {
        router.removeFailureRecoveryAFDestination(componentName);
    }

    public void removeFailureSuspectedActionFactory(final FailureSuspectedActionFactory failureSuspectedActionFactory) {
        router.removeDestination(failureSuspectedActionFactory);
    }

    /**
     * Removes a JoinNotificationActionFactory instance To remove a MessageActionFactory for a specific component, use the
     * method: removeActionFactory(String componentName);
     *
     * @param joinNotificationActionFactory implementation of this interface
     */
    public void removeActionFactory(final JoinNotificationActionFactory joinNotificationActionFactory) {
        router.removeDestination(joinNotificationActionFactory);
    }

    /**
     * Removes a JoinedAndReadyNotificationActionFactory instance To remove a MessageActionFactory for a specific component,
     * use the method: removeActionFactory(String componentName);
     *
     * @param joinedAndReadyNotificationActionFactory implementation of this interface
     */
    public void removeActionFactory(final JoinedAndReadyNotificationActionFactory joinedAndReadyNotificationActionFactory) {
        router.removeDestination(joinedAndReadyNotificationActionFactory);
    }

    /**
     * Removes a PlannedShutdownActionFactory instance To remove a MessageActionFactory for a specific component, use the
     * method: removeActionFactory(String componentName);
     *
     * @param plannedShutdownActionFactory implementation of this interface
     */
    public void removeActionFactory(final PlannedShutdownActionFactory plannedShutdownActionFactory) {
        router.removeDestination(plannedShutdownActionFactory);
    }

    /**
     * Removes a MessageActionFactory instance belonging to the specified component
     *
     * @param componentName name of component
     */
    public void removeMessageActionFactory(final String componentName) {
        router.removeMessageAFDestination(componentName);
    }

    public void removeActionFactory(GroupLeadershipNotificationActionFactory groupLeadershipNotificationActionFactory) {
        router.removeDestination(groupLeadershipNotificationActionFactory);
    }

    /**
     * Returns an implementation of GroupHandle
     *
     * @return com.sun.enterprise.ee.cms.GroupHandle
     */
    public GroupHandle getGroupHandle() {
        return ctx.getGroupHandle();
    }

    /**
     * Sends a shutdown command to the GMS indicating that the parent thread is about to be shutdown as part of a planned
     * shutdown operation
     */
    public void shutdown(final GMSConstants.shutdownType shutdownType) {
        leave(shutdownType);
    }

    public void updateMemberDetails(final String memberToken, final Serializable key, final Serializable value) throws GMSException {
        if (isWatchdog()) {
            return;
        }
        ctx.getDistributedStateCache().addToCache(MEMBER_DETAILS, memberToken, key, value);

    }

    /**
     * returns the details pertaining to the given member. At times, details pertaining to all members may be stored in the
     * Cache but keyed by the given member token. Through this route, details of all members could be obtained. returns a
     * Map containing key-value pairs constituting data pertaining to the member's details
     *
     * @param memberToken identity token of the member process
     * @return Map &lt;Serializable, Serializable&gt;
     */

    public Map<Serializable, Serializable> getMemberDetails(final String memberToken) {
        if (isWatchdog()) {
            final Map<Serializable, Serializable> retval = new HashMap<Serializable, Serializable>();
            return retval;
        }
        return ctx.getDistributedStateCache().getFromCacheForPattern(MEMBER_DETAILS, memberToken);
    }

    public Map<Serializable, Serializable> getAllMemberDetails(final Serializable key) {

        final Map<Serializable, Serializable> retval = new HashMap<Serializable, Serializable>();
        if (isWatchdog()) {
            return retval;
        }
        final Map<GMSCacheable, Object> ret = ctx.getDistributedStateCache().getFromCache(key);

        for (Map.Entry<GMSCacheable, Object> entry : ret.entrySet()) {
            GMSCacheable c = entry.getKey();
            if (c.getComponentName().equals(MEMBER_DETAILS)) {
                retval.put(c.getMemberTokenId(), (Serializable) entry.getValue());
            }
        }
        return retval;
    }

    public String getGroupName() {
        if (isWatchdog()) {
            return "";
        }
        return ctx.getGroupName();
    }

    public GroupManagementService.MemberType getMemberType() {
        return ctx.getMemberType();
    }

    public String getInstanceName() {
        return ctx.getServerIdentityToken();
    }

    /**
     * for this serverToken, use the map to derive key value pairs that constitute data pertaining to this member's details
     *
     * @param serverToken - member token id for this member.
     * @param keyValuePairs - a Map containing key-value pairs
     * @throws com.sun.enterprise.ee.cms.core.GMSException wraps underlying exception that caused adding of member details
     * to fail.
     */
    public void setMemberDetails(final String serverToken, final Map<? extends Object, ? extends Object> keyValuePairs) throws GMSException {
        if (isWatchdog()) {
            return;
        }
        for (Map.Entry<? extends Object, ? extends Object> entry : keyValuePairs.entrySet()) {
            ctx.getDistributedStateCache().addToLocalCache(MEMBER_DETAILS, serverToken, (Serializable) entry.getKey(), (Serializable) entry.getValue());
        }
    }

    public void join() throws GMSException {
        // ensure that only join the group once.
        if (hasJoinedGroup.compareAndSet(false, true)) {
            logger.log(Level.INFO, "gms.join", new Object[] { memberName, ctx.getGroupName() });
            ctx.join();
            hasLeftGroup.set(false);
        }
    }

    /**
     * Called when the application layer is shutting down and this member needs to leave the group formally for a graceful
     * shutdown event.
     *
     * @param shutdownType shutdown type corresponds to the shutdown types specified in GMSConstants.shudownType enum.
     */
    private void leave(final GMSConstants.shutdownType shutdownType) {
        // ensure that only leave the group once.
        if (hasLeftGroup.compareAndSet(false, true)) {
            try {
                logger.log(Level.INFO, "gms.leave", new Object[] { memberName, ctx.getGroupName() });
                ctx.leave(shutdownType);
                removeAllActionFactories();
            } finally {
                hasJoinedGroup.set(false);
                GMSFactory.removeGMSModule(ctx.getGroupName());
                GMSContextFactory.removeGMSContext(ctx.getGroupName());
            }
        }
    }

    private void removeAllActionFactories() {
        router.undocketAllDestinations();
    }

    /**
     * This method is used to announce that the group is about to be shutdown.
     *
     * @param groupName name of group being shutdown.
     */
    public void announceGroupShutdown(final String groupName, final GMSConstants.shutdownState shutdownState) {

        final GMSContext gctx = GMSContextFactory.getGMSContext(groupName);
        logger.log(Level.INFO, "gms.group.shutdown", new Object[] { groupName, shutdownState });
        gctx.announceGroupShutdown(groupName, shutdownState);

    }

    public void announceGroupStartup(String groupName, GMSConstants.groupStartupState startupState, List<String> memberTokens) {
        final GMSContext gctx = GMSContextFactory.getGMSContext(groupName);
        final StringBuffer sb = new StringBuffer(120);
        if (memberTokens != null) {
            for (String memberToken : memberTokens) {
                sb.append(memberToken).append(",");
            }
        }
        logger.log(Level.INFO, "gms.group.startup", new Object[] { startupState.toString(), groupName, sb.toString() });
        gctx.announceGroupStartup(groupName, startupState, memberTokens);
    }

    /**
     * <p>
     * This API is provided for the parent application to report to the group its joined and ready state to begin processing
     * its operations. The group member that this parent application represents is now ready to process its operations at
     * the time of this announcement to the group. GMS clients in all other group members that are interested in knowing
     * when another member is ready to start processing operations, can subscribe to the event JoinedAndReadyEvent and be
     * notified of this JoinedAndReadyNotificationSignal.
     * </p>
     * <p>
     * This api should be called only after group join operation has completed.
     * </p>
     *
     * @param groupName name of the group
     * @deprecated use reportJoinedAndReadyState()
     */
    public void reportJoinedAndReadyState(String groupName) {
        final GMSContext gctx = GMSContextFactory.getGMSContext(groupName);
        if (gctx != null) {
            logger.log(Level.INFO, "gms.ready", new Object[] { groupName });
            gctx.getGroupCommunicationProvider().reportJoinedAndReadyState();
        } else {
            reportJoinedAndReadyState();
        }
    }

    /**
     * <p>
     * This API is provided for the parent application to report to the group its joined and ready state to begin processing
     * its operations. The group member that this parent application represents is now ready to process its operations at
     * the time of this announcement to the group. GMS clients in all other group members that are interested in knowing
     * when another member is ready to start processing operations, can subscribe to the event JoinedAndReadyEvent and be
     * notified of this JoinedAndReadyNotificationSignal.
     * </p>
     * <p>
     * This api should be called only after group join operation has completed.
     * </p>
     */
    public void reportJoinedAndReadyState() {
        if (ctx != null) {
            logger.log(Level.INFO, "gms.ready", new Object[] { getGroupName() });
            ctx.getGroupCommunicationProvider().reportJoinedAndReadyState();
        } else {
            throw new IllegalStateException("GMSContext for group name " + getGroupName() + " unexpectedly null");
        }
    }

    /**
     * <p>
     * This API allows applications to query GMS to see if the group is shutting down. This helps with any pre-shutdown
     * processing that may be required to be done on the application's side.
     * </p>
     * <p>
     * Also returns true when called after the gms context has left the group during a group shutdown.
     * </p>
     *
     * @param groupName the group name
     * @return boolean
     * @deprecated
     */
    public boolean isGroupBeingShutdown(String groupName) {
        return ctx.isGroupBeingShutdown(groupName);
    }

    public boolean isGroupBeingShutdown() {
        return ctx.isGroupBeingShutdown(this.getGroupName());
    }

    public void announceWatchdogObservedFailure(String serverToken) throws GMSException {
        if (!isWatchdog()) {
            throw new GMSException("illegal state: announceWatchdogObservedFailure operation is only valid for a WATCHDOG member.");

        }
        GroupHandle gh = ctx.getGroupHandle();
        ctx.getGroupCommunicationProvider().announceWatchdogObservedFailure(serverToken);
    }

    private boolean isWatchdog() {
        return ctx.getMemberType() == MemberType.WATCHDOG;
    }

    public int outstandingNotifications() {
        return ((com.sun.enterprise.ee.cms.impl.base.GMSContextImpl) ctx).outstandingNotifications();
    }
}
