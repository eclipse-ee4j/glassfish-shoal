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

import com.sun.enterprise.ee.cms.impl.base.PeerID;

import java.io.IOException;

/**
 * This interface is for sending a {@link Message} to the specific destination
 *
 * This interface can be implemented for only TCP or only UDP or both TCP and UDP transport layer
 *
 * @author Bongjae Chang
 */
public interface MessageSender extends ShoalMessageSender {

	/**
	 * Sends the given {@link Message} to the destination
	 *
	 * @param peerID the destination {@link PeerID}. <code>null</code> is not allowed
	 * @param message a message which is sent to the peer
	 * @return true if the message is sent to the destination successfully, otherwise false
	 * @throws IOException if I/O error occurs or given parameters are not valid
	 */
	public boolean send(final PeerID peerID, final Message message) throws IOException;
}
