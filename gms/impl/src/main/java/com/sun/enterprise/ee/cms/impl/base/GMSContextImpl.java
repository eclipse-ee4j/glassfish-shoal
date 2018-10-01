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
import com.sun.enterprise.ee.cms.impl.common.*;
import com.sun.enterprise.ee.cms.spi.GMSMessage;
import com.sun.enterprise.ee.cms.spi.GroupCommunicationProvider;
import com.sun.enterprise.ee.cms.spi.MemberStates;

import static com.sun.enterprise.ee.cms.core.GroupManagementService.MemberType.WATCHDOG;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * @author Shreedhar Ganapathy Date: Jun 26, 2006
 * @version $Revision$
 */
public class GMSContextImpl extends GMSContextBase {
	private ArrayBlockingQueue<EventPacket> viewQueue;
	private static final int MAX_VIEWS_IN_QUEUE = 200;
	private ArrayBlockingQueue<MessagePacket> messageQueue;
	private static final int DEFAULT_INCOMING_MSG_QUEUE_SIZE = 500;
	private int MAX_MSGS_IN_QUEUE = DEFAULT_INCOMING_MSG_QUEUE_SIZE;
	private int DEFAULT_INCOMING_MSG_THREAD_POOL_SIZE = 5;
	private int INCOMING_MSG_THREAD_POOL_SIZE = DEFAULT_INCOMING_MSG_THREAD_POOL_SIZE;
	private ViewWindowImpl viewWindow;
	private GroupCommunicationProvider groupCommunicationProvider;
	private DistributedStateCache distributedStateCache;
	private GroupHandle gh;
	private Properties configProperties;
	private boolean isGroupShutdown = false; // remember if this context has left the group during a group shutdown.
	private boolean isGroupStartup = false;
	private Thread viewWindowThread = null;
	private Thread messageWindowThread = null;
	private AliveAndReadyViewWindow aliveAndReadyViewWindow = null;
	private final Map<String, RejoinSubevent> instanceRejoins = new ConcurrentHashMap<String, RejoinSubevent>();
	private MessageWindow messageWindow = null;
	private GMSMonitor gmsMonitor;

	public GMSMonitor getGMSMonitor() {
		return gmsMonitor;
	}

	public GMSContextImpl(final String serverToken, final String groupName, final GroupManagementService.MemberType memberType,
	        final Properties configProperties) {
		super(serverToken, groupName, memberType);
		MAX_MSGS_IN_QUEUE = Utility.getIntProperty(ServiceProviderConfigurationKeys.INCOMING_MESSAGE_QUEUE_SIZE.toString(), DEFAULT_INCOMING_MSG_QUEUE_SIZE,
		        configProperties);
		if (MAX_MSGS_IN_QUEUE != DEFAULT_INCOMING_MSG_QUEUE_SIZE && logger.isLoggable(Level.CONFIG)) {
			logger.config("INCOMING_MESSAGE_QUEUE_SIZE: " + MAX_MSGS_IN_QUEUE + " overrides default value of " + DEFAULT_INCOMING_MSG_QUEUE_SIZE);
		}
		INCOMING_MSG_THREAD_POOL_SIZE = Utility.getIntProperty(ServiceProviderConfigurationKeys.INCOMING_MESSAGE_THREAD_POOL_SIZE.toString(),
		        DEFAULT_INCOMING_MSG_THREAD_POOL_SIZE, configProperties);
		if (INCOMING_MSG_THREAD_POOL_SIZE != DEFAULT_INCOMING_MSG_THREAD_POOL_SIZE && logger.isLoggable(Level.CONFIG)) {
			logger.config(
			        "INCOMING_MSG_THREAD_POOL_SIZE: " + INCOMING_MSG_THREAD_POOL_SIZE + " overrides default value of " + DEFAULT_INCOMING_MSG_THREAD_POOL_SIZE);
		}
		long MAX_STARTCLUSTER_DURATION_MS = Utility.getLongProperty("MAX_STARTCLUSTER_DURATION_MS", 10000, configProperties);
		this.gmsMonitor = new GMSMonitor(serverToken, groupName, configProperties);
		aliveAndReadyViewWindow = new AliveAndReadyViewWindow(this);
		aliveAndReadyViewWindow.setStartClusterMaxDuration(MAX_STARTCLUSTER_DURATION_MS);
		router = new Router(groupName, MAX_MSGS_IN_QUEUE + 100, aliveAndReadyViewWindow, INCOMING_MSG_THREAD_POOL_SIZE, gmsMonitor);

		this.configProperties = configProperties;
		groupCommunicationProvider = new GroupCommunicationProviderImpl(groupName);

		if (isWatchdog()) {
			// lower overhead by not having view management for WATCHDOG.
			viewQueue = null;
			viewWindow = null;
		} else {
			viewQueue = new ArrayBlockingQueue<EventPacket>(MAX_VIEWS_IN_QUEUE, Boolean.TRUE);
			viewWindow = new ViewWindowImpl(groupName, viewQueue);
		}
		messageQueue = new ArrayBlockingQueue<MessagePacket>(MAX_MSGS_IN_QUEUE, Boolean.TRUE);
		gh = new GroupHandleImpl(groupName, serverToken);
		// TODO: consider untying the Dist State Cache creation from GMSContext.
		// It should be driven independent of GMSContext through a factory as
		// other impls of this interface can exist
		createDistributedStateCache();
		logger.log(Level.FINE, "gms.init");
	}

