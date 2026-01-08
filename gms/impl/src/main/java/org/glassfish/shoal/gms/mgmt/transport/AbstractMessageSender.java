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

import org.glassfish.shoal.gms.base.PeerID;

/**
 * This class implements a common {@link MessageSender} logic simply in order to help the specific transport layer to be
 * implemented easily
 *
 * Mainly, this stores both source's {@link PeerID} and target's {@link PeerID} before sending the message to the peer
 *
 * @author Bongjae Chang
 */
public abstract class AbstractMessageSender implements MessageSender {

    /**
     * Represents local {@link PeerID}. This value should be assigned in real {@link MessageSender}'s implementation
     * correspoinding to the specific transport layer
     */
    protected PeerID localPeerID;

    /**
     * {@inheritDoc}
     */
    public boolean send(final PeerID peerID, final Message message) throws IOException {
        if (peerID == null) {
            throw new IOException("peer ID can not be null");
        }
        if (message == null) {
            throw new IOException("message is null");
        }
        if (localPeerID != null) {
            message.addMessageElement(Message.SOURCE_PEER_ID_TAG, localPeerID);
        }
        if (peerID != null) {
            message.addMessageElement(Message.TARGET_PEER_ID_TAG, peerID);
        }
        return doSend(peerID, message);
    }

    /**
     * {@inheritDoc}
     */
    public void start() throws IOException {
    }

    /**
     * {@inheritDoc}
     */
    public void stop() throws IOException {
    }

    /**
     * Sends the given {@link Message} to the destination
     *
     * @param peerID the destination {@link PeerID}. <code>null</code> is not allowed
     * @param message a message which is sent to the peer
     * @return true if the message is sent to the destination successfully, otherwise false
     * @throws IOException if I/O error occurs or given parameters are not valid
     */
    protected abstract boolean doSend(final PeerID peerID, final Message message) throws IOException;
}
