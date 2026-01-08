/*
 * Copyright (c) 2010, 2018 Oracle and/or its affiliates. All rights reserved.
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

import java.util.LinkedList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.shoal.gms.api.core.AliveAndReadyView;
import org.glassfish.shoal.gms.api.core.CallBack;
import org.glassfish.shoal.gms.api.core.FailureNotificationSignal;
import org.glassfish.shoal.gms.api.core.GMSConstants;
import org.glassfish.shoal.gms.api.core.GroupHandle;
import org.glassfish.shoal.gms.api.core.JoinedAndReadyNotificationSignal;
import org.glassfish.shoal.gms.api.core.PlannedShutdownSignal;
import org.glassfish.shoal.gms.api.core.RejoinSubevent;
import org.glassfish.shoal.gms.api.core.Signal;
import org.glassfish.shoal.gms.api.spi.MemberStates;
import org.glassfish.shoal.gms.client.FailureNotificationActionFactoryImpl;
import org.glassfish.shoal.gms.client.JoinedAndReadyNotificationActionFactoryImpl;
import org.glassfish.shoal.gms.client.PlannedShutdownActionFactoryImpl;
import org.glassfish.shoal.gms.logging.GMSLogDomain;

public class AliveAndReadyViewWindow {
    protected static final Logger LOG = Logger.getLogger(GMSLogDomain.GMS_LOGGER + ".ready");

    static final long MIN_VIEW_DURATION = 1000; // 1 second
    private long MAX_CLUSTER_STARTTIME_DURATION_MS = 10000; // todo: revisit this constant of 10 seconds for cluster startup.

    static final int MAX_ALIVE_AND_READY_VIEWS = 5;
    private final List<AliveAndReadyView> aliveAndReadyView = new LinkedList<AliveAndReadyView>();
    private long viewId = 0;

    private JoinedAndReadyNotificationActionFactoryImpl joinedAndReadyActionFactory = null;
    private FailureNotificationActionFactoryImpl failureActionFactory = null;
    private PlannedShutdownActionFactoryImpl plannedShutdownFactory = null;

    final private JoinedAndReadyCallBack jrcallback;
    final private LeaveCallBack leaveCallback;

    private long simulatedStartClusterTime;
    private AtomicBoolean isSimulatedStartCluster = new AtomicBoolean(false);
    private final String currentInstanceName;

    // map from JoinedAndReady memberName to its DAS ready members
    private ConcurrentHashMap<String, SortedSet<String>> joinedAndReadySignalReadyList = new ConcurrentHashMap<String, SortedSet<String>>();

    private final GMSContext ctx;

    // set to Level.INFO to aid debugging.
    static private final Level TRACE_LEVEL = Level.FINE;

    public AliveAndReadyViewWindow(GMSContext ctx) {
        this.ctx = ctx;
        currentInstanceName = ctx.getServerIdentityToken();

        jrcallback = new JoinedAndReadyCallBack(ctx.getGroupHandle(), aliveAndReadyView);
        joinedAndReadyActionFactory = new JoinedAndReadyNotificationActionFactoryImpl(jrcallback);

        leaveCallback = new LeaveCallBack(ctx.getGroupHandle(), aliveAndReadyView);
        failureActionFactory = new FailureNotificationActionFactoryImpl(leaveCallback);
        plannedShutdownFactory = new PlannedShutdownActionFactoryImpl(leaveCallback);

        // initialize with a null initial previous and current views
        aliveAndReadyView.add(new AliveAndReadyViewImpl(new TreeSet<String>(), viewId++));
        aliveAndReadyView.add(new AliveAndReadyViewImpl(new TreeSet<String>(), viewId++));
    }

    // junit testing only - only scope to package access
    AliveAndReadyViewWindow() {
        ctx = null;
        jrcallback = new JoinedAndReadyCallBack(null, aliveAndReadyView);
        leaveCallback = new LeaveCallBack(null, aliveAndReadyView);
        currentInstanceName = null;

        // initialize with a null initial previous and current views
        aliveAndReadyView.add(new AliveAndReadyViewImpl(new TreeSet<String>(), viewId++));
        aliveAndReadyView.add(new AliveAndReadyViewImpl(new TreeSet<String>(), viewId++));
    }

    public void setStartClusterMaxDuration(long durationInMs) {
        MAX_CLUSTER_STARTTIME_DURATION_MS = durationInMs;
    }

    public void processNotification(Signal signal) {
        if (signal instanceof JoinedAndReadyNotificationSignal) {
            jrcallback.processNotification(signal);
        } else if (signal instanceof PlannedShutdownSignal || signal instanceof FailureNotificationSignal) {
            leaveCallback.processNotification(signal);
        }
    }

    public AliveAndReadyView getPreviousView() {
        AliveAndReadyView result = null;
        synchronized (aliveAndReadyView) {
            int size = aliveAndReadyView.size();
            assert (size > 2);
            if (size >= 2) {
                result = aliveAndReadyView.get(size - 2);
            } else if (size == 1) {
                result = aliveAndReadyView.get(0);
                if (LOG.isLoggable(TRACE_LEVEL)) {
                    LOG.log(TRACE_LEVEL, "getPreviousAliveAndReadyView() called and only a current view", result);
                }
            }
        }
        // return current view when previous join and ready had a short duration and looks like it was part of startup.
        if (LOG.isLoggable(TRACE_LEVEL)) {
            LOG.log(TRACE_LEVEL, "getPreviousAliveAndReadyView: returning " + result);
        }

        return result;
    }

    public AliveAndReadyView getCurrentView() {
        AliveAndReadyView result = null;
        synchronized (aliveAndReadyView) {
            int length = aliveAndReadyView.size();
            if (length > 0) {
                result = aliveAndReadyView.get(length - 1);
            }
        } // return current view when previous join and ready had a short duration and looks like it was part of startup.
        if (LOG.isLoggable(TRACE_LEVEL)) {
            LOG.log(TRACE_LEVEL, "getCurrentAliveAndReadyView: returning " + result);
        }
        return result;
    }

    private boolean isMySignal(Signal sig) {
        return currentInstanceName != null && currentInstanceName.equals(sig.getMemberToken());
    }

    private abstract class CommonCallBack implements CallBack {
        final protected List<AliveAndReadyView> aliveAndReadyView;
        final protected GroupHandle gh;

        public CommonCallBack(GroupHandle gh, List<AliveAndReadyView> aliveAndReadyViews) {
            this.aliveAndReadyView = aliveAndReadyViews;
            this.gh = gh;
        }

        public void add(Signal signal, SortedSet<String> members) {
            // complete the current view with signal indicating transition that makes this the previous view.
            AliveAndReadyViewImpl current = (AliveAndReadyViewImpl) getCurrentView();
            if (current != null) {
                current.setSignal(signal);
            }

            // create a new current view
            AliveAndReadyView arview = new AliveAndReadyViewImpl(members, viewId++);
            aliveAndReadyView.add(arview);
            if (aliveAndReadyView.size() > MAX_ALIVE_AND_READY_VIEWS) {
                aliveAndReadyView.remove(0);
            }
        }
    }

    private class LeaveCallBack extends CommonCallBack {

        public LeaveCallBack(GroupHandle gh, List<AliveAndReadyView> aliveAndReadyView) {
            super(gh, aliveAndReadyView);
        }

        public void processNotification(Signal signal) {
            if (signal instanceof PlannedShutdownSignal || signal instanceof FailureNotificationSignal) {
                synchronized (aliveAndReadyView) {

                    // only consider CORE members.
                    AliveAndReadyView current = getCurrentView();
                    if (current != null && current.getMembers().contains(signal.getMemberToken())) {
                        SortedSet<String> currentMembers = new TreeSet<String>(current.getMembers());
                        boolean result = currentMembers.remove(signal.getMemberToken());
                        assert (result);
                        add(signal, currentMembers);
                        if (signal instanceof PlannedShutdownSignalImpl) {
                            PlannedShutdownSignalImpl pssig = (PlannedShutdownSignalImpl) signal;
                            pssig.setCurrentView(getCurrentView());
                            pssig.setPreviousView(getPreviousView());
                        } else if (signal instanceof FailureNotificationSignalImpl) {
                            FailureNotificationSignalImpl fsig = (FailureNotificationSignalImpl) signal;
                            fsig.setCurrentView(getCurrentView());
                            fsig.setPreviousView(getPreviousView());
                        }
                    }
                }
            }
        }
    }

    private class JoinedAndReadyCallBack extends CommonCallBack {

        public JoinedAndReadyCallBack(GroupHandle gh, List<AliveAndReadyView> aliveAndReadyView) {
            super(gh, aliveAndReadyView);
        }

        // todo: currently allowing non-CORE JoinedAndReady to create a new view.
        // since it might have CORE members from DAS, may need to keep doing this.
        public void processNotification(Signal signal) {
            if (signal instanceof JoinedAndReadyNotificationSignal) {
                final JoinedAndReadyNotificationSignal jrns = (JoinedAndReadyNotificationSignal) signal;
                final RejoinSubevent rejoin = jrns.getRejoinSubevent();
                SortedSet<String> dasReadyMembers = joinedAndReadySignalReadyList.remove(jrns.getMemberToken());
                AliveAndReadyView current = null;
                synchronized (aliveAndReadyView) {
                    current = getCurrentView();
                    SortedSet<String> currentMembers = new TreeSet<String>(current.getMembers());
                    for (String member : jrns.getCurrentCoreMembers()) {
                        if (dasReadyMembers != null && dasReadyMembers.contains(member)) {
                            if (currentMembers.add(member)) {
                                if (ctx != null) {
                                    ctx.setGroupStartupState(member, MemberStates.ALIVEANDREADY);
                                }
                                if (LOG.isLoggable(TRACE_LEVEL)) {
                                    LOG.log(TRACE_LEVEL, "das ready member: " + member + " added");
                                }
                            }
                        } else if (jrns.getMemberToken().equals(member)) {
                            currentMembers.add(member);
                            if (ctx != null) {
                                ctx.setGroupStartupState(member, MemberStates.ALIVEANDREADY);
                            }
                        }
                    }
                    add(signal, currentMembers);

                    // current is now previous view after the add above.
                    // while still holding synchronize block,
                    // check if this is transition from GROUPSTARTUP to cluster is all started.
                    // if so, set MemberList to empty list in previous view.
                    if (jrns.getEventSubType() == GMSConstants.startupType.GROUP_STARTUP) {
                        if (ctx != null) {
                            ctx.setGroupStartupState(signal.getMemberToken(), MemberStates.ALIVEANDREADY);
                            if (isStartClusterComplete()) {

                                // after group starutp is complete, all clstered instances in cluser will have same previous view of empty members.
                                AliveAndReadyView previous = getPreviousView();
                                ((AliveAndReadyViewImpl) previous).clearMembers();
                                if (LOG.isLoggable(TRACE_LEVEL)) {
                                    LOG.log(TRACE_LEVEL, "start cluster has completed, resetting previous view. previous=" + previous);
                                }
                            }
                        }
                    } else if (isMySignal(signal)) { // && INSTANCE_STARTUP

                        // set previous view to be all members in current view minus myself.
                        // typically previous view after a restart is empty view.
                        // this change is so all clustered instances in cluster have same previous view after a INSTANTCE_STARTUP
                        // JoinedAnDReady.
                        AliveAndReadyView previous = getPreviousView();
                        SortedSet<String> previousMembers = new TreeSet<String>(currentMembers);
                        if (rejoin == null) {

                            // this is a restart after FAILURE was detected or a PlannedShutdown.
                            // this instance should not be in previous view.
                            previousMembers.remove(currentInstanceName);
                        } // else this instance is rejoining group with no failure detection.
                          // the previous view and current view members are the same for this case.

                        ((AliveAndReadyViewImpl) previous).setMembers(previousMembers);
                        if (LOG.isLoggable(TRACE_LEVEL)) {
                            LOG.log(TRACE_LEVEL, "JoinedAndReady INSTANCE_STARTUP current=" + getCurrentView() + " previous=" + getPreviousView());
                        }
                    }
                    if (jrns instanceof JoinedAndReadyNotificationSignalImpl) {
                        JoinedAndReadyNotificationSignalImpl jrnsimpl = (JoinedAndReadyNotificationSignalImpl) jrns;
                        jrnsimpl.setCurrentView(getCurrentView());
                        jrnsimpl.setPreviousView(getPreviousView());
                    }
                } // end synchronized aliveAndReadyView
            }
        }
    }

    public void put(String joinedAndReadyMember, SortedSet<String> readyMembers) {
        if (LOG.isLoggable(TRACE_LEVEL)) {
            LOG.log(TRACE_LEVEL, "put joinedAndReadySignal member:" + joinedAndReadyMember + " ready members:" + readyMembers);
        }
        joinedAndReadySignalReadyList.put(joinedAndReadyMember, readyMembers);
    }

    private boolean isStartClusterComplete() {
        return ctx != null ? ctx.isGroupStartupComplete() : false;
    }
}
