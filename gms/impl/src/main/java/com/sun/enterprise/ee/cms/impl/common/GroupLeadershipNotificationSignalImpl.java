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

import com.sun.enterprise.ee.cms.core.*;
import com.sun.enterprise.ee.cms.core.GMSMember;
import com.sun.enterprise.ee.cms.logging.GMSLogDomain;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.io.Serializable;

/**
 * Implements GroupLeadershipNotificationSignal
 *
 * @author Bongjae Chang
 */
public class GroupLeadershipNotificationSignalImpl implements GroupLeadershipNotificationSignal {

    protected static final Logger logger = GMSLogDomain.getLogger( GMSLogDomain.GMS_LOGGER );

    private static final String MEMBER_DETAILS = "MEMBERDETAILS";
    private GMSContext ctx;

    private final String memberToken;
    private final String groupName;
    private final long startTime;
    private final List<GMSMember> previousView;
    private final List<GMSMember> currentView;
    private final List<String> currentCoreMembers;
    private final List<String> allCurrentMembers;

    public GroupLeadershipNotificationSignalImpl( final String memberToken,
                                                  final List<GMSMember> previousView,
                                                  final List<GMSMember> currentView,
                                                  final List<String> currentCoreMembers,
                                                  final List<String> allCurrentMembers,
                                                  final String groupName,
                                                  final long startTime ) {
        this.memberToken = memberToken;
        this.previousView = previousView;
        this.currentView = currentView;
        this.currentCoreMembers = currentCoreMembers;
        this.allCurrentMembers = allCurrentMembers;
        this.groupName = groupName;
        this.startTime = startTime;
        ctx = GMSContextFactory.getGMSContext( groupName );
    }

    GroupLeadershipNotificationSignalImpl( final GroupLeadershipNotificationSignal signal ) {
        this( signal.getMemberToken(),
              signal.getPreviousView(),
              signal.getCurrentView(),
              signal.getCurrentCoreMembers(),
              signal.getAllCurrentMembers(),
              signal.getGroupName(),
              signal.getStartTime() );
    }

    /**
     * {@inheritDoc}
     */
    public void acquire() throws SignalAcquireException {
    }

    /**
     * {@inheritDoc}
     */
    public void release() throws SignalReleaseException {
    }

    /**
     * {@inheritDoc}
     */
    public String getMemberToken() {
        return memberToken;
    }

    /**
     * {@inheritDoc}
     */
    public Map<Serializable, Serializable> getMemberDetails() {
        Map<Serializable, Serializable> ret = new HashMap<Serializable, Serializable>();
        if( ctx == null ) {
            ctx = GMSContextFactory.getGMSContext( groupName );
        }
        DistributedStateCache dsc = ctx.getDistributedStateCache();
        if( dsc != null ) {
            ret = dsc.getFromCacheForPattern( MEMBER_DETAILS, memberToken );
        } else {
            logger.log( Level.WARNING, "no.instance.dsc", new Object[]{ memberToken } );
        }
        return ret;
    }

    /**
     * {@inheritDoc}
     */
    public String getGroupName() {
        return groupName;
    }

    /**
     * {@inheritDoc}
     */
    public long getStartTime() {
        return startTime;
    }

    /**
     * {@inheritDoc}
     */
    public List<GMSMember> getPreviousView() {
        return previousView;
    }

    /**
     * {@inheritDoc}
     */
    public List<GMSMember> getCurrentView() {
        return currentView;
    }

    /**
     * {@inheritDoc}
     */
    public List<String> getCurrentCoreMembers() {
        return currentCoreMembers;
    }

    /**
     * {@inheritDoc}
     */
    public List<String> getAllCurrentMembers() {
        return allCurrentMembers;
    }
}
