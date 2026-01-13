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

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.shoal.gms.api.core.AliveAndReadyView;
import org.glassfish.shoal.gms.api.core.DistributedStateCache;
import org.glassfish.shoal.gms.api.core.GMSConstants;
import org.glassfish.shoal.gms.api.core.JoinedAndReadyNotificationSignal;
import org.glassfish.shoal.gms.api.core.RejoinSubevent;
import org.glassfish.shoal.gms.api.core.SignalAcquireException;
import org.glassfish.shoal.gms.api.core.SignalReleaseException;
import org.glassfish.shoal.gms.logging.GMSLogDomain;

/**
 * Implements JoinedAndReadyNotificationSignal
 *
 * @author Sheetal Vartak Date: 11/13/07
 */
public class JoinedAndReadyNotificationSignalImpl implements JoinedAndReadyNotificationSignal {

    private String memberToken;
    private String groupName;
    private List<String> currentCoreMembers;
    private List<String> allCurrentMembers;
    private static final String MEMBER_DETAILS = "MEMBERDETAILS";
    private GMSContext ctx;
    private GMSConstants.startupType startupKind;
    private long startTime;
    private RejoinSubevent rs;
    private AliveAndReadyView currentView = null;
    private AliveAndReadyView previousView = null;

    // Logging related stuff
    protected static final Logger logger = GMSLogDomain.getLogger(GMSLogDomain.GMS_LOGGER);

    void setStartupKind(GMSConstants.startupType startupKind) {
        this.startupKind = startupKind;
    }

    public JoinedAndReadyNotificationSignalImpl(final String memberToken, final List<String> currentCoreMembers, final List<String> allCurrentMembers,
            final String groupName, final long startTime, final GMSConstants.startupType startupKind, final RejoinSubevent rs) {
        this.memberToken = memberToken;
        this.currentCoreMembers = currentCoreMembers;
        this.allCurrentMembers = allCurrentMembers;
        this.groupName = groupName;
        this.startTime = startTime;
        this.startupKind = startupKind;
        this.rs = rs;

        if (logger.isLoggable(Level.FINE)) {
            logger.fine("JoinAndReadyNotificationSignalImpl ctor: member=" + memberToken + " group=" + groupName + " startupKind=" + startupKind.toString());
        }
    }

    JoinedAndReadyNotificationSignalImpl(final JoinedAndReadyNotificationSignal signal) {
        this(signal.getMemberToken(), signal.getCurrentCoreMembers(), signal.getAllCurrentMembers(), signal.getGroupName(), signal.getStartTime(),
                signal.getEventSubType(), signal.getRejoinSubevent());
        currentView = signal.getCurrentView();
        previousView = signal.getPreviousView();
    }

    /**
     * Signal is acquired prior to processing of the signal to protect group resources being acquired from being affected by
     * a race condition
     *
     * @throws org.glassfish.shoal.gms.api.core.SignalAcquireException Exception when unable to acquire the signal
     *
     */
    @Override
    public void acquire() throws SignalAcquireException {
        // TODO??
    }

    /**
     * Signal is released after processing of the signal to bring the group resources to a state of availability
     *
     * @throws org.glassfish.shoal.gms.api.core.SignalReleaseException Exception when unable to release the signal
     *
     */
    @Override
    public void release() throws SignalReleaseException {
        memberToken = null;
        currentCoreMembers = null;
        allCurrentMembers = null;
    }

    /**
     * returns the identity token of the member that caused this signal to be generated. For instance, in the case of a
     * MessageSignal, this member token would be the sender. In the case of a FailureNotificationSignal, this member token
     * would be the failed member. In the case of a JoinNotificationSignal or PlannedShutdownSignal, the member token would
     * be the member who joined or is being gracefully shutdown, respectively.
     */
    @Override
    public String getMemberToken() {
        return memberToken;
    }

    @Override
    public List<String> getCurrentCoreMembers() {
        return currentCoreMembers;
    }

    @Override
    public List<String> getAllCurrentMembers() {
        return allCurrentMembers;
    }

    /**
     * returns the details of the member who caused this Signal to be generated returns a Map containing key-value pairs
     * constituting data pertaining to the member's details
     *
     * @return Map &lt;Serializable, Serializable&gt;
     */
    @Override
    public Map<Serializable, Serializable> getMemberDetails() {
        Map<Serializable, Serializable> ret = new HashMap<Serializable, Serializable>();
        if (ctx == null) {
            ctx = GMSContextFactory.getGMSContext(groupName);
        }
        DistributedStateCache dsc = ctx.getDistributedStateCache();
        if (dsc != null) {
            ret = dsc.getFromCacheForPattern(MEMBER_DETAILS, memberToken);
        } else {
            logger.log(Level.WARNING, "no.instance.dsc", new Object[] { memberToken });
        }
        return ret;
    }

    /**
     * returns the group to which the member involved in the Signal belonged to
     *
     * @return String
     */
    @Override
    public String getGroupName() {
        return groupName;
    }

    @Override
    public long getStartTime() {
        return startTime;
    }

    @Override
    public GMSConstants.startupType getEventSubType() {
        return startupKind;
    }

    @Override
    public RejoinSubevent getRejoinSubevent() {
        return rs;
    }

    @Override
    public AliveAndReadyView getCurrentView() {
        return currentView;
    }

    @Override
    public AliveAndReadyView getPreviousView() {
        return previousView;
    }

    void setCurrentView(AliveAndReadyView current) {
        currentView = current;
    }

    void setPreviousView(AliveAndReadyView previous) {
        previousView = previous;
    }

}
