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

package com.sun.enterprise.ee.cms.core;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Provides API for joining, and leaving the group and to register Action Factories of specific types for specific Group
 * Event Signals.
 *
 * @author Shreedhar Ganapathy Date: June 10, 2006
 * @version $Revision$
 */
public interface GroupManagementService {
	/**
	 * Members joining the group should be one of the following types. Core members are ones whose failure is a material
	 * event to the group, Spectators are those whose failure is not a material event to other group members. A Watchdog
	 * member of the group can report to the group that a member of the group has failed. The failure of a Watchdog is not a
	 * material event to the other group members. In order to lower overhead for a WATCHDOG, Distributed State Cache
	 * management is disabled for a WATCHDOG member. Additionally, a WATCHDOG member does not receive any GMS event
	 * notifications nor can it ever be the MASTER of a GMS group.
	 * <p/>
	 * <p/>
	 * Motivation for WATCHDOG member is to enable Framework Agents that control and monitor the runtime status of GMS
	 * members to be able to report failures to GMS when they are detected. If a Framework Agent runs on same machine as
	 * processes it monitors, it can detect failure sooner and more reliably than heartbeat based failure detection can. A
	 * WATCHDOG member can lessen the amount of time that GMS takes to notify a group that a member has failed.
	 */
	public static enum MemberType {
		CORE, SPECTATOR, WATCHDOG
	}

	/**
	 * These are possible recovery states used by GMS's recovery selection and failure fencing functions
	 */
	public static enum RECOVERY_STATE {
		RECOVERY_SERVER_APPOINTED, RECOVERY_IN_PROGRESS
	}

	/**
	 * Registers a FailureNotificationActionFactory instance.
	 *
	 * @param failureNotificationActionFactory Implementation of this interface produces a FailureNotificationAction
	 * instance which consumes the failure notification Signal
	 */
	void addActionFactory(FailureNotificationActionFactory failureNotificationActionFactory);

	/**
	 * Registers a FailureRecoveryActionFactory instance.
	 *
	 * @param componentName The name of the parent application's component that should be notified of being selected for
	 * performing recovery operations. One or more components in the parent application may want to be notified of such
	 * selection for their respective recovery operations
	 * @param failureRecoveryActionFactory Implementation of this interface produces a FailureRecoveryAction instance which
	 * consumes the failure recovery selection notification Signal
	 */
	void addActionFactory(String componentName, FailureRecoveryActionFactory failureRecoveryActionFactory);

	/**
	 * Registers a JoinNotificationActionFactory instance.
	 *
	 * @param joinNotificationActionFactory Implementation of this interface produces a JoinNotificationAction instance
	 * which consumes the member join notification signal.
	 */
	void addActionFactory(JoinNotificationActionFactory joinNotificationActionFactory);

	/**
	 * Registers a JoinedAndReadyNotificationActionFactory instance.
	 *
	 * @param joinedAndReadyNotificationActionFactory Implementation of this interface produces a
	 * JoinedAndReadyNotificationAction instance which consumes the member joined and ready notification signal.
	 */

	void addActionFactory(JoinedAndReadyNotificationActionFactory joinedAndReadyNotificationActionFactory);

	/**
	 * Registers a PlannedShutdownActionFactory instance.
	 *
	 * @param plannedShutdownActionFactory Implementation of this interface produces a PlannedShutdownAction instance which
	 * consumes the planned shutdown notification Signal
	 */
	void addActionFactory(PlannedShutdownActionFactory plannedShutdownActionFactory);

	/**
	 * Registers a MessageActionFactory instance for the specified component name.
	 *
	 * @param messageActionFactory Implementation of this interface produces a MessageAction instance that consumes a
	 * MessageSignal.
	 * @param componentName Name of the component that would like to consume Messages. One or more components in the parent
	 * application would want to be notified when messages arrive addressed to them. This registration allows GMS to deliver
	 * messages to specific components.
	 */
	void addActionFactory(MessageActionFactory messageActionFactory, String componentName);

	/**
	 * Registers a FailureSuspectedActionFactory Instance.
	 *
	 * @param failureSuspectedActionFactory Implementation of this interface produces a Failure Suspected Action instance
	 * that would consume the FailureSuspectedSignal
	 */
	void addActionFactory(FailureSuspectedActionFactory failureSuspectedActionFactory);

	/**
	 * Registers a GroupLeadershipNotificationActionFactory instance.
	 *
	 * @param groupLeadershipNotificationActionFactory Implementation of this interface produces a
	 * GroupLeadershipNotificationAction instance which consumes the GroupLeadershipNotificationSignal
	 */
	void addActionFactory(GroupLeadershipNotificationActionFactory groupLeadershipNotificationActionFactory);

	/**
	 * Removes a FailureNotificationActionFactory instance
	 *
	 * @param failureNotificationActionFactory the factory to remove
	 */
	void removeActionFactory(FailureNotificationActionFactory failureNotificationActionFactory);

	/**
	 * Removes a FailureRecoveryActionFactory instance
	 *
	 * @param componentName the component name to remove
	 */
	void removeFailureRecoveryActionFactory(String componentName);

