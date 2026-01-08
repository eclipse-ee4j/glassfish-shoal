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

package org.glassfish.shoal.gms.api.spi;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import org.glassfish.shoal.gms.api.core.GMSConstants;
import org.glassfish.shoal.gms.api.core.GMSException;
import org.glassfish.shoal.gms.api.core.MemberNotInViewException;
import org.glassfish.shoal.gms.api.core.GMSConstants.groupStartupState;

/**
 * Provides a plugging interface for integrating group communication providers(GCP). Acts as a bridge between GCP
 * packages and GMS. Implementation of this API allows GMS objects to delegate interaction with the underlying GCP
 * through commonly executed calls. GCPs should have a notion of grouping members and allow for messaging. GCPs should
 * be capable of notifying group events. GCP should provide interfaces for programmatic configuration of their group
 * communication and membership protocols.
 *
 * @author Shreedhar Ganapathy Date: Jun 26, 2006
 * @version $Revision$
 */
public interface GroupCommunicationProvider {
    /**
     * Initializes the Group Communication Service Provider with the requisite values of group identity, member(self)
     * identity, and a Map containing recognized and valid configuration properties that can be set/overriden by the
     * employing application. The valid property keys must be specified in a datastructure that is available to the
     * implementation and to GMS.
     *
     * @param memberName member name
     * @param groupName name of group
     * @param identityMap - additional member identity params specified through key-value pairs.
     * @param configProperties - properties that the employing applications likes to configure in the underlying GCP.
     */
    void initializeGroupCommunicationProvider(String memberName, String groupName, Map<String, String> identityMap, Map configProperties) throws GMSException;

    /**
     * Joins the group using semantics specified by the underlying GCP system
     */
    void join();

    /**
     * Sends an announcement to the group that a cluster wide shutdown is impending
     *
     * @param gmsMessage an object that encapsulates the application's Message
     */
    void announceClusterShutdown(GMSMessage gmsMessage);

    /**
     * Leaves the group as a result of a planned administrative action to shutdown.
     *
     * @param isClusterShutdown - true if we are leaving as part of a cluster wide shutdown
     */
    void leave(boolean isClusterShutdown);

    /**
     * Sends a message using the underlying group communication providers'(GCP's) APIs. Requires the users' message to be
     * wrapped into a GMSMessage object.
     *
     * @param targetMemberIdentityToken The member token string that identifies the target member to which this message is
     * addressed. The implementation is expected to provide a mapping the member token to the GCP's addressing semantics. If
     * null, the entire group would receive this message.
     * @param message a Serializable object that wraps the user specified message in order to allow remote GMS instances to
     * unpack this message appropriately.
     * @param synchronous setting true here will call the underlying GCP's api that corresponds to a synchronous message, if
     * available.
     * @throws org.glassfish.shoal.gms.api.core.GMSException wraps the underlying exception
     */
    void sendMessage(String targetMemberIdentityToken, Serializable message, boolean synchronous) throws GMSException, MemberNotInViewException;

    /**
     * Sends a message to the entire group using the underlying group communication provider's APIs. The Serializable object
     * here is a GMSMessage Object.
     *
     * @param message a Serializable object that wraps the users specified message in order to allow remote GMS instances to
     * unpack this message appropriately
     * @throws GMSException Underlying exception is wrapped in a GMSException
     */
    void sendMessage(Serializable message) throws GMSException, MemberNotInViewException;

    /**
     * returns a list of members that are currently alive in the group. The list should contain the member identity token
     * that GMS understands as member identities.
     *
     * @return list of current live members
     */
    List<String> getMembers();

    /**
     * Returns true if this peer is the leader of the group
     *
     * @return boolean true if group leader, false if not.
     */
    boolean isGroupLeader();

    /**
     * Returns the state of the member. The parameters <code>threshold</code> and <code>timeout</code> enable the caller to
     * tune this lookup between accuracy and time it will take to complete the call. It is lowest cost to just return the
     * local computed concept for a member's state. <code>threshold</code> parameter controls this. If the local state is
     * stale, then the <code>timeout</code> parameter enables one to control how long they are willing to wait for more
     * accurate state information from the member itself.
     *
     * @param member
     * @param threshold allows caller to specify how up-to-date the member state information has to be. The larger this
     * value, the better chance that this method just returns the local concept of this member's state. The smaller this
     * value, the better chance that the local state is not fresh enough and the method will find out directly from the
     * instance what its current state is.
     * @param timeout is the time for which the caller instance should wait to get the state from the concerned member via a
     * network call. if timeout and threshold are both 0, then the default values are used if threshold is 0, then a network
     * call is made to get the state of the member if timeout is 0, then the caller instance checks for the state of the
     * member stored with it within the given threshold
     * @return the state of the member Returns UNKNOWN when the local state for the member is considered stale (determined
     * by threshold value) and the network invocation to get the member state times out before getting a reply from the
     * member of what its state is.
     */

    MemberStates getMemberState(String member, long threshold, long timeout);

    /**
     * Returns the member state as defined in the Enum MemberStates
     *
     * @return MemberStates
     * @param memberIdentityToken identity of member.
     */
    MemberStates getMemberState(String memberIdentityToken);

    /**
     * Returns the Group Leader as defined by the underlying Group Communication Provider.
     *
     * @return String
     */
    String getGroupLeader();

    /**
     * <p>
     * Provides for this instance to become a group leader explicitly. Typically this can be employed by an administrative
     * member to become a group leader prior to shutting down a group of members simultaneously.
     * </p>
     *
     * <p>
     * For underlying Group Communication Providers who don't support the feature of a explicit leader role assumption, the
     * implementation of this method would be a no-op.
     * </p>
     *
     **/
    void assumeGroupLeadership();

    /**
     * Can be used especially to inform the HealthMonitoring service that the group is shutting down.
     */
    void setGroupStoppingState();

    /**
     * This API is provided for the parent application to report to the group its joined and ready state to begin processing
     * its operations. The group member that this parent application represents is now ready to process its operations at
     * the time of this announcement to the group. GMS clients in all other group members that are interested in knowing
     * when another member is ready to start processing operations, can subscribe to the event JoinedAndReadyEvent and be
     * notified of this JoinedAndReadyNotificationSignal.
     */

    void reportJoinedAndReadyState();

    /**
     * Allow for enhanced GMS failure detection by external control entities (one example is NodeAgent of Glassfish
     * Application Server.) Only a GMS MemberType of WATCHDOG is allowed to broadcast to all members of a group that this
     * <code>serverToken</code> has failed.
     *
     * @param serverToken failed member
     * @throws GMSException if called by a member that is not a WATCHDOG member or if serverToken is not currently running
     * in group.
     */
    void announceWatchdogObservedFailure(String serverToken) throws GMSException;

    /**
     * Invoked indirectly by a controlling parent application that has a static preconfiguration of all members of the group
     * to announce that the parent application is "initiating" and then that it has "completed" startup of all preconfigured
     * members of this group.
     *
     * <P>
     * Group members in parameter <code>members</code> is interpreted differently based on startupState. All preconfigured
     * members of group are passed in <code>members</code> when {@link groupStartupState#INITIATED} or
     * {@link groupStartupState#COMPLETED_SUCCESS}. When startupState is {@link groupStartupState#COMPLETED_FAILED},
     * <code>members</code> is a list of the members that failed to start.
     *
     * @param groupName
     * @param startupState demarcate initiation of groupStartup and completion of group startup
     * @param memberTokens list of memberTokens.
     */
    void announceGroupStartup(String groupName, GMSConstants.groupStartupState startupState, List<String> memberTokens);

    boolean isDiscoveryInProgress();
}
