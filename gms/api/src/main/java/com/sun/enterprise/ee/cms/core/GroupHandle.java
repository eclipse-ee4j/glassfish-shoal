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

import com.sun.enterprise.ee.cms.spi.MemberStates;

import java.util.List;

/**
 * Provides a handle to the interact with the membership group.
 * Using this interface, applications can send messages to the group, to
 * individual members or a list of members, and also to specific components
 * within the target member or group.
 * Provides a reference to the DistributedStateCache, and APIs for
 * FailureFencing.
 *
 * @author Shreedhar Ganapathy
 *         Date: Jan 12, 2004
 * @version $Revision$
 */
public interface GroupHandle {
    /**
     * Sends a message to all members of the Group.
     * Expects a target component name and a byte array as parameter
     * carrying the payload.
     * <p/>
     * Note: when utilizing multicast, message size should not exceed the underlying communication
     * provider message limits (64K).  When sending messages larger than the limit, consider sending
     * the message to each individual member
     *
     * @param targetComponentName target name
     * @param message             the message to send
     * @throws GMSException - any exception while sending message wrapped into GMSException
     */
    void sendMessage(String targetComponentName, byte[] message) throws GMSException;

    /**
     * Sends a message to a single member of the group
     * Expects a targetServerToken representing the recipient member's
     * id, the target component name in the target recipient member,
     * and a byte array as parameter carrying the payload.
     *
     * @param targetServerToken   targetServerToken representing the recipient member's id
     * @param targetComponentName target name
     * @param message             the message to send
     * @throws GMSException - any exception while sending message wrapped into GMSException
     */
    void sendMessage(String targetServerToken, String targetComponentName,
                     byte[] message) throws GMSException;

    /**
     * Sends a message to a list of members in the group. An empty list would
     * have the same effect as sending the message to the entire group
     *
     * @param targetServerTokens  List of target server tokens
     * @param targetComponentName a component in the target members to which message is addressed.
     * @param message             - the payload
     * @throws GMSException - any exception while sending message wrapped into GMSException
     */
    void sendMessage(List<String> targetServerTokens, String targetComponentName,
                     byte[] message) throws GMSException;

    /**
     * returns a DistributedStateCache object that provides the ability to
     * set and retrieve CachedStates.
     *
     * @return DistributedStateCache
     * @see DistributedStateCache
     */
    DistributedStateCache getDistributedStateCache();

    /**
     * returns a List of strings containing the current core members
     * in the group
     *
     * @return List
     */
    List<String> getCurrentCoreMembers();

    /**
     * * returns only the members that are in ALIVE or READY state
     *
     * @return List
     */
    List<String> getCurrentAliveOrReadyMembers();

    /**
     * API for giving the state of the member
     * Calls #getMemberState(String, long, long) with implementation default values for <code>threshold</code> and <code>timeout</code>.
     *
     * @param member the group member
     * @return the state of the <code>member</code>.
     */
    MemberStates getMemberState(String member);

    /**
     * Returns the state of the member.
     * The parameters <code>threshold</code> and <code>timeout</code> enable the caller to tune this
     * lookup between accuracy and time it will take to complete the call.  It is lowest cost to just return
     * the local computed concept for a member's state. <code>threshold</code> parameter controls this.
     * If the local state is stale, then the <code>timeout</code> parameter enables one to control how
     * long they are willing to wait for more accurate state information from the member itself.
     *
     * @param member    the group member
     * @param threshold allows caller to specify how up-to-date the member state information has to be.
     *                  The  larger this value, the better chance that this method just returns the local concept of this member's state.
     *                  The smaller this value, the better chance that the local state is not fresh enough and the method will find out directly
     *                  from the instance what its current state is.
     * @param timeout   is the time for which the caller instance should wait to get the state from the concerned member
     *                  via a network call.
     *                  if timeout and threshold are both 0, then the default values are used
     *                  if threshold is 0, then a network call is made to get the state of the member
     *                  if timeout is 0, then the caller instance checks for the state of the member stored with it within the
     *                  given threshold
     * @return the state of the member
     *         Returns UNKNOWN when the local state for the member is considered stale (determined by threshold value)
     *         and the network invocation to get the member state times out before getting a reply from the member of what its state is.
     */

    MemberStates getMemberState(String member, long threshold, long timeout);

    /**
     * returns a List of strings containing the current group membership including
     * spectator members.
     *
     * @return List
     */
    List<String> getAllCurrentMembers();

    /**
     * returns a List of strings containing the current core members
     * in the group. Each entry contains a "::" delimiter after the
     * member token id. After the delimited is a string representation of the
     * long value that stands for that member's startup timestamp.
     *
     * @return List
     */
    List<String> getCurrentCoreMembersWithStartTimes();

    /**
     * returns a List of strings containing the current group membership including
     * spectator members. Each entry contains a "::" delimiter after the
     * member token id. After the delimited is a string representation of the
     * long value that stands for that member's startup timestamp.
     *
     * @return List
     */
    List<String> getAllCurrentMembersWithStartTimes();

