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

package com.sun.enterprise.ee.cms.core;

import java.util.List;
import java.util.Set;
import java.util.SortedSet;

import com.sun.enterprise.ee.cms.core.Signal;


/**
 * A read-only view consisting of all the AliveAndReady CORE members of a GMS group.
 *
 * The GMS notification signals of JoinedAndReadyNotificationSignal, FailureNotificationSignal and PlannedShutdownSignal
 * transition from one of these views to the next view. When one of these signal occurs, the current view is terminated
 * by setting its signal.  While the view's signal is null, it is considered the current view. Once a terminating signal
 * occurs, than this view is considered the previous view and getSignal() returns the GMS notification that caused this 
 * view to conclude.
 */
public interface AliveAndReadyView {

    /**
     * These are members of this view BEFORE the GMS notification signal that terminated
     * this view as being the current view.
     *
     * @return an unmodifiable list of sorted CORE members who are alive and ready.
     *
     */
    SortedSet<String> getMembers();

    /**
     *
     * @return signal that caused transition from this view. returns null when this is the current view
     *         and no signal has occurred to cause a transition to the next view.
     */
     Signal getSignal();


    /**
     *
     * @return time this view ceased being the current view when its signal was set.
     */
    long getSignalTime();

    /**
     * Monotonically increasing id.  Each GMS notification signal for a core member that causes a new view to be created
     * results in this value being increased.
     * @return a generated id
     */
    long getViewId();

    /**
     *
     * @return duration in milliseconds that this view is/was the current view.
     *          If <code>getSignal</code> is null, this value is still growing each time this method is called.
     */
    long getViewDuration();


    /**
     *
     * @return time that this view got created.
     */
    long getViewCreationTime();
}
