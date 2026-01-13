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

/**
 * Represents Signal state corresponding to whether a signal is part of an entire group startup or the start of an
 * individual member of the group.
 *
 * @see GroupManagementService#announceGroupStartup(String,
 * org.glassfish.shoal.gms.api.core.GMSConstants.groupStartupState, java.util.List)
 * @author Joe Fialli Date: April 1, 2009
 */
public interface GroupStartupNotificationSignal {
    /**
     * Denote whether Joining member is joining group as part of an entire group startup or not.
     *
     * <P>
     * Handlers of Join and JoinAndReady notifications may use this information to optimize their handlers to perform
     * differently based on whether all members of group are being started at same time or whether a instance is just
     * joining an already running group.
     *
     * @return INSTANCE_STARTUP or GROUP_STARTUP.
     */
    GMSConstants.startupType getEventSubType();
}
