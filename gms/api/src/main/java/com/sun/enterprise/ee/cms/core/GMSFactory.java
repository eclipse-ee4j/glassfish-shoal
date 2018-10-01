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

import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * <p>
 * This is the entry point to GMS for the parent application that is initiating GMS module and by client components in
 * the parent app that need to interact with GMS for group events or send or receive messages.
 * </p>
 * <p>
 * GMSFactory is the interface for starting GMS module through the startGMSModule() api which returns a
 * GroupManagementService instance, and for retrieving the said GroupManagementService instance by any client
 * components.
 * </p>
 *
 * <p>
 * The GroupManagementService instance provides APIs for registering clients who wish to be notified of Group Events and
 * Message Events, and in addition provides a reference to GroupHandle, and and api for announcing the impending
 * shutdown of this parent process.
 * </p>
 *
 * <p>
 * Example for parent lifecycle module to start GMS:<br>
 * <code>final Runnable gms = GMSFactory.startGMSModule(serverName, groupName,
                                            memberType, properties);<br>
 * final Thread gservice = new Thread(gms, "GMSThread");<br>
   gservice.start();<br>
 * </code>
 * </p>
 *
 * <p>
 * Example for parent lifecycle module to shutdown GMS:<br>
 * <code>
 * gms.shutdown(GMSConstants.shutdownType.INSTANCE_SHUTDOWN); <br>
 * or<br>
 * gms.shutdown(GMSConstants.shutdownType.GROUP_SHUTDOWN);<br>
 * </code>
 * </p>
 * <p>
 * Registration Example for clients that want to consume group events and message events:<br>
 * <code>
 * GroupManagementService gms = GMSFactory.getGMSModule(groupName);<br>
 * gms.addActionFactory(myfailureNotificationActionFactoryImpl); <br>
 * </code>
 * </p>
 * 
 * @author Shreedhar Ganapathy
 * @version $Revision$
 */

public class GMSFactory {
	static private Logger LOG = Logger.getLogger("ShoalLogger.api", StringManager.LOG_STRINGS);
	static private StringManager sm = StringManager.getInstance();

	private static Hashtable<String, GroupManagementService> groups = new Hashtable<String, GroupManagementService>();
	private static Map<String, Boolean> gmsEnabledMap = new HashMap<String, Boolean>();
	private static String memberToken;

	private GMSFactory() {
	}

	/**
	 * starts and returns the GMS module object as a Runnable that could be started in a separate thread. This method is
	 * only used to create an instance of GroupManagementService and sets up the configuration properties in appropriate
	 * delegate objects. To actually create a group or join an existing group, one has to either pass in the
	 * GroupManagementService Object in a new Thread and start it or call the GroupManagementService.join() method.
	 *
	 * The startGMSModule method is expected to be called by the parent module's lifecycle management code to initiate GMS
	 * module. Invocation of this method assumes that GMS is enabled in the parent application's configuration.
	 *
	 * Calls to GMSFactory.getGMSModule() made before any code calls this method will result in GMSNotEnabledException to be
	 * thrown.
	 *
	 * @param serverToken a logical name or identity given the member that is repeatable over lifetimes of the server
	 * @param groupName name of the group
	 * @param memberType The member type corresponds to the MemberType specified in GroupManagementService.MemberType
	 * @param properties Key-Value pairs of entries that are intended to configure the underlying group communication
	 * provider's protocols such as address, failure detection timeouts and retries, etc. Allowable keys are specified in
	 * GMSConfigConstants
	 * @return java.lang.Runnable
	 */
	public static Runnable startGMSModule(final String serverToken, final String groupName, final GroupManagementService.MemberType memberType,
	        final Properties properties) {
		if (serverToken == null) {
			throw new RuntimeException(sm.get("ex.factory.start.missing.instance"));
		}
		if (groupName == null) {
			throw new RuntimeException(sm.get("ex.factory.start.missing.group"));
		}
		GroupManagementService gms;
		// if this method is called, GMS is enabled. It is assumed that
		// calling code made checks in configurations about the enablement
		// The recommended way for calling code for this purpose is to call the
		// setGMSEnabledState() method in this class(see below).
		gmsEnabledMap.put(groupName, Boolean.TRUE);
		try { // sanity check: if this group instance is
		      // already created return that instance
			gms = getGMSModule(groupName);
		} catch (GMSException e) {
			gms = getGroupManagementServiceInstance();
			gms.initialize(serverToken, groupName, memberType, properties);
			memberToken = serverToken;
			groups.put(getCompositeKey(groupName), gms);
		}
		return (Runnable) gms;
	}

