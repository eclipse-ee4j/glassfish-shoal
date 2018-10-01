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

package com.sun.enterprise.ee.cms.core;

/**
 * Implementations of this interface consume a PlannedShutdownNotificationSignal which provides specifics associated
 * with a member that has announced its planned departure from the group. The Signal passed to consumeSignal method
 * should be cast to a PlannedShutdownSignal in order to use the additional information of the event sub type. The Event
 * SubType is defined by the enum shutdownType defined in GMSConstants.
 * 
 * @author Shreedhar Ganapathy Date: Feb 3, 2005
 * @version $Revision$
 */
public interface PlannedShutdownAction extends Action {
}
