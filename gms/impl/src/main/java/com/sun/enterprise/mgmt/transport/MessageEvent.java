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

import java.util.EventObject;

import com.sun.enterprise.ee.cms.impl.base.PeerID;

/**
 * This class represents a received message event
 *
 * Management modules will use this message event in order to process a received network packet internally
 *
 * @author Bongjae Chang
 */
public class MessageEvent extends EventObject {
    static final long serialVersionUID = -7065693766490210768L;

    /**
     * The received {@link Message}
     */
    private final Message message;

    /**
     * The received message's source {@link PeerID}
     */
    private final PeerID sourcePeerID;

    /**
     * The received message's destination {@link PeerID}
     */
    private final PeerID targetPeerID;

    /**
     * Creates a new event
     *
     * @param source The object on which the message was received.
     * @param message The message object
     * @param sourcePeerID source peer id
     * @param targetPeerID target peer id
     */
    public MessageEvent(Object source, Message message, PeerID sourcePeerID, PeerID targetPeerID) {
        super(source);
        this.message = message;
        this.sourcePeerID = sourcePeerID;
        this.targetPeerID = targetPeerID;
    }

    /**
     * Returns the message associated with the event
     *
     * @return message
     */
    public Message getMessage() {
        return message;
    }

    /**
     * Returns the source peer id from which this message is sent
     *
     * @return peer id
     */
    public PeerID getSourcePeerID() {
        return sourcePeerID;
    }

    /**
     * Returns the target peer id to which this message is sent
     *
     * @return peer id
     */
    public PeerID getTargetPeerID() {
        return targetPeerID;
    }
}
