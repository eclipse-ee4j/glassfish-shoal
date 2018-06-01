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

package com.sun.enterprise.mgmt.transport.grizzly;

/**
 * Specifies constants that are allowed to be used as keys for configuration
 * elements that are sought to be set or retrieved for/from Grizzly platform
 * configuration
 *
 * @author Bongjae Chang
 */
public enum GrizzlyConfigConstants {
    TCPSTARTPORT,
    TCPENDPORT,
    BIND_INTERFACE_NAME,

    // thread pool
    MAX_POOLSIZE, // max threads for tcp and multicast processing. See max parameter for ThreadPoolExecutor constructor.
    CORE_POOLSIZE, // core threads for tcp and multicast processing. See core parameter for ThreadPoolExecutor constructor.
    KEEP_ALIVE_TIME, // ms
    POOL_QUEUE_SIZE,

    // pool management
    HIGH_WATER_MARK, // maximum number of active outbound connections Controller will handle
    NUMBER_TO_RECLAIM, // number of LRU connections, which will be reclaimed in case highWaterMark limit will be reached
    MAX_PARALLEL, // maximum number of active outbound connections to single destination (usually <host>:<port>)

    START_TIMEOUT, // ms
    WRITE_TIMEOUT, // ms

    MAX_WRITE_SELECTOR_POOL_SIZE,

    // comma separated list of tcp uri endpoints
    // ex) tcp://192.168.0.3:9090,tcp://61.77.153.2:9090
    DISCOVERY_URI_LIST,
    MULTICAST_TIME_TO_LIVE
}
