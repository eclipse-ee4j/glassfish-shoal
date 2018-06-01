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

package com.sun.enterprise.ee.cms.impl.base;

import com.sun.enterprise.mgmt.ClusterView;
import com.sun.enterprise.mgmt.ClusterViewEvents;

/**
 * @author Shreedhar Ganapathy
 *         Date: Jun 27, 2006
 * @version $Revision$
 */
public class EventPacket {
    private final ClusterViewEvents clusterViewEvent;
    private final SystemAdvertisement systemAdvertisement;
    private final ClusterView clusterView;

    public EventPacket(
            final ClusterViewEvents clusterViewEvent,
            final SystemAdvertisement systemAdvertisement,
            final ClusterView clusterView) {
        this.clusterViewEvent = clusterViewEvent;
        this.systemAdvertisement = systemAdvertisement;
        this.clusterView = clusterView;
    }

    public ClusterViewEvents getClusterViewEvent() {
        return clusterViewEvent;
    }

    public SystemAdvertisement getSystemAdvertisement() {
        return systemAdvertisement;
    }

    public ClusterView getClusterView() {
        return clusterView;
    }

    public String toString() {
        final String token = systemAdvertisement.getName();
        return clusterViewEvent.toString() + " from " + token;
    }
}
