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
 * Signal corresponding to JoinedAndReadyNotificationAction. This Signal enables the consumer to get specifics about a
 * join and ready notification. This Signal type will only be passed to a JoinedAndReadyNotificationAction. This Signal
 * is delivered to registered GMS Clients on all members of the group.
 *
 * @author Sheetal Vartak Date: Nov 13 2007
 */
public interface JoinedAndReadyNotificationSignal extends Signal, GroupStartupNotificationSignal, RejoinableEvent, AliveAndReadySignal {

    List<String> getCurrentCoreMembers();

    List<String> getAllCurrentMembers();
}