	@Override
	protected void createDistributedStateCache() {
		if (isWatchdog()) {
			distributedStateCache = null;
		} else {
			distributedStateCache = DistributedStateCacheImpl.getInstance(groupName);
		}
	}

	/**
	 * returns Group handle
	 *
	 * @return Group handle
	 */
	@Override
	public GroupHandle getGroupHandle() {
		return gh;
	}

	@Override
	public DistributedStateCache getDistributedStateCache() {
		// Never create a distributed state cache for a WATCHDOG.
		if (distributedStateCache == null && !isWatchdog()) {
			createDistributedStateCache();
		}
		return distributedStateCache;
	}

	@Override
	public void join() throws GMSException {
		viewWindowThread = isWatchdog() ? null : new Thread(viewWindow, "GMS ViewWindowThread Group-" + groupName);
		messageWindow = new MessageWindow(groupName, messageQueue);

		messageWindowThread = new Thread(messageWindow, "GMS MessageWindowThread Group-" + groupName);
		messageWindowThread.setDaemon(true);
		messageWindowThread.start();

		if (viewWindowThread != null) {
			viewWindowThread.setDaemon(true);
			viewWindowThread.start();
		}

		final Map<String, String> idMap = new HashMap<String, String>();
		idMap.put(CustomTagNames.MEMBER_TYPE.toString(), memberType);
		idMap.put(CustomTagNames.GROUP_NAME.toString(), groupName);
		idMap.put(CustomTagNames.START_TIME.toString(), startTime.toString());

		try {
			groupCommunicationProvider.initializeGroupCommunicationProvider(serverToken, groupName, idMap, configProperties);
			groupCommunicationProvider.join();
		} catch (Throwable t) {

			// transport can throw IllegalStateException if not able to start up correctly.
			GMSException ge = new GMSException("failed to join group " + groupName, t);
			throw ge;
		}
	}

	@Override
	public void leave(final GMSConstants.shutdownType shutdownType) {
		if (shutdownHelper.isGroupBeingShutdown(groupName)) {
			logger.log(Level.INFO, "shutdown.groupshutdown", new Object[] { groupName });
			groupCommunicationProvider.leave(true);
			isGroupShutdown = true;
			shutdownHelper.removeFromGroupShutdownList(groupName);
		} else {
			logger.log(Level.INFO, "shutdown.instanceshutdown", new Object[] { groupName });
			groupCommunicationProvider.leave(false);
		}
		shuttingDown = true;
		if (viewWindowThread != null) {
			viewWindowThread.interrupt();
		}
		if (messageWindowThread != null) {
			messageWindowThread.interrupt();
		}
		if (messageWindow != null) {
			messageWindow.stop();
		}
		if (router != null) {
			router.shutdown();
		}
		if (gmsMonitor != null) {
			gmsMonitor.stop();
		}
		if (distributedStateCache != null) {
			distributedStateCache.removeAll();
			distributedStateCache = null;
		}
	}

	@Override
	public long getStartTime() {
		return startTime;
	}

