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

package com.sun.enterprise.mgmt.transport;

import java.util.EventListener;

/**
 * Message listener interface
 *
 * For receiving and processing inbound messages, this listener should be registered on {@link NetworkManager} with
 * corresponding to the appropriate message type i.g. For adding the listener, use
 * {@link NetworkManager#addMessageListener(MessageListener)}} and for removing the listener, use
 * {@link com.sun.enterprise.mgmt.transport.NetworkManager#removeMessageListener(MessageListener)}
 *
 * @author Bongjae Chang
 */
public interface MessageListener extends EventListener {

    /**
     * Processing a {@link MessageEvent}
     *
     * @param event a received message event
     * @throws MessageIOException if I/O error occurs
     */
    void receiveMessageEvent(final MessageEvent event) throws MessageIOException;

    /**
     * Returns the message type which {@link Message} is supporting i.g. {@link Message#TYPE_CLUSTER_MANAGER_MESSAGE} or
     * {@link Message#TYPE_HEALTH_MONITOR_MESSAGE}'s integer value or etc...
     *
     * @return the message type about which this listener is concerned
     */
    int getType();
}
