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

package org.glassfish.shoal.gms.mgmt.transport;

import java.io.IOException;

/**
 * This interface is for broadcasting or multicasting a {@link Message} to all members
 *
 * @author Bongjae Chang
 */
public interface MulticastMessageSender extends ShoalMessageSender {

    /**
     * Broadcasts or Multicasts the given {@link Message} to all members
     *
     * @param message a message which is sent to all members
     * @return true if the message is sent to all members successfully, otherwise false
     * @throws IOException if I/O error occurs or given parameters are not valid
     */
    boolean broadcast(final Message message) throws IOException;
}
