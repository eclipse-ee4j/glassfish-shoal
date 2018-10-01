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

package com.sun.enterprise.ee.cms.impl.base;

import com.sun.enterprise.ee.cms.core.*;
import com.sun.enterprise.ee.cms.impl.common.GMSContextFactory;
import com.sun.enterprise.ee.cms.impl.common.ViewWindow;
import com.sun.enterprise.ee.cms.impl.common.GMSContext;
import com.sun.enterprise.ee.cms.logging.GMSLogDomain;
import com.sun.enterprise.ee.cms.spi.GMSMessage;
import com.sun.enterprise.ee.cms.spi.GroupCommunicationProvider;
import com.sun.enterprise.ee.cms.spi.MemberStates;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementation of GroupHandle interface.
 *
 * @author Shreedhar Ganapathy Date: Jan 12, 2004
 * @version $Revision$
 */
public final class GroupHandleImpl implements GroupHandle {
	private String groupName;
	private String serverToken;
	private GMSContext ctx;
	private static final Logger logger = GMSLogDomain.getLogger(GMSLogDomain.GMS_LOGGER);
	private static final String REC_PROGRESS_STATE = GroupManagementService.RECOVERY_STATE.RECOVERY_IN_PROGRESS.toString();
	private static final String REC_APPOINTED_STATE = GroupManagementService.RECOVERY_STATE.RECOVERY_SERVER_APPOINTED.toString();

	private static final int SYNC_WAIT = 2000;
	private static final List<String> EMPTY_LIST = new LinkedList<String>();
	private List<String> selfRecoveryList;

	public GroupHandleImpl(final String groupName, final String serverToken) {
		this.groupName = groupName;
		this.serverToken = serverToken;
		this.selfRecoveryList = new ArrayList<String>();
	}

	private GMSContext getGMSContext() {
		if (ctx == null) {
			ctx = (GMSContext) GMSContextFactory.getGMSContext(groupName);
		}
		return ctx;
	}

	/**
	 * Sends a message to all members of the Group. Expects a byte array as parameter carrying the payload.
	 *
	 * @param componentName Destination component in remote members.
	 * @param message Payload in byte array to be delivered to the destination.
	 */
	public void sendMessage(final String componentName, final byte[] message) throws GMSException {
		try {
			final GMSMessage gMsg = new GMSMessage(componentName, message, groupName, getGMSContext().getStartTime());
			getGMSContext().getGroupCommunicationProvider().sendMessage(null, gMsg, true);
		} catch (Throwable t) {
			if (t instanceof GMSException) {
				throw (GMSException) t;
			} else {
				throw new GMSException("failed to brodcast message to group " + groupName + " to target component:" + componentName, t);
			}
		}
	}

	/**
	 * Sends a message to a single member of the group Expects a targetServerToken representing the recipient member's id,
	 * the target component name in the target recipient member, and a byte array as parameter carrying the payload.
	 * Specifying a null component name would result in the message being delivered to all registered components in the
	 * target member instance.
	 *
	 * @param targetServerToken destination member's identification
	 * @param targetComponentName destination member's target component
	 * @param message Payload in byte array to be delivered to the destination.
	 */
	public void sendMessage(final String targetServerToken, final String targetComponentName, final byte[] message) throws GMSException {
		try {
			final GMSMessage gMsg = new GMSMessage(targetComponentName, message, groupName, getGMSContext().getStartTime());
			getGMSContext().getGroupCommunicationProvider().sendMessage(targetServerToken, gMsg, false);
		} catch (Throwable t) {
			if (t instanceof GMSException) {
				throw (GMSException) t;
			} else {
				throw new GMSException("failed to send message to target server:" + targetServerToken + " target component:" + targetComponentName, t);
			}
		}
	}

	public void sendMessage(List<String> targetServerTokens, String targetComponentName, byte[] message) throws GMSException {
		Throwable lastThrowable = null;
		String failedSendToken = null;
		final GMSMessage gMsg = new GMSMessage(targetComponentName, message, groupName, getGMSContext().getStartTime());
		if (targetServerTokens.isEmpty()) {
			getGMSContext().getGroupCommunicationProvider().sendMessage(null, gMsg, true);
		} else {
			for (String token : targetServerTokens) {
				try {
					getGMSContext().getGroupCommunicationProvider().sendMessage(token, gMsg, false);
				} catch (Throwable t) {
					lastThrowable = t;
					failedSendToken = token;
					logger.log(Level.WARNING, "group.handle.sendmessage.failed", new Object[] { message, targetComponentName, token, t.getLocalizedMessage() });
				}
			}
		}
		if (lastThrowable != null) {
			if (lastThrowable instanceof GMSException) {
				throw (GMSException) lastThrowable;
			} else {
				throw new GMSException("failed to send message to target server:" + failedSendToken + " target component=" + targetComponentName,
				        lastThrowable);
			}
		}
	}

