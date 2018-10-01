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

package org.shoal.ha.cache.impl.util;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Mahesh Kannan
 *
 */
public class CumulativeCommandResponse extends CommandResponse {

	private int maxResponse;

	CountDownLatch latch;

	public CumulativeCommandResponse(ResponseMediator mediator, int maxResponse, Object initialValue) {
		super(mediator);
		this.maxResponse = maxResponse;
		latch = new CountDownLatch(maxResponse);

		// set initial value
		result = initialValue;
	}

	public void setResult(Object v) {
		updateResult(result, v);
		latch.countDown();
		if (latch.getCount() == 0) {
			super.setResult(result);
		}
	}

	protected void updateResult(Object oldValue, Object newValue) {

	}
}
