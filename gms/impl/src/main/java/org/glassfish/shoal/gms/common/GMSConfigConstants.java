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

package org.glassfish.shoal.gms.common;

/**
 * Configuration constants used to denote keys for configuration elements. These are used both to populate values for
 * such contants and to retrive them.
 *
 * @author Shreedhar Ganapathy Date: Jul 13, 2005
 * @version $Revision$ TODO: Move this out of here to impl.jxta
 */
public class GMSConfigConstants {
    public static final String MULTICAST_ADDRESS = "UDP::mcast_addr";
    public static final String MULTICAST_PORT = "UDP::mcast_port";
    public static final String FD_TIMEOUT = "FD::timeout";
    public static final String FD_MAX_RETRIES = "FD::max_tries";
    public static final String MERGE_MAX_INTERVAL = "MERGE2::max_interval";
    public static final String MERGE_MIN_INTERVAL = "MERGE2::min_interval";
    public static final String VS_TIMEOUT = "VERIFY_SUSPECT::timeout";
    public static final String PING_TIMEOUT = "PING::timeout";
}