	/**
	 * returns a DistributedStateCache object that provides the ability to set and retrieve CachedStates.
	 *
	 * @return DistributedStateCache
	 * @see com.sun.enterprise.ee.cms.core.DistributedStateCache
	 */
	public DistributedStateCache getDistributedStateCache() {
		// TBD: code review comment to follow up on in future.
		// consider an empty no-op DistributedStateCache instead of null when member type is WATCHDOG.
		// makes code cleaner not to have to check for null or isWatchdogy() all over.
		if (isWatchdog()) {
			return null;
		}
		return getGMSContext().getDistributedStateCache();
	}

	/**
	 * returns a List containing the current core members in the group.
	 *
	 * @return List a List of member token ids pertaining to core members
	 */
	public List<String> getCurrentCoreMembers() {
		final ViewWindow viewWindow = getGMSContext().getViewWindow();
		if (viewWindow == null) {
			return EMPTY_LIST;
		} else {
			return viewWindow.getCurrentCoreMembers();
		}
	}

	/**
	 * returns a List containing the current group membership including spectator members.
	 *
	 * @return List a List of member token ids pertaining to all members
	 */
	public List<String> getAllCurrentMembers() {
		final ViewWindow viewWindow = getGMSContext().getViewWindow();
		if (viewWindow == null) {
			return EMPTY_LIST;
		} else {
			return viewWindow.getAllCurrentMembers();
		}
	}

	public List<String> getCurrentCoreMembersWithStartTimes() {
		final ViewWindow viewWindow = getGMSContext().getViewWindow();
		if (viewWindow == null) {
			return EMPTY_LIST;
		} else {
			return viewWindow.getCurrentCoreMembersWithStartTimes();
		}
	}

	public List<String> getAllCurrentMembersWithStartTimes() {
		final ViewWindow viewWindow = getGMSContext().getViewWindow();
		if (viewWindow == null) {
			return EMPTY_LIST;
		} else {
			return viewWindow.getAllCurrentMembersWithStartTimes();
		}
	}

	/**
	 * Enables the caller to raise a logical fence on a specified target member token's component. This API is directly
	 * called only when a component is raising a fence itself and not as part of acquiring a signal. If this is part of
	 * acquiring a signal, then the call should be to signal.acquire() which encompasses raising a fence and potentially
	 * other state updates.
	 * <p>
	 * Failure Fencing is a group-wide protocol that, on one hand, requires members to update a shared/distributed
	 * datastructure if any of their components need to perform operations on another members' corresponding component. On
	 * the other hand, the group-wide protocol requires members to observe "Netiquette" during their startup so as to check
	 * if any of their components are being operated upon by other group members. Typically this check is performed by the
	 * respective components themselves. See the isFenced() method below for this check. When the operation is completed by
	 * the remote member component, it removes the entry from the shared datastructure. See the lowerFence() method below.
	 * <p>
	 * Raising the fence, places an entry into a distributed datastructure that is accessed by other members during their
	 * startup
	 *
	 * @param componentName the component name
	 * @param failedMemberToken the member token
	 * @throws GMSException the GMS generic exception
	 */
	public void raiseFence(final String componentName, final String failedMemberToken) throws GMSException {
		if (isWatchdog()) {
			return;
		}
		if (!isFenced(componentName, failedMemberToken)) {
			final DistributedStateCache dsc = getGMSContext().getDistributedStateCache();
			dsc.addToCache(componentName, getGMSContext().getServerIdentityToken(), failedMemberToken, setStateAndTime());
			if (fenceForSelfRecovery(failedMemberToken)) {
				saveRaisedFenceState(componentName, failedMemberToken);
			}
			if (logger.isLoggable(Level.FINE)) {
				logger.log(Level.FINE, "Fence raised for member " + failedMemberToken + " by member " + getGMSContext().getServerIdentityToken() + " component "
				        + componentName);
			}
		} else {
			throw new GMSException("Could not raise fence. Fence for member " + failedMemberToken + " and Component " + componentName + " already exists");
		}
	}

	private void saveRaisedFenceState(final String componentName, final String failedMemberToken) {
		selfRecoveryList.add(componentName + failedMemberToken);
	}

