/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
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

package org.glassfish.shoal.gms.api.core;

import java.lang.System.Logger;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.Logger.Level.WARNING;

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
    private static final Logger LOG = System.getLogger(GMSFactory.class.getName());

    private static final Hashtable<String, GroupManagementService> GROUPS = new Hashtable<>();
    private static final Map<String, Boolean> GMS_ENABLED_MAP = new Hashtable<>();

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
            throw new RuntimeException("instance name was not specified and cannot be null");
        }
        if (groupName == null) {
            throw new RuntimeException("group name was not specified and cannot be null");
        }
        GroupManagementService gms;
        // if this method is called, GMS is enabled. It is assumed that
        // calling code made checks in configurations about the enablement
        // The recommended way for calling code for this purpose is to call the
        // setGMSEnabledState() method in this class(see below).
        GMS_ENABLED_MAP.put(groupName, Boolean.TRUE);
        try { // sanity check: if this group instance is
              // already created return that instance
            gms = getGMSModule(groupName);
        } catch (GMSException e) {
            gms = getGroupManagementServiceInstance();
            gms.initialize(serverToken, groupName, memberType, properties);
            memberToken = serverToken;
            GROUPS.put(getCompositeKey(groupName), gms);
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
            throw new GMSException("group name was not specified and cannot be null");
        }
        final String key = getCompositeKey(groupName);
        if (GROUPS.containsKey(key)) {
            return GROUPS.get(key);
        } else if (!isGMSEnabled(groupName)) {
            throw new GMSNotEnabledException("GMS not enabled for group " + groupName);
        } else {
            throw new GMSNotInitializedException("Group Management Service is not initialized for group " + groupName);
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
        if (instances.isEmpty()) {
            throw new GMSNotInitializedException("Group Management Service is not initialized for any group");
        }
        gms = (GroupManagementService) instances.toArray()[0];
        return gms;
    }

    /**
     * For the case where there are multiple GROUPS in which this process has become a member.
     *
     * @return Collection
     */
    public static Collection getAllGMSInstancesForMember() {
        return GROUPS.values();
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
        final Boolean val = GMS_ENABLED_MAP.get(groupName);
        return val != null && !Boolean.FALSE.equals(val);
    }

    /**
     * enables an initialization code in the Application layer to set GMS to be enabled or not based on the application's
     * configuration
     *
     * @param groupName Name of the group
     * @param value a Boolean value
     */
    public static void setGMSEnabledState(final String groupName, final Boolean value) {
        GMS_ENABLED_MAP.put(groupName, value);
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
            if (GROUPS.containsKey(key)) {
                GROUPS.remove(key);
            }
        }
    }

    private static GroupManagementService findByServiceLoader() {
        GroupManagementService groupManagementService = null;
        ServiceLoader<GroupManagementService> loader = ServiceLoader.load(GroupManagementService.class);
        Iterator<GroupManagementService> iter = loader.iterator();

        if (iter.hasNext()) {
            try {
                groupManagementService = iter.next().getClass().getConstructor().newInstance();
            } catch (Throwable t) {
                LOG.log(WARNING, "GMS2002: error instantiating GroupManagementService service", t);
            }
        }
        if (groupManagementService == null) {
            LOG.log(ERROR, "SMG2003: fatal error, no GroupManagementService implementations found");
        } else {
            LOG.log(DEBUG, "GMS2001: findByServiceLoader() loaded service {0}", groupManagementService.getClass());
        }
        return groupManagementService;
    }

    private static GroupManagementService findByClassLoader(String classname) {
        GroupManagementService gmsImpl = null;
        try {
            Class<?> gmsImplClass = Class.forName(classname);
            if (gmsImplClass == null) {
                LOG.log(ERROR, "factory.load.service.error");
            } else {
                gmsImpl = (GroupManagementService) gmsImplClass.getConstructor().newInstance();
                LOG.log(DEBUG, "findByClassLoader() loaded service " + gmsImpl.getClass());
            }
        } catch (Throwable x) {
            LOG.log(ERROR, "GMS2004: fatal error instantiating GroupManagementService service", x);
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
            String classname = "org.glassfish.shoal.gms.common.GroupManagementServiceImpl";
            gmsImpl = findByClassLoader(classname);
        }
        return gmsImpl;
    }

}
