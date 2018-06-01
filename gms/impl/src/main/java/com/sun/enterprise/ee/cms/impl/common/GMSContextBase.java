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

import com.sun.enterprise.ee.cms.core.GroupManagementService;
import com.sun.enterprise.ee.cms.logging.GMSLogDomain;
import com.sun.enterprise.ee.cms.core.GMSMember;

import java.util.ArrayList;
import java.util.logging.Logger;

/**
 * @author Shreedhar Ganapathy
 *         Date: Jan 31, 2006
 * @version $Revision$
 */
public abstract class GMSContextBase implements GMSContext {
    protected String serverToken = null;
    protected String groupName = null;
    protected Router router;
    protected ViewWindow viewWindow;
    protected static final Logger logger = GMSLogDomain.getLogger(GMSLogDomain.GMS_LOGGER);
    protected String memberType;
    protected GMSMember gmsMember;
    protected final ArrayList<String> suspectList;
    protected final Long startTime;
    protected boolean shuttingDown = false;
    protected final ShutdownHelper shutdownHelper;
    protected final GroupManagementService.MemberType gmsMemberType;

    protected GMSContextBase(final String serverToken, final String groupName,
                             final GroupManagementService.MemberType memberType) {
        this.serverToken = serverToken;
        this.groupName = groupName;
        this.gmsMemberType = memberType;
        this.memberType = getMemberType(memberType);
        startTime = System.currentTimeMillis();
        gmsMember = new GMSMember(serverToken, this.memberType, groupName,
                startTime);
        suspectList = new ArrayList<String>();
        shutdownHelper = new ShutdownHelper();
    }

    protected static String getMemberType(
            final GroupManagementService.MemberType memberType) {
        if (memberType == null)
            return GroupManagementService.MemberType.CORE.toString();
        else
            return memberType.toString();
    }

    public GroupManagementService.MemberType getMemberType() {
        return gmsMemberType;
    }

    /**
     * returns the serverIdentityToken pertaining to the process that
     * owns this GMS instance
     *
     * @return java.lang.String
     */
    public String getServerIdentityToken() {
        return serverToken;
    }

    /**
     * returns the name of the group this context represents
     */
    public String getGroupName() {
        return groupName;
    }

    /**
     * returns the router
     *
     * @return router
     */
    public Router getRouter() {
        return router;
    }

    protected abstract void createDistributedStateCache();

    /**
     * Return <code>true</code> if shutting down
     * @return <code>true</code> if shutting down
     */
    public boolean isShuttingDown() {
        return shuttingDown;
    }

    abstract public AliveAndReadyViewWindow  getAliveAndReadyViewWindow();

    abstract public GMSMonitor getGMSMonitor();
 }
