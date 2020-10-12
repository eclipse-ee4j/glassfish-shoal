/*
 * Copyright (c) 2010, 2018 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.enterprise.ee.cms.impl.common;

import java.text.MessageFormat;
import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeSet;

import com.sun.enterprise.ee.cms.core.AliveAndReadyView;
import com.sun.enterprise.ee.cms.core.Signal;

public class AliveAndReadyViewImpl implements AliveAndReadyView {
    private Signal signal;
    private long signalTime;

    private SortedSet<String> members;
    final private long viewId;
    final private long creationTime;

    public AliveAndReadyViewImpl(SortedSet<String> members, long viewId) {
        this.members = new TreeSet<String>(members);
        this.viewId = viewId;
        this.creationTime = System.currentTimeMillis();
        this.signal = null;
        this.signalTime = -1L;
    }

    // NOTE: specifically did not want to expose method setSignal(Signal) in AliveAndReadyView interface for end users.
    // This method exists for implementation to use only and thus only occurs here to enforce that desire.
    /**
     * Terminates this view as being the current view.
     *
     * @param signal the signal
     * @throws NullPointerException if closeViewSignal is null.
     */
    public void setSignal(final Signal signal) {
        if (signal == null) {
            throw new NullPointerException("setSignal: parameter signal is not allowed to be set to null");
        }
        this.signal = signal;
        this.signalTime = System.currentTimeMillis();
    }

    /**
     *
     * @return signal that caused transition to this view.
     */
    public Signal getSignal() {
        return signal;
    }

    /**
     *
     * @return an unmodifiable list of members who were alive and ready.
     */
    public synchronized SortedSet<String> getMembers() {
        return Collections.unmodifiableSortedSet(members);
    }

    // Do not make public. Implementation only use.
    // only to enable setting previous view to EMPTY list when start-cluster has completed.
    synchronized void clearMembers() {
        this.members = new TreeSet<String>();
    }

    // Do not make public. Implementation only use.
    // only to enable previous view for INSTANCE_STARTUP.
    synchronized void setMembers(SortedSet<String> members) {
        this.members = members;
    }

    /**
     *
     * @return time that this signal notification first occurred.
     */
    public long getSignalTime() {
        return signalTime;
    }

    public long getViewId() {
        return viewId;
    }

    public long getViewCreationTime() {
        return this.creationTime;
    }

    public long getViewDuration() {
        long duration;
        if (signal != null) {
            duration = signalTime - creationTime;
        } else {
            duration = System.currentTimeMillis() - creationTime;
        }
        return duration;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("AliveAndReadyView  ViewId:").append(viewId);
        if (signal == null) {
            sb.append(" View created at ").append(MessageFormat.format("{0,date} {0,time,full}", creationTime));
        } else {
            sb.append(" Signal:").append(signal.getClass().getSimpleName());
            sb.append(" Duration(ms):").append(getViewDuration());
            sb.append(" View terminated at ").append(MessageFormat.format("{0,date} {0,time,full}", signalTime));
        }
        if (members != null) {
            int size = members.size();
            sb.append(" Members[").append(size).append("]:[");
            for (String member : members) {
                sb.append(member).append(",");
            }
            if (size != 0) {
                sb.setCharAt(sb.length() - 1, ']');
            } else {
                sb.append("]");
            }
        }

        return sb.toString();
    }
}
