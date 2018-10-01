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
 * Signal corresponding to PlannedShutdownAction. This Signal enables the consumer to get specifics about a graceful
 * administratively driven shutdown of a member. This Signal type will only be passed to a PlannedShutdownAction.
 * 
 * @author Shreedhar Ganapathy Date: Feb 3, 2005
 * @version $Revision$
 */
public interface PlannedShutdownSignal extends Signal, AliveAndReadySignal {
	/**
	 * Planned shutdown events can be one of two types, Group Shutdown or Instance Shutdown. These types are defined in an
	 * enum in the class GMSConstants.shutdownType
	 * 
	 * @see com.sun.enterprise.ee.cms.core.GMSConstants
	 * @return GMSConstants.shutdownType
	 */
	GMSConstants.shutdownType getEventSubType();
}