	/**
	 * Removes a FailureSuspectedActionFactory instance
	 *
	 * @param failureSuspectedActionFactory the factory to remove
	 */
	void removeFailureSuspectedActionFactory(FailureSuspectedActionFactory failureSuspectedActionFactory);

	/**
	 * Removes a JoinNotificationActionFactory instance
	 *
	 * @param joinNotificationActionFactory the factory to remove
	 */
	void removeActionFactory(JoinNotificationActionFactory joinNotificationActionFactory);

	/**
	 * Removes a JoinedAndReadyNotificationActionFactory instance
	 *
	 * @param joinedAndReadyNotificationActionFactory the factory to remove
	 */
	void removeActionFactory(JoinedAndReadyNotificationActionFactory joinedAndReadyNotificationActionFactory);

	/**
	 * Removes a PlannedShutdownActionFactory instance
	 *
	 * @param plannedShutdownActionFactory the factory to remove
	 */
	void removeActionFactory(PlannedShutdownActionFactory plannedShutdownActionFactory);

	/**
	 * Removes a MessageActionFactory instance belonging to the specified component
	 *
	 * @param componentName the component name
	 */
	void removeMessageActionFactory(String componentName);

	/**
	 * Removes a GroupLeadershipNotificationActionFactory instance
	 *
	 * @param groupLeadershipNotificationActionFactory
	 */
	void removeActionFactory(GroupLeadershipNotificationActionFactory groupLeadershipNotificationActionFactory);

	/**
	 * Returns an implementation of GroupHandle
	 *
	 * @return com.sun.enterprise.ee.cms.core.GroupHandle
	 */
	GroupHandle getGroupHandle();

	/**
	 * Invokes the underlying group communication library's group creation and joining operations.
	 *
	 * @throws GMSException wraps any underlying exception that causes join to not occur
	 */
	void join() throws GMSException;

	/**
	 * Sends a shutdown command to the GMS indicating that the parent thread is about to be shutdown as part of a planned
	 * shutdown operation for the given shutdown type. The given shutdown type is specified by GMSConstants
	 *
	 * @param shutdownType the shutdown type
	 */
	void shutdown(GMSConstants.shutdownType shutdownType);

	/**
	 * Enables the client to update the Member Details shared datastructure The implementation of this api updates an
	 * existing datastructure that is keyed to MEMBER_DETAILS in the DistributedStateCache which stores other shared
	 * information. The dedicated Member Details datastructure allows for caching configuration type information about a
	 * member in the shared cache so that on occurence of join, failure or shutdown details related to the particular member
	 * would be readily available. There is nothing preventing other state information from being stored here but this is
	 * intended as a lightweight mechanism in terms of messaging overhead.
	 *
	 * @param memberToken - identifier token of this member
	 * @param key - Serializable object that uniquely identifies this cachable state
	 * @param value - Serializable object that is to be stored in the shared cache
	 * @throws GMSException if a group membership service error occurs
	 */
	void updateMemberDetails(String memberToken, Serializable key, Serializable value) throws GMSException;

	/**
	 * returns the details pertaining to the given member. returns a Map containing key-value pairs constituting data
	 * pertaining to the member's details
	 *
	 * @param memberToken the member
	 * @return Map <Serializable, Serializable>
	 */
	Map<Serializable, Serializable> getMemberDetails(String memberToken);

	/**
	 * returns the member details pertaining to the given key. This is particularly useful when the details pertain to all
	 * members and not just one member and such details are keyed by a common key. Through this route, details of all
	 * members could be obtained. returns a Map containing key-value pairs constituting data pertaining to the member's
	 * details for the given key.
	 *
	 * @param key the map key
	 * @return Map <Serializable, Serializable>
	 */
	Map<Serializable, Serializable> getAllMemberDetails(Serializable key);

	/**
	 * This method can be used by a controlling parent application that has a static preconfiguration of all members of the
	 * group to announce that the parent application is "initiating" and then that it has "completed" startup of all
	 * preconfigured members of this group.
	 * <p/>
	 * <P>
	 * Group members in parameter <code>members</code> is interpreted differently based on startupState. All preconfigured
	 * members of group are passed in <code>members</code> when <code>GMSConstants.groupStartupState.INITIATED</code> or
	 * <code>GMSConstants.groupStartupState.COMPLETED_SUCCESS</code>. When startupState is
	 * <code>GMSConstants.groupStartupState.COMPLETED_FAILED</code>, <code>members</code> is a list of the members that
	 * failed to start.
	 *
	 * @param groupName the group name
	 * @param startupState demarcate initiation of groupStartup and completion of group startup
	 * @param memberTokens list of memberTokens.
	 */
	void announceGroupStartup(String groupName, GMSConstants.groupStartupState startupState, List<String> memberTokens);

