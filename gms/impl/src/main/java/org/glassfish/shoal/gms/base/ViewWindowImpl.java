/*
 * Copyright (c) 1997, 2018 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020 Payara Services Ltd.
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

package org.glassfish.shoal.gms.base;

import java.io.Serializable;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.shoal.gms.api.core.DistributedStateCache;
import org.glassfish.shoal.gms.api.core.GMSCacheable;
import org.glassfish.shoal.gms.api.core.GMSConstants;
import org.glassfish.shoal.gms.api.core.GMSException;
import org.glassfish.shoal.gms.api.core.GMSMember;
import org.glassfish.shoal.gms.api.core.GroupManagementService;
import org.glassfish.shoal.gms.api.core.RejoinSubevent;
import org.glassfish.shoal.gms.api.core.Signal;
import org.glassfish.shoal.gms.api.spi.MemberStates;
import org.glassfish.shoal.gms.common.FailureNotificationSignalImpl;
import org.glassfish.shoal.gms.common.FailureRecoverySignalImpl;
import org.glassfish.shoal.gms.common.FailureSuspectedSignalImpl;
import org.glassfish.shoal.gms.common.GMSContext;
import org.glassfish.shoal.gms.common.GMSContextFactory;
import org.glassfish.shoal.gms.common.GroupLeadershipNotificationSignalImpl;
import org.glassfish.shoal.gms.common.JoinNotificationSignalImpl;
import org.glassfish.shoal.gms.common.JoinedAndReadyNotificationSignalImpl;
import org.glassfish.shoal.gms.common.PlannedShutdownSignalImpl;
import org.glassfish.shoal.gms.common.RecoveryTargetSelector;
import org.glassfish.shoal.gms.common.Router;
import org.glassfish.shoal.gms.common.SignalPacket;
import org.glassfish.shoal.gms.common.ViewWindow;
import org.glassfish.shoal.gms.logging.GMSLogDomain;
import org.glassfish.shoal.gms.mgmt.ClusterView;
import org.glassfish.shoal.gms.mgmt.ClusterViewEvents;

import static org.glassfish.shoal.gms.api.core.GMSConstants.startupType.GROUP_STARTUP;
import static org.glassfish.shoal.gms.api.core.GMSConstants.startupType.INSTANCE_STARTUP;

/**
 * @author Shreedhar Ganapathy Date: Jun 26, 2006
 * @version $Revision$
 */
class ViewWindowImpl implements ViewWindow, Runnable {
    private GMSContext ctx;
    static private final Logger logger = GMSLogDomain.getLogger(GMSLogDomain.GMS_LOGGER);
    static private final ResourceBundle gmsRb = logger.getResourceBundle();
    private static final int MAX_VIEWS = 100; // 100 is some default.
    private static final List<GMSMember> EMPTY_GMS_MEMBER_LIST = new ArrayList<GMSMember>();
    private final List<List<GMSMember>> views = new Vector<List<GMSMember>>();
    private List<Signal> signals = new Vector<Signal>();
    private final List<String> currentCoreMembers = new ArrayList<String>();
    private final List<String> allCurrentMembers = new ArrayList<String>();
    private static final String CORETYPE = "CORE";
    // This is for DSC cache syncups so that member details are locally available
    // to GMS clients when they ask for it with the Join signals.
    private static final int SYNCWAITMILLIS = 3000;
    private static final String REC_PROGRESS_STATE = GroupManagementService.RECOVERY_STATE.RECOVERY_IN_PROGRESS.toString();
    private static final String REC_APPOINTED_STATE = GroupManagementService.RECOVERY_STATE.RECOVERY_SERVER_APPOINTED.toString();
    private final ArrayBlockingQueue<EventPacket> viewQueue;
    private final String groupName;
    private ConcurrentHashMap<String, MemberStates> pendingGroupJoins = new ConcurrentHashMap<String, MemberStates>();

    ViewWindowImpl(final String groupName, final ArrayBlockingQueue<EventPacket> viewQueue) {
        this.groupName = groupName;
        this.viewQueue = viewQueue;
    }

    private GMSContext getGMSContext() {
        if (ctx == null) {
            ctx = GMSContextFactory.getGMSContext(groupName);
        }
        return ctx;
    }

