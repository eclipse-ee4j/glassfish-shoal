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

package com.sun.enterprise.mgmt.transport.jxta;

/**
 * Specifies constants that are allowed to be used as keys for configuration
 * elements that are sought to be set or retrieved for/from Jxta platform
 * configuration
 *
 * @author Shreedhar Ganapathy
 *         Date: Jun 22, 2006
 * @version $Revision$
 */
public enum JxtaConfigConstants {
    PRINCIPAL,

    PASSWORD,

    JXTAHOME,

    TCPSTARTPORT,

    TCPENDPORT,

    HTTPADDRESS,

    HTTPPORT,

    // specifies if this node is a rendezvous seed peer
    IS_BOOTSTRAPPING_NODE,

    //comma separated list of tcp/http rendezvous seed uri endpoints
    VIRTUAL_MULTICAST_URI_LIST,

    MULTICAST_POOLSIZE,    // how many simultaneous multicast messages can be processed before multicast messages start getting dropped.

    TCP_MAX_POOLSIZE,      // max threads for tcp processing.   See max parameter for ThreadPoolExecutor constructor.

    TCP_CORE_POOLSIZE,     // core threads for tcp processing.  See core parameter for ThreadPoolExecutor constructor.

    TCP_BLOCKING_QUEUESIZE,  // queue for pending incoming tcp requests (out of CORE threads).  When full, new threads created till MAX.
}