	/**
	 * This method can be used by parent application to notify all group members that the parent application is "initiating"
	 * or has "completed" shutdown of this group.
	 *
	 * @param groupName the group name
	 * @param shutdownState from GMSConstants.shutdownState - one of Initiated or Completed
	 */
	void announceGroupShutdown(String groupName, GMSConstants.shutdownState shutdownState);

	/**
	 * <p>
	 * This API is provided for the parent application to report to the group its joined and ready state to begin processing
	 * its operations. The group member that this parent application represents is now ready to process its operations at
	 * the time of this announcement to the group. GMS clients in all other group members that are interested in knowing
	 * when another member is ready to start processing operations, can subscribe to the event JoinedAndReadyEvent and be
	 * given this JoinedAndReadyNotificationSignal. Currently this API can only be used by cluster members which are of the
	 * type CORE and not the SPECTATOR members. Shoal makes the assumption that only the CORE members will act as servers
	 * for serving the client requests and not the SPECTATOR members.
	 * </p>
	 * <p/>
	 * <p>
	 * The behavioral semantics of this feature is that as each member reports its joined and ready state, the corresponding
	 * JoinedAndReadyNotificationSignal will be delivered to other cluster members who have already joined the group and
	 * have components who have registered for this event to be delivered. There may be members who may not have joined the
	 * group at the time a particular member reported its JoinedAndReady state. For these cases, those members have to rely
	 * on the <code>JoinNotificationSignal</code> of this particular member and call the <code>getMemberState()</code> api.
	 * The implementation currently does not require the joined and ready notification to be sent out to members joining the
	 * group after a member reported its joined and ready state.
	 * </p>
	 */
	void reportJoinedAndReadyState();

	/**
	 * <p>
	 * This API is provided for the parent application to report to the group its joined and ready state to begin processing
	 * its operations. The group member that this parent application represents is now ready to process its operations at
	 * the time of this announcement to the group. GMS clients in all other group members that are interested in knowing
	 * when another member is ready to start processing operations, can subscribe to the event JoinedAndReadyEvent and be
	 * given this JoinedAndReadyNotificationSignal. Currently this API can only be used by cluster members which are of the
	 * type CORE and not the SPECTATOR members. Shoal makes the assumption that only the CORE members will act as servers
	 * for serving the client requests and not the SPECTATOR members.
	 * </p>
	 * <p/>
	 * <p>
	 * The behavioral semantics of this feature is that as each member reports its joined and ready state, the corresponding
	 * JoinedAndReadyNotificationSignal will be delivered to other cluster members who have already joined the group and
	 * have components who have registered for this event to be delivered. There may be members who may not have joined the
	 * group at the time a particular member reported its JoinedAndReady state. For these cases, those members have to rely
	 * on the <code>JoinNotificationSignal</code> of this particular member and call the <code>getMemberState()</code> api.
	 * The implementation currently does not require the joined and ready notification to be sent out to members joining the
	 * group after a member reported its joined and ready state.
	 * </p>
	 *
	 * @param groupName name of the group
	 * @deprecated use method that takes no parameters.
	 */
	void reportJoinedAndReadyState(String groupName);

	/**
	 * <p>
	 * This API allows applications to query GMS to see if the group is shutting down. This helps with any pre-shutdown
	 * processing that may be required to be done on the application's side.
	 * </p>
	 *
	 * @return boolean true if it is being shutdown
	 */
	boolean isGroupBeingShutdown();

	/**
	 * <p>
	 * This API allows applications to query GMS to see if the group is shutting down. This helps with any pre-shutdown
	 * processing that may be required to be done on the application's side.
	 * </p>
	 *
	 * @param groupName The group name
	 * @return boolean true if it is being shutdown
	 * @deprecated use method with same name and no method parameters.
	 */
	boolean isGroupBeingShutdown(String groupName);

	/**
	 * GMS WATCHDOG member reports <code>serverToken</code> has been observed to have failed to this GMS group.
	 * <p/>
	 * This is merely a hint and the GMS system validates that the GMS member has truely failed using the same verification
	 * algorithm used when GMS heartbeat reports a member is INDOUBT and SUSPECTED of failure.
	 * <p/>
	 * Allows for enhanced GMS failure detection by external control entities (one example is NodeAgent of Glassfish
	 * Application Server.) Only a GMS MemberType of WATCHDOG is allowed to broadcast to all members of a group that this
	 * <code>serverToken</code> has likely failed.
	 *
	 * @param serverToken failed member
	 * @throws GMSException if called by a member that is not a WATCHDOG member or if serverToken is not currently running
	 * in group.
	 */
	public void announceWatchdogObservedFailure(String serverToken) throws GMSException;

	/**
	 * Returns the group name for this context
	 *
	 * @return the group name for this context
	 */
	public String getGroupName();

	/**
	 * Returns the MemberType for this context
	 *
	 * @return the MemberType for this context
	 */
	public GroupManagementService.MemberType getMemberType();

	/**
	 * Returns the Instance name for this context
	 *
	 * @return the Instance name for this context
	 */
	public String getInstanceName();

	public void initialize(final String serverToken, final String groupName, final GroupManagementService.MemberType membertype, final Properties properties);

}