	private boolean fenceForSelfRecovery(final String failedMemberToken) {
		return failedMemberToken.equals(getGMSContext().getServerIdentityToken());
	}

	/**
	 * Enables the caller to lower a logical fence that was earlier raised on a target member component. This is typically
	 * done when the operation being performed on the target member component has now completed. This api is directly called
	 * only by a component that is lowering a fence directly and not as part of releasing a signal. If the operation is to
	 * release a signal, then the appropriate call is to signal.release() which encompasses lowering the fence and other
	 * cleanups.
	 *
	 * @param componentName target member component
	 * @param failedMemberToken the member token of the failed member
	 * @throws GMSException the GMS Generic Exception
	 */
	public void lowerFence(final String componentName, final String failedMemberToken) throws GMSException { // If there is a fence for delegated recovery or
	                                                                                                         // self recovery
		if (isWatchdog()) {
			return;
		}
		if (componentName == null || failedMemberToken == null) {
			throw new IllegalArgumentException("parameters to GroupHandle.lowerFence must be non-null");
		}

		if (isFenced(componentName, failedMemberToken) || selfRecoveryList.contains(componentName + failedMemberToken)) {
			final DistributedStateCache dsc = getGMSContext().getDistributedStateCache();
			dsc.removeFromCache(componentName, getGMSContext().getServerIdentityToken(), failedMemberToken);
			if (logger.isLoggable(Level.FINE)) {
				logger.log(Level.FINE, "Fence lowered for member " + failedMemberToken + " by member " + getGMSContext().getServerIdentityToken()
				        + " component " + componentName);
			}
			// this removes any recovery appointments that were made but were
			// not exercised by the client thus leaving an orphan entry in
			// cache.
			removeRecoveryAppointments(dsc.getFromCache(failedMemberToken), failedMemberToken, componentName);
			selfRecoveryList.remove(componentName + failedMemberToken);
		}
	}

	public void removeRecoveryAppointments(String failedMemberToken, String componentName) throws GMSException {
		DistributedStateCache dsc = getGMSContext().getDistributedStateCache();
		removeRecoveryAppointments(dsc.getFromCache(failedMemberToken), failedMemberToken, componentName);
	}

	private void removeRecoveryAppointments(final Map<GMSCacheable, Object> fromCache, final String failedMemberToken, final String componentName)
	        throws GMSException {
		if (isWatchdog()) {
			return;
		}

		final DistributedStateCache dsc = getGMSContext().getDistributedStateCache();

		for (final Map.Entry<GMSCacheable, Object> entry : fromCache.entrySet()) {
			final GMSCacheable cKey = entry.getKey();
			if (cKey.getKey().equals(failedMemberToken) && cKey.getComponentName().equals(componentName)
			        && entry.getValue().toString().startsWith(REC_APPOINTED_STATE)) {
				if (logger.isLoggable(Level.FINE)) {
					logger.log(Level.FINE, "remove RecoveryAppointment componentName: " + componentName + " failedMember:" + failedMemberToken + "value="
					        + entry.getValue().toString());
				}
				dsc.removeFromCache(cKey.getComponentName(), cKey.getMemberTokenId(), (Serializable) cKey.getKey());
			}
		}
	}

	/**
	 * Provides the status of a member component's fence, if any.
	 * <p>
	 * This check is <strong>mandatorily</strong> done at the time a member component is in the process of starting(note
	 * that at this point we assume that this member failed in its previous lifetime).
	 * <p>
	 * The boolean value returned would indicate if this member component is being recovered by any other member. The
	 * criteria for returning a boolean "true" is that this componentName-memberToken combo is present as a value for any
	 * key in the GMS DistributedStateCache. If a true is returned, for instance, this could mean that the client component
	 * should continue its startup without attempting to perform its own recovery operations.
	 * </p>
	 * <p>
	 * The criteria for returning a boolean "false" is that the componentId-memberTokenId combo is not present in the list
	 * of values in the DistributedStateCache.If a boolean "false" is returned, this could mean that the client component
	 * can continue with its lifecycle startup per its normal startup policies.
	 *
	 * @param componentName the component name
	 * @param memberToken the member token
	 * @return boolean
	 */
	public boolean isFenced(final String componentName, final String memberToken) {
		if (isWatchdog()) {
			return false;
		}

		boolean retval = false;
		final DistributedStateCache dsc = getDistributedStateCache();
		final Map<GMSCacheable, Object> entries;
		final List<String> members = getAllCurrentMembers();
		int count = 0;
		while (members.size() > 1 && !dsc.isFirstSyncDone()) {
			logger.log(Level.FINE, "Waiting for DSC first Sync");
			try {
				Thread.sleep(SYNC_WAIT);
				count++;
				// this is
				if (count > 4) {
					forceDSCSync((DistributedStateCacheImpl) dsc);
				}
			} catch (InterruptedException e) {
				logger.log(Level.WARNING, e.getLocalizedMessage());
			}
		}
		entries = dsc.getFromCache(memberToken);
		for (Map.Entry<GMSCacheable, Object> entry : entries.entrySet()) {
			final GMSCacheable c = entry.getKey();
			if (componentName.equals(c.getComponentName())) { // if this member is being recovered by someone
				if (memberToken.equals(c.getKey())) { // if this is an old record of a self recovery then ignore
					if (!memberToken.equals(c.getMemberTokenId())) {
						if (((String) entry.getValue()).startsWith(REC_PROGRESS_STATE)) {
							if (logger.isLoggable(Level.FINER)) {
								logger.log(Level.FINER, c.toString() + " value:" + entry.getValue());
								logger.log(Level.FINER, "Returning true for isFenced query");
							}
							retval = true;
							break;
						}
					}
				}
			}
		}
		return retval;
	}

