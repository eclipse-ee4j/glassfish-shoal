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

package org.glassfish.shoal.gms.client;

import java.util.Date;

import org.glassfish.shoal.gms.api.core.RejoinSubevent;

/**
 * Implementation of rejoin subevent that captures the time that a previously failed instance had joined the cluster.
 */
public class RejoinSubeventImpl implements RejoinSubevent {
    static final long serialVersionUID = -3554482822551862156L;

    final long groupJoinTime;

    public RejoinSubeventImpl(long groupJoinTime) {
        this.groupJoinTime = groupJoinTime;
    }

    @Override
    public long getGroupJoinTime() {
        return groupJoinTime;
    }

    @Override
    public String toString() {
        return new Date(groupJoinTime).toString();
    }
}
