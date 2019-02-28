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

package com.sun.enterprise.mgmt;

/**
 * Specifies constants that are allowed to be used as keys for configuration elements that are sought to be set or
 * retrieved for/from transport configuration
 *
 * @author Shreedhar Ganapathy Date: Jun 22, 2006
 * @version $Revision$
 */
public enum ConfigConstants {
    MULTICASTADDRESS,

    MULTICASTPORT,

    MULTICAST_PACKET_SIZE,

    FAILURE_DETECTION_TIMEOUT,

    FAILURE_DETECTION_RETRIES,

    FAILURE_VERIFICATION_TIMEOUT,

    DISCOVERY_TIMEOUT,

    LOOPBACK,

    // used for specifying which interface to use for group communication
    // This is the address which Shoal should bind to for communication.
    BIND_INTERFACE_ADDRESS,

    // admin can specify the timeout after which the HealthMonitor.isConnected() thread can
    // quit checking if the peer's machine is up or not.
    FAILURE_DETECTION_TCP_RETRANSMIT_TIMEOUT,

    // port where a socket can be created to see if the instance's machine is up or down
    FAILURE_DETECTION_TCP_RETRANSMIT_PORT
}