    //FAILURE FENCING RELATED API
    /**
     * Enables the caller to raise a logical fence on a specified target member
     * token's component.
     * <p>Failure Fencing is a group-wide protocol that, on one hand, requires
     * members to update a shared/distributed datastructure if any of their
     * components need to perform operations on another members' corresponding
     * component. On the other hand, the group-wide protocol requires members
     * to observe "Netiquette" during their startup so as to check if any of
     * their components are being operated upon by other group members.
     * Typically this check is performed by the respective components
     * themselves. See the isFenced() method below for this check.
     * When the operation is completed by the remote member component, it
     * removes the entry from the shared datastructure. See the lowerFence()
     * method below.
     * <p>Raising the fence, places an entry into a distributed datastructure
     * that is accessed by other members during their startup
     * <p/>
     * <p>Direct calls to this method is meant only for self-recovering clients.
     * For clients that perform recovery as a surrogate for a failed instance,
     * the FailureRecoverySignal's acquire() method should be called. That
     * method has the effect of raising the fence and performing any other state
     * management operation that may be added in future.
     *
     * @param componentName     the component channel name
     * @param failedMemberToken the failed member
     * @throws GMSException if an error occurs
     */
    void raiseFence(String componentName,
                    String failedMemberToken) throws GMSException;

    /**
     * Enables the caller to lower a logical fence that was earlier raised on
     * a target member component. This is typically done when the operation
     * being performed on the target member component has now completed.
     * <p/>
     * <p>Direct calls to this method is meant only for self-recovering clients.
     * For clients that perform recovery as a surrogate for a failed instance,
     * the FailureRecoverySignal's release() method should be called. That
     * method has the effect of lowering the fence and performing any other
     * state management operation that may be added in future.
     *
     * @param componentName     the component channel name
     * @param failedMemberToken the failed member
     * @throws GMSException if an error occurs
     */
    void lowerFence(String componentName,
                    String failedMemberToken) throws GMSException;

    /**
     * Provides the status of a member component's fence, if any.
     * <p>This check is <strong>mandatorily</strong> done at the time a member
     * component is in the process of starting(note that at this point we assume
     * that this member failed in its previous lifetime).
     * <p>The boolean value returned would indicate if this member component is
     * being recovered by any other member. The criteria  for returning a
     * boolean "true" is that this componentName-memberToken combo is present
     * as a value for any key in the GMS DistributedStateCache. If a
     * true is returned, for instance, this could mean that the client component
     * should continue its startup without attempting to perform its own
     * recovery operations.</p>
     * <p>The criteria for returning a boolean "false" is that the
     * componentId-memberTokenId combo is not present in the list of values in
     * the DistributedStateCache.If a boolean "false" is returned, this could
     * mean that the client component can continue with its lifecycle startup
     * per its normal startup policies.
     *
     * @param componentName the component channel name
     * @param memberToken   the member
     * @return boolean
     */
    boolean isFenced(String componentName, String memberToken);

    /**
     * Checks if a member is alive
     *
     * @param memberToken the member
     * @return boolean
     */
    boolean isMemberAlive(String memberToken);

    /**
     * Return the leader of the group
     *
     * @return String representing the member identity token of the group leader
     */
    String getGroupLeader();

    /**
     * This is a check to find out if this peer is a group leader.
     *
     * @return true if this peer is the group leader
     */
    boolean isGroupLeader();

    /**
     * GMS WATCHDOG member reports <code>serverToken</code> has been observed to have failed to this GMS group.
     * <p/>
     * This is merely a hint and the GMS system validates that the GMS member has truely failed using
     * the same verification algorithm used when GMS heartbeat reports a member is INDOUBT and SUSPECTED of
     * failure.
     * <p/>
     * Allows for enhanced GMS failure detection by external control entities (one example is NodeAgent of Glassfish Application Server.)
     * Only a GMS MemberType of WATCHDOG is allowed to broadcast to all members of a group that this <code>serverToken</code> has likely failed.
     *
     * @param serverToken failed member
     * @throws GMSException if called by a member that is not a WATCHDOG member or if serverToken is not currently running in group.
     */
    void announceWatchdogObservedFailure(String serverToken) throws GMSException;

    /**
     * Return a snapshot of members in current view.
     * <p>
     * Note: returns an empty list if no current view.
     * @return current members
     */
    List<GMSMember> getCurrentView();

    /**
     * Return snapshot of members in previous view.
     * <p>
     * Note: returns an empty list if no previous view.
     * @return members from previous view
     */
    List<GMSMember> getPreviousView();

    /**
     * This snapshot was terminated by AliveAndReadyView.getSignal(), a GMS notification of
     * either JoinedAndReadyNotificationSignal, FailureNotificationSignal or PlannedShutdownSignal.
     *
     * <p>
     * If the last GMS notification is a JOIN with a REJOIN subevent, the list previous CORE members will be same as list of current CORE members.
     * (this scenario reflects a fast restart of an instance in less than GMS heartbeat failure detection can detect failure, this is called a REJOIN
     * when an instance fails and restarts so quickly that FAILURE_NOTIFICATION is never sent. THe REJOIN subevent represents the unreported FAILURE
     * detected  at the time that the instance is restarting.
     *
     * <p>
     * Behavior is not well defined at during GROUP_STARTUP or GROUP_SHUTDOWN.
     *
     *
     * @return the previous AliveAndReady Core member snapshot
     */
    AliveAndReadyView getPreviousAliveAndReadyCoreView();

    /**
     * Get a current snapshot of the AliveAndReady Core members.
     * AliveAndReadyView.getSignal() returns null to signify that
     * the view is still the current view and no GMS notification signal
     * has terminated this current view yet.
     *
     * @return current view of AliveAndReady Core members
     */
    AliveAndReadyView getCurrentAliveAndReadyCoreView();

    void removeRecoveryAppointments(String failedMemberToken, String componentName) throws GMSException;
}

