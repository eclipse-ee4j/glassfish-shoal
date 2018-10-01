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

import com.sun.enterprise.ee.cms.core.*;
import com.sun.enterprise.ee.cms.logging.GMSLogDomain;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reference implementation of MessageAction interface.
 *
 * @author Shreedhar Ganapathy Date: Jan 21, 2004
 * @version $Revision$
 */
public class MessageActionImpl implements MessageAction {
	private Logger logger = GMSLogDomain.getLogger(GMSLogDomain.GMS_LOGGER);
	private CallBack callback;

	public MessageActionImpl(final CallBack callback) {
		this.callback = callback;
	}

	/**
	 * Implementations of consumeSignal should strive to return control promptly back to the thread that has delivered the
	 * Signal.
	 */
	public void consumeSignal(final Signal signal) throws ActionException {
		boolean signalAcquired = false;
		// Always Acquire before doing any other processing
		try {
			signal.acquire();
			signalAcquired = true;
			processMessage(signal);
		} catch (SignalAcquireException e) {
			logger.log(Level.SEVERE, e.getLocalizedMessage(), e);
		} finally {
			// Always Release after completing any other processing.
			if (signalAcquired) {
				try {
					signal.release();
				} catch (SignalReleaseException e) {
					logger.log(Level.SEVERE, e.getLocalizedMessage(), e);
				}
			}
		}
	}

	private void processMessage(final Signal signal) throws ActionException {
		try {
			callback.processNotification(signal);
		} catch (Throwable t) {
			final String callbackClassName = callback == null ? "<null>" : callback.getClass().getName();
			logger.log(Level.WARNING, "msg.action.unhandled.exception", new Object[] { t.getClass().getName(), callbackClassName });
			ActionException ae = new ActionException("unhandled exception processing signal " + signal.toString());
			ae.initCause(t);
			throw ae;
		}
	}
}