    public void setPendingGroupJoins(Set<String> memberTokens) {
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("setPendingGroupJoins: members:" + memberTokens);
        }
        for (String member : memberTokens) {
            pendingGroupJoins.put(member, MemberStates.UNKNOWN);
        }
    }

    public boolean isGroupStartup(String member) {
        boolean result = false;
        MemberStates state = null;
        if (member != null) {
            state = pendingGroupJoins.get(member);
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("pendingGroupJoins member:" + member + " state=" + state);
            }
            if (state != null) {
                switch (state) {
                case UNKNOWN:
                case ALIVE:
                    result = true;
                    break;
                default:
                    result = false;
                    break;
                }
            }
        }
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("isGroupStartup: member: " + member + " result:" + result + " isGroupStartupComplete:" + isGroupStartupComplete());
        }
        return result;
    }

    public boolean setGroupStartupState(String member, MemberStates state) {
        boolean result = false;
        switch (state) {
        case READY:
        case ALIVEANDREADY:
            Object value = pendingGroupJoins.remove(member);
            result = value != null;
            break;
        case ALIVE:
            result = pendingGroupJoins.replace(member, MemberStates.UNKNOWN, MemberStates.ALIVE);
            break;
        default:
            break;
        }
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("setGroupStartupState: member: " + member + " newState:" + state + " result:" + result + " isGroupStartupComplete:"
                    + isGroupStartupComplete() + " pendingMembers:" + pendingGroupJoins.keySet().toString());
        }
        return result;
    }

    public boolean isGroupStartupComplete() {
        boolean result = pendingGroupJoins.size() == 0;
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("isGroupStartupComplete: result=" + result + " pendingJoinedAndReadys=" + pendingGroupJoins.size());
        }
        return result;
    }

    public void run() {
        boolean alreadyLogged = false;
        while (!getGMSContext().isShuttingDown()) {
            EventPacket packet = null;
            try {
                int vqSize = viewQueue.size();
                if (vqSize > 0) {
                    // todo: make Level.FINE before final release
                    logger.log(Level.FINE, "viewQueue size before take " + vqSize + " for group: " + groupName);
                }
                packet = viewQueue.take();
                if (packet != null) {
                    logger.log(Level.FINE, "ViewWindow : processing a received view " + packet.getClusterViewEvent() + " for group:" + groupName);
                    newViewObserved(packet);
                    alreadyLogged = false;
                } else {
                    if (!alreadyLogged && logger.isLoggable(Level.FINER)) {
                        logger.finer("viewQueue poll timeout after 30 seconds for group: " + groupName);
                        alreadyLogged = true;
                    }
                }
            } catch (InterruptedException e) {
                logger.log(Level.FINEST, e.getLocalizedMessage());
            } catch (Throwable t) {
                final String packetInfo = (packet == null ? "<null>" : packet.toString());
                logger.log(Level.WARNING, "view.window.eventhandler.exception", new Object[] { packetInfo });
                logger.log(Level.WARNING, "stack trace", t);
            }
        }
        logger.log(Level.INFO, "view.window.thread.terminated", new Object[] { groupName });
    }

    private void newViewObserved(final EventPacket packet) {
        final GMSMember member = Utility.getGMSMember(packet.getSystemAdvertisement());
        synchronized (views) {
            views.add(Collections.unmodifiableList(getMemberTokens(packet)));
            if (views.size() > MAX_VIEWS) {
                views.remove(0);
            }
            logger.log(Level.INFO, "membership.snapshot.analysis",
                    new Object[] { packet.getClusterViewEvent().toString(), member.getMemberToken(), member.getGroupName() });
            Signal[] activeSignals = analyzeViewChange(packet);

            if (activeSignals.length != 0) {
                getGMSContext().getRouter().queueSignals(new SignalPacket(activeSignals));
            }
        }
    }

    private ArrayList<GMSMember> getMemberTokens(final EventPacket packet) {
        final List<GMSMember> tokens = new ArrayList<GMSMember>(); // contain list of GMSMember objects.
        final StringBuilder sb = new StringBuilder(100);

        // NOTE: always synchronize currentCoreMembers and allCurrentMembers in this order when getting both locks at same time.
        synchronized (currentCoreMembers) {
            synchronized (allCurrentMembers) {
                currentCoreMembers.clear();
                allCurrentMembers.clear();
                ClusterView view = packet.getClusterView();
                GMSMember member;
                SystemAdvertisement advert;
                int count = 0;
                for (SystemAdvertisement systemAdvertisement : view.getView()) {
                    advert = systemAdvertisement;
                    member = Utility.getGMSMember(advert);
                    member.setSnapShotId(view.getClusterViewId());
                    sb.append(++count).append(": MemberId: ").append(member.getMemberToken()).append(", MemberType: ").append(member.getMemberType())
                            .append(", Address: ").append(advert.getID().toString()).append('\n');
                    if (member.getMemberType().equals(CORETYPE)) {
                        currentCoreMembers.add(new StringBuilder(member.getMemberToken()).append("::").append(member.getStartTime()).toString());
                    }
                    tokens.add(member);
                    allCurrentMembers.add(new StringBuilder().append(member.getMemberToken()).append("::").append(member.getStartTime()).toString());
                }
            }
        }
        logger.log(Level.INFO, "view.window.view.change", new Object[] { groupName, packet.getClusterViewEvent().toString(), sb.toString() });
        return (ArrayList<GMSMember>) tokens;
    }

    private Signal[] analyzeViewChange(final EventPacket packet) {
        ((Vector) signals).removeAllElements();
        final ClusterViewEvents events = packet.getClusterViewEvent();
        switch (events) {
        case ADD_EVENT:
        case NO_LONGER_INDOUBT_EVENT:
            addNewMemberJoins(packet);
            break;
        case CLUSTER_STOP_EVENT:
            addPlannedShutdownSignals(packet);
            break;
        case FAILURE_EVENT:
            addFailureSignals(packet);
            break;
        case IN_DOUBT_EVENT:
            addInDoubtMemberSignals(packet);
            break;
        case JOINED_AND_READY_EVENT:
            addReadyMembers(packet);
            break;
        case MASTER_CHANGE_EVENT:
            analyzeMasterChangeView(packet);
            break;
        case PEER_STOP_EVENT:
            addPlannedShutdownSignals(packet);
            break;
        default:
        }

        final Signal[] s = new Signal[signals.size()];
        return signals.toArray(s);
    }

    private void analyzeMasterChangeView(final EventPacket packet) {
        final SystemAdvertisement advert = packet.getSystemAdvertisement();
        final GMSMember member = Utility.getGMSMember(advert);
        final String token = member.getMemberToken();
        if (!this.getGMSContext().isWatchdog()) {
            addGroupLeadershipNotificationSignal(token, member.getGroupName(), member.getStartTime());
        }
        if (views.size() == 1 && !getGMSContext().getGroupCommunicationProvider().isDiscoveryInProgress()) { // views list only contains 1 view which is assumed
                                                                                                             // to be the 1st view.
            addNewMemberJoins(packet);
        }
        if (views.size() > 1 && packet.getClusterView().getSize() != getPreviousView().size()) {
            determineAndAddNewMemberJoins();
        }
    }

    private void determineAndAddNewMemberJoins() {
        final List<GMSMember> newMembership = getCurrentView();
        String token;
        if (views.size() == 1) {
            if (newMembership.size() > 1) {
                for (GMSMember member : newMembership) {
                    token = member.getMemberToken();
                    if (!token.equals(getGMSContext().getServerIdentityToken())) {
                        syncDSC(token);
                    }
                    if (member.getMemberType().equalsIgnoreCase(CORETYPE)) {
                        addJoinNotificationSignal(token, member.getGroupName(), member.getStartTime());
                    }
                }
            }
        } else if (views.size() > 1) {
            final List<String> oldMembers = getTokens(getPreviousView());
            for (GMSMember member : newMembership) {
                token = member.getMemberToken();
                if (!oldMembers.contains(token)) {
                    syncDSC(token);
                    if (member.getMemberType().equalsIgnoreCase(CORETYPE)) {
                        addJoinNotificationSignal(token, member.getGroupName(), member.getStartTime());
                    }
                }
            }
        }
    }

    private List<String> getTokens(final List<GMSMember> oldMembers) {
        final List<String> tokens = new ArrayList<String>();
        for (GMSMember member : oldMembers) {
            tokens.add(member.getMemberToken());
        }
        return tokens;
    }

    private void addPlannedShutdownSignals(final EventPacket packet) {
        final SystemAdvertisement advert = packet.getSystemAdvertisement();
        final String token = advert.getName();
        final DistributedStateCache dsc = getGMSContext().getDistributedStateCache();
        final GMSConstants.shutdownType shutdownType;
        if (packet.getClusterViewEvent().equals(ClusterViewEvents.CLUSTER_STOP_EVENT)) {
            shutdownType = GMSConstants.shutdownType.GROUP_SHUTDOWN;
        } else {
            shutdownType = GMSConstants.shutdownType.INSTANCE_SHUTDOWN;
            if (dsc != null) {
                dsc.removeAllForMember(token);
            }
        }
        logger.log(Level.INFO, "plannedshutdownevent.announcement", new Object[] { token, shutdownType, groupName });
        String gName = Utility.getGroupName(advert);
        if (gName == null) {
            logger.log(Level.WARNING, "systemadv.not.contain.customtag", CustomTagNames.GROUP_NAME);
            return;
        }
        long startTime = Utility.getStartTime(advert);
        if (startTime == Utility.NO_SUCH_TIME) {
            logger.log(Level.WARNING, "systemadv.not.contain.customtag", CustomTagNames.START_TIME);
            return;
        }
        signals.add(new PlannedShutdownSignalImpl(token, gName, startTime, shutdownType));
    }

    private void addInDoubtMemberSignals(final EventPacket packet) {
        final SystemAdvertisement advert = packet.getSystemAdvertisement();
        final GMSMember member = Utility.getGMSMember(advert);
        final String token = member.getMemberToken();
        getGMSContext().addToSuspectList(token);
        logger.log(Level.INFO, "gms.failureSuspectedEventReceived", new Object[] { token, groupName });
        signals.add(new FailureSuspectedSignalImpl(token, member.getGroupName(), member.getStartTime()));
    }

    private void addFailureSignals(final EventPacket packet) {
        final SystemAdvertisement advert = packet.getSystemAdvertisement();
        final GMSMember member = Utility.getGMSMember(advert);
        final String failedMember = member.getMemberToken();
        if (member.getMemberType().equalsIgnoreCase(CORETYPE)) {
            List<GMSMember> previousView = getPreviousViewContaining(failedMember);
            logger.log(Level.INFO, "member.failed", new Object[] { failedMember, member.getGroupName() });
            generateFailureRecoverySignals(previousView, failedMember, member.getGroupName(), member.getStartTime());
            if (getGMSContext().getRouter().isFailureNotificationAFRegistered()) {
                signals.add(new FailureNotificationSignalImpl(failedMember, member.getGroupName(), member.getStartTime()));
            }
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("removing newly added node from the suspected list..." + failedMember);
            }
            getGMSContext().removeFromSuspectList(failedMember);
        }
    }

    /**
     * Best effort to find a view in past that contains <code>member</code>.
     *
     * Returns previous view if none of <code>MAX_VIEWS_IN_PAST</code> views contain member. MASTER_CHANGE_EVENTS can cause
     * a recently failed instance to not be in a view. This is a partial fix for shoal issue 83.
     *
     * @param member return a past view that contains member
     * @return a view containing member or just return previous view if non of MAX_VIEWS_IN_PAST contain member.
     */
    private List<GMSMember> getPreviousViewContaining(String member) {
        final int MAX_VIEWS_IN_PAST = 10;
        List<GMSMember> found = getPreviousView(); // may not contain member but better than returning null.
        for (int i = 2; ((i < (2 + MAX_VIEWS_IN_PAST)) && ((views.size() - i) >= 0)); i++) {
            List<GMSMember> current = views.get(views.size() - i);
            if (viewContains(current, member)) {
                found = current;
                break;
            }
        }
        return found;
    }

    static private boolean viewContains(List<GMSMember> view, String member) {
        boolean found = false;
        for (GMSMember current : view) {
            if (current.getMemberToken().compareTo(member) == 0) {
                found = true;
                break;
            }
        }
        return found;
    }

    private void generateFailureRecoverySignals(final List<GMSMember> oldMembership, final String token, final String groupName, final Long startTime) {

        final Router router = getGMSContext().getRouter();
        // if Recovery notification is registered then
        if (router.isFailureRecoveryAFRegistered()) {
            logger.log(Level.FINE, "Determining the recovery server..");
            // determine if we are recovery server
            if (RecoveryTargetSelector.resolveRecoveryTarget(null, oldMembership, token, getGMSContext())) {
                // this is a list containing failed members who were in the
                // process of being recovered.i.e. state was RECOVERY_IN_PROGRESS
                final List<String> recInProgressMembers = getRecoveriesInProgressByFailedMember(token);
                // this is a list of failed members (who are still dead)
                // for whom the failed member here was appointed as recovery
                // server.
                final List<String> recApptsHeldByFailedMember = getRecApptsHeldByFailedMember(token);
                for (final String comp : router.getFailureRecoveryComponents()) {
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, new StringBuilder("adding failure recovery signal for component=").append(comp).toString());
                    }
                    signals.add(new FailureRecoverySignalImpl(comp, token, groupName, startTime));
                    if (!recInProgressMembers.isEmpty()) {
                        for (final String fToken : recInProgressMembers) {
                            signals.add(new FailureRecoverySignalImpl(comp, fToken, groupName, 0));
                        }
                    }
                    if (!recApptsHeldByFailedMember.isEmpty()) {
                        for (final String fToken : recApptsHeldByFailedMember) {
                            signals.add(new FailureRecoverySignalImpl(comp, fToken, groupName, 0));
                        }
                    }
                }
            }
        }
    }

    private List<String> getRecApptsHeldByFailedMember(final String token) {
        final List<String> tokens = new ArrayList<String>();
        final DistributedStateCache dsc = getGMSContext().getDistributedStateCache();
        final Map<GMSCacheable, Object> entries = dsc.getFromCache(token);
        for (Map.Entry<GMSCacheable, Object> entry : entries.entrySet()) {
            GMSCacheable gmsCacheable = entry.getKey();
            // if this failed member was appointed for recovering someone else
            if (token.equals(gmsCacheable.getMemberTokenId()) && !token.equals(gmsCacheable.getKey())) {
                if (entry.getValue() instanceof String) {
                    if (((String) entry.getValue()).startsWith(REC_APPOINTED_STATE) && !currentCoreMembers.contains(gmsCacheable.getKey())) {
                        if (logger.isLoggable(Level.FINER)) {
                            // if the target member is already up dont include that
                            logger.log(Level.FINER,
                                    new StringBuilder("Failed Member ").append(token).append(" was appointed for recovery of ").append(gmsCacheable.getKey())
                                            .append(" when ").append(token).append(" failed. ").append("Adding to recovery-appointed list...").toString());
                        }
                        tokens.add((String) gmsCacheable.getKey());
                        try {
                            dsc.removeFromCache(gmsCacheable.getComponentName(), gmsCacheable.getMemberTokenId(), (Serializable) gmsCacheable.getKey());
                            RecoveryTargetSelector.setRecoverySelectionState(getGMSContext().getServerIdentityToken(), (String) gmsCacheable.getKey(),
                                    getGMSContext().getGroupName());
                        } catch (GMSException e) {
                            logger.log(Level.INFO, e.getLocalizedMessage(), e);
                        }
                    }
                }
            }
        }
        return tokens;
    }

    private List<String> getRecoveriesInProgressByFailedMember(final String token) {
        final List<String> tokens = new ArrayList<String>();
        final DistributedStateCache dsc = getGMSContext().getDistributedStateCache();
        final Map<GMSCacheable, Object> entries = dsc.getFromCache(token);

        for (Map.Entry<GMSCacheable, Object> entry : entries.entrySet()) {
            GMSCacheable gmsCacheable = entry.getKey();
            // if this member is recovering someone else
            if (token.equals(gmsCacheable.getMemberTokenId()) && !token.equals(gmsCacheable.getKey())) {
                if (entry.getValue() instanceof String) {
                    if (((String) entry.getValue()).startsWith(REC_PROGRESS_STATE)) {
                        if (logger.isLoggable(Level.FINER)) {
                            logger.log(Level.FINER, new StringBuilder("Failed Member ").append(token).append(" had recovery-in-progress for ")
                                    .append(gmsCacheable.getKey()).append(" when ").append(token).append(" failed. ").toString());
                        }
                        tokens.add((String) gmsCacheable.getKey());
                        RecoveryTargetSelector.setRecoverySelectionState(getGMSContext().getServerIdentityToken(), (String) gmsCacheable.getKey(),
                                getGMSContext().getGroupName());
                    }
                }
            }
        }
        return tokens;
    }

    private void addNewMemberJoins(final EventPacket packet) {
        final SystemAdvertisement advert = packet.getSystemAdvertisement();
        final GMSMember member = Utility.getGMSMember(advert);
        final String token = member.getMemberToken();
        final List<String> oldMembers = getTokens(getPreviousView());

        RejoinSubevent rjse = getGMSContext().getInstanceRejoins().get(packet.getSystemAdvertisement().getName());
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE,
                    "addNewMemberJoins: member: " + member + " joined group time:" + new Date(Utility.getStartTime(advert)) + " rejoin subevent=" + rjse);
        }

        // Series of checks needed to avoid duplicate ADD messages.
        // This conditional was added to avoid duplicate ADD events caused
        // by GroupLeaderShip change notifications.
        // The coordinator handles ADD event differently than all other members.
        // Lastly, this instance is always added to view so let ADD event through w/o check for this instance.
        if (isCoordinator() || !oldMembers.contains(token) || rjse != null || token.compareTo(getGMSContext().getServerIdentityToken()) == 0) {
            if (packet.getClusterView().getSize() > 1) {
                // TODO: Figure out a better way to sync
                syncDSC(advert.getID());
            }
            if (member.isCore()) {
                addJoinNotificationSignal(token, member.getGroupName(), member.getStartTime());
            }
        }
    }

    private void addReadyMembers(final EventPacket packet) {
        final SystemAdvertisement advert = packet.getSystemAdvertisement();
        final String token = advert.getName();
        final GMSMember member = Utility.getGMSMember(advert);

        if (member.isCore()) {
            addJoinedAndReadyNotificationSignal(token, member.getGroupName(), member.getStartTime());
        }
    }

    private void addJoinedAndReadyNotificationSignal(final String token, final String groupName, final long startTime) {
        String rejoinTxt = "";
        RejoinSubevent rse = ctx.getInstanceRejoins().get(token);
        if (rse != null) {
            rejoinTxt = gmsRb.getString("viewwindow.rejoining") + " " + rse.toString();
            if (logger.isLoggable(Level.FINE)) {
                logger.fine(String.format("addJoinedAndReadyNotificationSignal setting rejoin subevent for token '%s'", token));
            }
        }
        final GMSConstants.startupType startupState = isGroupStartup(token) ? GROUP_STARTUP : INSTANCE_STARTUP;
        String msg = MessageFormat.format(gmsRb.getString("viewwindow.adding.joined.ready.member"),
                new Object[] { token, groupName, startupState.toString(), rejoinTxt });
        logger.log(Level.INFO, msg);
        JoinedAndReadyNotificationSignalImpl jarSignal = new JoinedAndReadyNotificationSignalImpl(token, getCurrentCoreMembers(), getAllCurrentMembers(),
                groupName, startTime, startupState, rse);
        signals.add(jarSignal);
    }

    private void addJoinNotificationSignal(final String token, final String groupName, final long startTime) {
        String rejoinTxt = "";
        RejoinSubevent rse = ctx.getInstanceRejoins().get(token);
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "addJoinNotificationSignal member:" + token + " RejoinSubevent:" + rse);
        }
        if (rse != null) {
            rejoinTxt = MessageFormat.format(gmsRb.getString("viewwindow.rejoining"), rse.toString());
            if (logger.isLoggable(Level.FINE)) {
                logger.fine(String.format("addJoinNotificationSignal setting rejoin subevent for token '%s'", token));
            }
        }
        final GMSConstants.startupType startupState = isGroupStartup(token) ? GROUP_STARTUP : INSTANCE_STARTUP;
        setGroupStartupState(token, MemberStates.ALIVE);
        String msg = MessageFormat.format(gmsRb.getString("viewwindow.adding.join.member"),
                new Object[] { token, groupName, startupState.toString(), rejoinTxt });
        logger.log(Level.INFO, msg);
        JoinNotificationSignalImpl jnSignal = new JoinNotificationSignalImpl(token, getCurrentCoreMembers(), getAllCurrentMembers(), groupName, startTime,
                startupState, rse);
        signals.add(jnSignal);
    }

    private void addGroupLeadershipNotificationSignal(final String token, final String groupName, final long startTime) {
        logger.log(Level.INFO, "view.window.groupleader.notify", new Object[] { token, groupName });
        signals.add(new GroupLeadershipNotificationSignalImpl(token, getPreviousView(), getCurrentView(), getCurrentCoreMembers(), getAllCurrentMembers(),
                groupName, startTime));
    }

    private void syncDSC(final String token) {
        final DistributedStateCacheImpl dsc;
        // if coordinator, call dsc to sync with this member
        if (isCoordinator()) {
            logger.log(Level.FINE, "I am coordinator, performing sync ops on " + token);
            try {
                dsc = (DistributedStateCacheImpl) getGMSContext().getDistributedStateCache();
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, "got DSC ref " + dsc.toString());
                }

                // this sleep() gives the new remote member some time to receive
                // this same view change before we ask it to sync with us.
                Thread.sleep(SYNCWAITMILLIS);
                logger.log(Level.FINER, "Syncing...");
                dsc.syncCache(token, true);
                logger.log(Level.FINER, "Sync request sent..");
            } catch (GMSException e) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, "GMSException during DSC sync " + e.getLocalizedMessage(), e);
                }
            } catch (InterruptedException e) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, e.getLocalizedMessage(), e);
                }
            } catch (Exception e) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, "Exception during DSC sync:" + e, e);
                }
            }
        }
    }

    private void syncDSC(final PeerID peerid) {
        final DistributedStateCacheImpl dsc;
        // if coordinator, call dsc to sync with this member
        if (isCoordinator()) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "I am coordinator, performing sync ops on member:" + peerid.getInstanceName() + " peerid:" + peerid);
            }
            try {
                dsc = (DistributedStateCacheImpl) getGMSContext().getDistributedStateCache();
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, "got DSC ref " + dsc.toString());
                }

                // this sleep() gives the new remote member some time to receive
                // this same view change before we ask it to sync with us.
                Thread.sleep(SYNCWAITMILLIS);
                logger.log(Level.FINER, "Syncing...");
                dsc.syncCache(peerid, true);
                logger.log(Level.FINER, "Sync request sent..");
            } catch (GMSException e) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, "GMSException during DSC sync " + e.getLocalizedMessage(), e);
                }
            } catch (InterruptedException e) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, e.getLocalizedMessage(), e);
                }
            } catch (Exception e) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, "Exception during DSC sync:" + e, e);
                }
            }
        }
    }

    public boolean isCoordinator() {
        return getGMSContext().getGroupCommunicationProvider().isGroupLeader();
    }

    public List<GMSMember> getPreviousView() {
        List<GMSMember> result = EMPTY_GMS_MEMBER_LIST;
        synchronized (views) {
            final int INDEX = views.size() - 2;
            if (INDEX >= 0) {
                result = views.get(INDEX);
            }
        }
        return result;
    }

    public List<GMSMember> getCurrentView() {
        List<GMSMember> result = EMPTY_GMS_MEMBER_LIST;
        synchronized (views) {
            final int INDEX = views.size() - 1;
            if (INDEX >= 0) {
                result = views.get(INDEX);
            }
        }
        return result;
    }

    public List<String> getCurrentCoreMembers() {
        final List<String> retVal = new ArrayList<String>();
        synchronized (currentCoreMembers) {
            for (String member : currentCoreMembers) {
                member = member.substring(0, member.indexOf("::"));
                retVal.add(member);
            }
        }
        return retVal;
    }

    public List<String> getAllCurrentMembers() {
        final List<String> retVal = new ArrayList<String>();
        synchronized (allCurrentMembers) {
            for (String member : allCurrentMembers) {
                member = member.substring(0, member.indexOf("::"));
                retVal.add(member);
            }
        }
        return retVal;
    }

    public List<String> getCurrentCoreMembersWithStartTimes() {
        List<String> ret = new ArrayList<String>();
        synchronized (currentCoreMembers) {
            ret.addAll(currentCoreMembers);
        }
        return ret;
    }

    public List<String> getAllCurrentMembersWithStartTimes() {
        List<String> ret = new ArrayList<String>();
        synchronized (allCurrentMembers) {
            ret.addAll(allCurrentMembers);
        }
        return ret;
    }
}
