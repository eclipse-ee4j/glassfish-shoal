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

package org.glassfish.shoal.gms.api.core;

import java.util.List;

/**
 * Signal corresponding to GroupLeadershipNotificationAction. This Signal enables the consumer to get specifics about a
 * GroupLeadership notification. This Signal type will only be passed to a GroupLeadershipNotificationAction. This
 * Signal is delivered to registered GMS Clients on all members of the group.
 *
 * @author Bongjae Chang
 */
public interface GroupLeadershipNotificationSignal extends Signal {
    /**
     * provides a read-only list of the previous view's snapshot at time signal arrives.
     *
     * @return List containing the list of <code>GMSMember</code>s which are corresponding to the view
     */
    List<GMSMember> getPreviousView();

    /**
     * provides a read-only list of the current view's snapshot at time signal arrives.
     *
     * @return List containing the list of <code>GMSMember</code>s which are corresponding to the view
     */
    List<GMSMember> getCurrentView();

    /**
     * provides a list of all live and current CORE designated members.
     *
     * @return List containing the list of member token ids of core members
     */
    List<String> getCurrentCoreMembers();

    /**
     * provides a list of all live members i.e. CORE and SPECTATOR members.
     *
     * @return List containing the list of member token ids of all members
     */
    List<String> getAllCurrentMembers();
}
