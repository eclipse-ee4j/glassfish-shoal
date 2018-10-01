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
 * A <code>Signal</code> type that enables consumers to acquire the MessageSignal, get the message( i.e payload),
 * perform appropriate operations and then release the signal to the signal pool.
 *
 * @author Shreedhar Ganapathy Date: Jan 12, 2004
 * @version $Revision$
 */
public interface MessageSignal extends Signal {
    /**
     * Returns the message(payload) as a byte array.
     *
     * @return byte[]
     */
    byte[] getMessage();

    /**
     * Returns the target component in this member to which this message is addressed.
     *
     * @return String targetComponent
     */
    String getTargetComponent();

}
