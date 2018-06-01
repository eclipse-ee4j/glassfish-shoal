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

package org.shoal.ha.cache.impl.command;

/**
 * @author Mahesh Kannan
 */
public class ReplicationCommandOpcode {
    

    public static final byte REPLICATION_FRAME_PAYLOAD = 1;

    public static final byte SIMPLE_ACK_COMMAND = 2;

    public static final byte SAVE = 33;

    public static final byte LOAD_REQUEST = 35;

    public static final byte REMOVE = 36;

    public static final byte LOAD_RESPONSE = 37;

    public static final byte TOUCH = 38;

    public static final byte REMOVE_EXPIRED = 39;

    public static final byte REMOVE_EXPIRED_RESULT = 44;

    public static final byte STALE_REMOVE = 40;

    public static final byte SIZE_REQUEST = 51;

    public static final byte SIZE_RESPONSE = 52;


    public static final byte STOREABLE_SAVE = 68;

    public static final byte STOREABLE_UNICAST_LOAD_REQUEST = 69;

    public static final byte STOREABLE_REMOVE = 71;

    public static final byte STOREABLE_LOAD_RESPONSE = 72;

    public static final byte STOREABLE_TOUCH = 73;

    public static final byte STOREABLE_FULL_SAVE_COMMAND = 76;

    
    public static final byte NOOP_COMMAND = 102;
}
