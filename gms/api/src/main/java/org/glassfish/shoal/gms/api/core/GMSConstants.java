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
 * Herein we specify client facing constants that may be applicable to specific GMS notifications, api calls or key
 * descriptions.
 *
 * @author Shreedhar Ganapathy Date: Aug 15, 2005
 * @version $Revision$
 */
public class GMSConstants {
    public static final String GRIZZLY_GROUP_COMMUNICATION_PROVIDER = "grizzly2";
    public static final String JXTA_GROUP_COMMUNICATION_PROVIDER = "jxta";
    public static final String DEFAULT_GROUP_COMMUNICATION_PROVIDER = GRIZZLY_GROUP_COMMUNICATION_PROVIDER;
    public static final String GROUP_COMMUNICATION_PROVIDER = System.getProperty("SHOAL_GROUP_COMMUNICATION_PROVIDER", DEFAULT_GROUP_COMMUNICATION_PROVIDER);

    public static enum shutdownType {
        INSTANCE_SHUTDOWN, GROUP_SHUTDOWN
    }

    public static enum shutdownState {
        INITIATED, COMPLETED
    }

    public static enum startupType {
        INSTANCE_STARTUP, GROUP_STARTUP
    }

    public static enum groupStartupState {
        INITIATED, COMPLETED_SUCCESS, COMPLETED_FAILED
    }

    public static final int DEFAULT_MULTICAST_TIME_TO_LIVE = -1;
    public static final int MINIMUM_MULTICAST_TIME_TO_LIVE = 4;
    public static final String JOIN_CLUSTER_SEED_URI_LIST = "JOIN_CLUSTER_SEED_URI_LIST";
}
