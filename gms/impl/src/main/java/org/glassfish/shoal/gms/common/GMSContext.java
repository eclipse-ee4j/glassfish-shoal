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

package org.glassfish.shoal.gms.common;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.glassfish.shoal.gms.api.core.AliveAndReadyView;
import org.glassfish.shoal.gms.api.core.DistributedStateCache;
import org.glassfish.shoal.gms.api.core.GMSConstants;
import org.glassfish.shoal.gms.api.core.GMSException;
import org.glassfish.shoal.gms.api.core.GroupHandle;
import org.glassfish.shoal.gms.api.core.GroupManagementService;
import org.glassfish.shoal.gms.api.core.RejoinSubevent;
import org.glassfish.shoal.gms.api.spi.GroupCommunicationProvider;
import org.glassfish.shoal.gms.api.spi.MemberStates;

/**
 * Provides contextual information about all useful GMS artifacts. These are GMS objects that are tied to a particular
 * group identity and thus scoped to provide information within the group's context. There can be as many GMSContext
 * objects as there are groups within a single JVM process.
 *
 * @author Shreedhar Ganapathy Date: Jan 12, 2004
 * @version $Revision$
 */
public interface GMSContext {
    /**
     * returns the serverIdentityToken pertaining to the process that owns this GMS instance
     *
     * @return java.lang.String
     */
    String getServerIdentityToken();

    /**
     * returns the name of the group this context represents.
     *
     * @return the name of the group.
     */
    String getGroupName();

    /**
     * returns Group handle
     *
     * @return Group handle
     */
    GroupHandle getGroupHandle();

    /**
     * returns the router
     *
     * @return router
     */
    Router getRouter();

    ViewWindow getViewWindow();

    DistributedStateCache getDistributedStateCache();

    GMSMonitor getGMSMonitor();

    void join() throws GMSException;

    void leave(final GMSConstants.shutdownType shutdownType);

    boolean isShuttingDown();

    long getStartTime();

    void announceGroupStartup(final String groupName, final GMSConstants.groupStartupState startupState, final List<String> memberTokens);

    void announceGroupShutdown(final String groupName, final GMSConstants.shutdownState shutdownState);

    boolean addToSuspectList(final String token);

    void removeFromSuspectList(final String token);

    boolean isSuspected(final String token);

    List<String> getSuspectList();

    ShutdownHelper getShutdownHelper();

    GroupCommunicationProvider getGroupCommunicationProvider();

    /**
     * lets this instance become a group leader explicitly Typically this can be employed by an administrative member to
     * become a group leader prior to shutting down a group of members simultaneously.
     *
     * For underlying Group Communication Providers who don't support the feature of a explicit leader role assumption, the
     * implementation of this method would be a no-op.
     */
    void assumeGroupLeadership();

    boolean isGroupBeingShutdown(String groupName);

    boolean isGroupStartup();

    void setGroupStartup(boolean value);

    GroupManagementService.MemberType getMemberType();

    boolean isWatchdog();

    AliveAndReadyView getPreviousAliveAndReadyView();

    AliveAndReadyView getCurrentAliveAndReadyView();

    Map<String, RejoinSubevent> getInstanceRejoins();

    AliveAndReadyViewWindow getAliveAndReadyViewWindow();

    void setGroupStartupJoinMembers(Set<String> members);

    boolean isGroupStartupComplete();

    boolean setGroupStartupState(String member, MemberStates state);
}
