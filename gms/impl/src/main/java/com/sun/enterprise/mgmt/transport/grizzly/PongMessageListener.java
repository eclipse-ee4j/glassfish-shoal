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

import com.sun.enterprise.mgmt.transport.MessageListener;
import com.sun.enterprise.mgmt.transport.MessageEvent;
import com.sun.enterprise.mgmt.transport.MessageIOException;
import com.sun.enterprise.mgmt.transport.Message;
import com.sun.enterprise.ee.cms.impl.base.PeerID;

import java.util.concurrent.CountDownLatch;
import java.util.logging.Logger;

/**
 * @author Bongjae Chang
 */
public class PongMessageListener implements MessageListener {

	private final static Logger LOG = GrizzlyNetworkManager.getLogger();

	@Override
	public void receiveMessageEvent(final MessageEvent event) throws MessageIOException {
		if (event == null)
			return;
		final Message msg = event.getMessage();
		if (msg == null)
			return;
		Object obj = event.getSource();
		if (!(obj instanceof GrizzlyNetworkManager))
			return;
		GrizzlyNetworkManager networkManager = (GrizzlyNetworkManager) obj;
		PeerID sourcePeerId = event.getSourcePeerID();
		if (sourcePeerId == null)
			return;
		CountDownLatch pingMessageLock = networkManager.getPingMessageLock(sourcePeerId);
		if (pingMessageLock != null)
			pingMessageLock.countDown();
	}

	@Override
	public int getType() {
		return Message.TYPE_PONG_MESSAGE;
	}
}
