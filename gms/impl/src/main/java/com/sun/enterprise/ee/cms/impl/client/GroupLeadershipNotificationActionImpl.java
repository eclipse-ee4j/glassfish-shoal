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

package com.sun.enterprise.ee.cms.impl.client;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.enterprise.ee.cms.core.ActionException;
import com.sun.enterprise.ee.cms.core.CallBack;
import com.sun.enterprise.ee.cms.core.GroupLeadershipNotificationAction;
import com.sun.enterprise.ee.cms.core.Signal;
import com.sun.enterprise.ee.cms.core.SignalAcquireException;
import com.sun.enterprise.ee.cms.core.SignalReleaseException;
import com.sun.enterprise.ee.cms.logging.GMSLogDomain;

/**
 * Reference Implementation of GroupLeadershipNotificationAction
 *
 * @author Bongjae Chang
 */
public class GroupLeadershipNotificationActionImpl implements GroupLeadershipNotificationAction {

	private Logger logger = GMSLogDomain.getLogger(GMSLogDomain.GMS_LOGGER);

	private final CallBack callBack;

	public GroupLeadershipNotificationActionImpl(final CallBack callBack) {
		this.callBack = callBack;
	}

	/**
	 * Implementations of consumeSignal should strive to return control promptly back to the thread that has delivered the
	 * Signal.
	 */
	public void consumeSignal(final Signal s) throws ActionException {
		try {
			s.acquire();
			callBack.processNotification(s);
		} catch (SignalAcquireException e) {
			logger.log(Level.SEVERE, e.getLocalizedMessage(), e);
		}

		try {
			s.release();
		} catch (SignalReleaseException e) {
			logger.log(Level.SEVERE, e.getLocalizedMessage(), e);
		}
	}
}