	private void forceDSCSync(final DistributedStateCacheImpl dsc) {
		if (isWatchdog()) {
			return;
		}
		try {
			final String token = getGMSContext().getGroupCommunicationProvider().getGroupLeader();

			if (logger.isLoggable(Level.FINE)) {
				logger.log(Level.FINE, "Force Syncing DistributedStateCache with " + token);
			}
			dsc.syncCache(token, true);
		} catch (GMSException e) {
			logger.log(Level.WARNING, "gh.dsc.force.sync.failed", new Object[] { e.getLocalizedMessage() });
		}

	}

	public boolean isMemberAlive(final String memberToken) {
		if (memberToken == null) {
			throw new IllegalArgumentException("isMemberAlive parameter memberToken must be non-null");
		}
		return memberToken.equals(serverToken) || getAllCurrentMembers().contains(memberToken);
	}

	public String getGroupLeader() {
		return getGMSContext().getGroupCommunicationProvider().getGroupLeader();
	}

	public boolean isGroupLeader() {
		return getGMSContext().getGroupCommunicationProvider().isGroupLeader();
	}

	public List<String> getSuspectList() {
		return getGMSContext().getSuspectList();
	}

	public String toString() {
		return "group:" + groupName + " server:" + serverToken;
	}

	private static String setStateAndTime() {
		return GroupManagementService.RECOVERY_STATE.RECOVERY_IN_PROGRESS.toString() + "|" + System.currentTimeMillis();

	}

	public List<String> getCurrentAliveOrReadyMembers() {
		List<String> members = getCurrentCoreMembers();
		List<String> currentAliveOrReadyMembers = new ArrayList<String>();
		GroupCommunicationProvider gcp = getGMSContext().getGroupCommunicationProvider();

		for (String member : members) {
			MemberStates state = gcp.getMemberState(member);
			if (state == MemberStates.ALIVE || state == MemberStates.READY || state == MemberStates.ALIVEANDREADY) {
				currentAliveOrReadyMembers.add(member);
			}
		}
		return currentAliveOrReadyMembers;
	}

	public MemberStates getMemberState(String member) {
		GroupCommunicationProvider gcp = getGMSContext().getGroupCommunicationProvider();
		return gcp.getMemberState(member);
	}

	public MemberStates getMemberState(String member, long threshold, long timeout) {
		GroupCommunicationProvider gcp = getGMSContext().getGroupCommunicationProvider();
		return gcp.getMemberState(member, threshold, timeout);
	}

	public boolean isWatchdog() {
		return getGMSContext().getMemberType() == GroupManagementService.MemberType.WATCHDOG;
	}

	public void announceWatchdogObservedFailure(String serverToken) throws GMSException {
		getGMSContext().getGroupCommunicationProvider().announceWatchdogObservedFailure(serverToken);
	}

	public List<GMSMember> getCurrentView() {
		return getGMSContext().getViewWindow().getCurrentView();
	}

	public List<GMSMember> getPreviousView() {
		return getGMSContext().getViewWindow().getPreviousView();
	}

	public AliveAndReadyView getPreviousAliveAndReadyCoreView() {
		return getGMSContext().getPreviousAliveAndReadyView();
	}

	public AliveAndReadyView getCurrentAliveAndReadyCoreView() {
		return getGMSContext().getCurrentAliveAndReadyView();
	}
}