	/**
	 * This returns an instance of the GroupManagementService for a given non-null group name.
	 * 
	 * @param groupName groupName
	 * @return GroupManagementService
	 * @throws GMSException - if the groupName is null
	 *
	 * @throws GMSNotEnabledException - If GMS is not enabled
	 * @throws GMSNotInitializedException - If GMS is not initialized
	 */
	public static GroupManagementService getGMSModule(final String groupName) throws GMSNotEnabledException, GMSException, GMSNotInitializedException {
		if (groupName == null) {
			throw new GMSException(sm.get("ex.factory.start.missing.group"));
		}
		final String key = getCompositeKey(groupName);
		if (groups.containsKey(key))
			return groups.get(key);
		else if (!isGMSEnabled(groupName)) {
			throw new GMSNotEnabledException(sm.get("ex.factory.get.gms.is.disabled", new Object[] { groupName }));
		} else {
			throw new GMSNotInitializedException(sm.get("ex.factory.get.not.init", new Object[] { groupName }));
		}

	}

	/**
	 * This is to be used only in the case where this process is a member of one and only one group and the group name is
	 * unknown to the caller.
	 * 
	 * @return GroupManagementService
	 * @throws GMSException - wraps a throwable GMSNotInitializedException if there are no GMS instances found.
	 */
	public static GroupManagementService getGMSModule() throws GMSException {
		GroupManagementService gms;
		final Collection instances = getAllGMSInstancesForMember();
		if (instances.size() == 0) {
			throw new GMSNotInitializedException(sm.get("ex.init.failure"));
		}
		gms = (GroupManagementService) instances.toArray()[0];
		return gms;
	}

	/**
	 * For the case where there are multiple groups in which this process has become a member.
	 * 
	 * @return Collection
	 */
	public static Collection getAllGMSInstancesForMember() {
		return groups.values();
	}

	private static String getCompositeKey(final String groupName) {
		return memberToken + "::" + groupName;
	}

	/**
	 * returns true if GMS is enabled for the specified group.
	 * 
	 * @param groupName Name of the group
	 * @return true if GMS is enabled
	 */
	public static boolean isGMSEnabled(final String groupName) {
		final Boolean val = gmsEnabledMap.get(groupName);
		return !(val == null || val.equals(Boolean.FALSE));
	}

	/**
	 * enables an initialization code in the Application layer to set GMS to be enabled or not based on the application's
	 * configuration
	 * 
	 * @param groupName Name of the group
	 * @param value a Boolean value
	 */
	public static void setGMSEnabledState(final String groupName, final Boolean value) {
		gmsEnabledMap.put(groupName, value);
	}

	/**
	 * removes the GMS instance that is cached from a prior initialization. This is typically called only when GMS module is
	 * being shutdown by a lifecycle action.
	 * 
	 * @param groupName Name of the Group
	 */
	public static void removeGMSModule(final String groupName) {
		if (groupName != null) {
			final String key = getCompositeKey(groupName);
			if (groups.containsKey(key)) {
				groups.remove(key);
			}
		}
	}

	private static GroupManagementService findByServiceLoader() {
		GroupManagementService groupManagementService = null;
		ServiceLoader<GroupManagementService> loader = ServiceLoader.load(GroupManagementService.class);
		Iterator<GroupManagementService> iter = loader.iterator();

		if (iter.hasNext()) {
			try {
				groupManagementService = iter.next().getClass().newInstance();
			} catch (Throwable t) {
				LOG.log(Level.WARNING, "factory.load.service.error");
				LOG.log(Level.WARNING, "stack trace", t);
			}
		}
		if (groupManagementService == null) {
			LOG.log(Level.SEVERE, sm.get("factory.fatal"));
		} else {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.log(Level.FINE, "factory.findservice", new Object[] { groupManagementService.getClass().getName() });
			}
		}
		return groupManagementService;
	}

	private static GroupManagementService findByClassLoader(String classname) {
		GroupManagementService gmsImpl = null;
		// for jdk 5. just use class loader.
		try {
			Class GmsImplClass = Class.forName(classname);
			if (GmsImplClass == null) {
				LOG.log(Level.SEVERE, "factory.load.service.error");
			} else {
				gmsImpl = (GroupManagementService) GmsImplClass.newInstance();
				if (LOG.isLoggable(Level.FINE)) {
					LOG.log(Level.FINE, "findByClassLoader() loaded service " + gmsImpl.getClass().getName());
				}
			}
		} catch (Throwable x) {
			LOG.log(Level.SEVERE, "factory.classload.failed");
			LOG.log(Level.SEVERE, "stack trace", x);
		}
		return gmsImpl;
	}

	public static GroupManagementService getGroupManagementServiceInstance() {
		GroupManagementService gmsImpl = null;
		try {
			gmsImpl = findByServiceLoader();
		} catch (Throwable t) {
			// jdk 5 will end up here. Not a reportable error.
		}
		if (gmsImpl == null) {
			String classname = "com.sun.enterprise.ee.cms.impl.common.GroupManagementServiceImpl";
			gmsImpl = findByClassLoader(classname);
		}
		return gmsImpl;
	}

}