	@Override
	public void announceGroupShutdown(final String groupName, final GMSConstants.shutdownState shutdownState) {
		// It is required to announce cluster shutdown BEFORE seizing group leadership.
		// Otherwise, seizing groupleadership is viewed as a master collision.
		// This subtle ordering is necessary so the current master resigns and allows the admin
		// member to grab groupleadership before the entire group is shutdown.
		groupCommunicationProvider.announceClusterShutdown(new GMSMessage(GMSConstants.shutdownType.GROUP_SHUTDOWN.toString(), null, groupName, null));
		if (!this.getGroupCommunicationProvider().isGroupLeader()) {
			logger.log(Level.INFO, "gmsctx.assume.groupleader", new Object[] { groupName });
			assumeGroupLeadership();
		}
	}

	@Override
	public void announceGroupStartup(final String groupName, final GMSConstants.groupStartupState startupState, final List<String> memberTokens) {
		groupCommunicationProvider.announceGroupStartup(groupName, startupState, memberTokens);
	}

	@Override
	public boolean addToSuspectList(final String token) {
		boolean retval = false;
		synchronized (suspectList) {
			if (!suspectList.contains(token)) {
				suspectList.add(token);
				retval = true;
			}
		}
		return retval;
	}

	@Override
	public void removeFromSuspectList(final String token) {
		synchronized (suspectList) {
			if (suspectList.contains(token)) {
				suspectList.remove(token);
			}
		}
	}

	@Override
	public boolean isSuspected(final String token) {
		boolean retval = false;
		synchronized (suspectList) {
			if (suspectList.contains(token)) {
				retval = true;
			}
		}
		return retval;
	}

	@Override
	public List<String> getSuspectList() {
		final List<String> retval;
		synchronized (suspectList) {
			retval = new ArrayList<String>(suspectList);
		}
		return retval;
	}

	@Override
	public ShutdownHelper getShutdownHelper() {
		return shutdownHelper;
	}

	ArrayBlockingQueue<EventPacket> getViewQueue() {
		return viewQueue;
	}

	ArrayBlockingQueue<MessagePacket> getMessageQueue() {
		return messageQueue;
	}

	@Override
	public GroupCommunicationProvider getGroupCommunicationProvider() {
		return groupCommunicationProvider;
	}

	@Override
	public com.sun.enterprise.ee.cms.impl.common.ViewWindow getViewWindow() {
		return viewWindow;
	}

	@Override
	public void assumeGroupLeadership() {
		groupCommunicationProvider.assumeGroupLeadership();
	}

	@Override
	public boolean isGroupBeingShutdown(String groupName) {
		return isGroupShutdown || getShutdownHelper().isGroupBeingShutdown(groupName);
	}

	@Override
	public boolean isGroupStartup() {
		return isGroupStartup;
	}

	@Override
	public void setGroupStartup(boolean value) {
		isGroupStartup = value;
	}

	@Override
	public boolean isWatchdog() {
		return this.getMemberType() == WATCHDOG;
	}

	public int outstandingNotifications() {
		return viewQueue.size();
	}

	@Override
	public AliveAndReadyView getPreviousAliveAndReadyView() {
		if (aliveAndReadyViewWindow != null) {
			return aliveAndReadyViewWindow.getPreviousView();
		} else {
			return null;
		}
	}

	@Override
	public AliveAndReadyView getCurrentAliveAndReadyView() {
		if (aliveAndReadyViewWindow != null) {
			return aliveAndReadyViewWindow.getCurrentView();
		} else {
			return null;
		}
	}

	@Override
	public Map<String, RejoinSubevent> getInstanceRejoins() {
		return instanceRejoins;
	}

	@Override
	public AliveAndReadyViewWindow getAliveAndReadyViewWindow() {
		return aliveAndReadyViewWindow;
	}

	@Override
	public void setGroupStartupJoinMembers(Set<String> members) {
		this.viewWindow.setPendingGroupJoins(members);
	}

	@Override
	public boolean isGroupStartupComplete() {
		return viewWindow.isGroupStartupComplete();
	}

	@Override
	public boolean setGroupStartupState(String member, MemberStates state) {
		return viewWindow.setGroupStartupState(member, state);
	}
}
