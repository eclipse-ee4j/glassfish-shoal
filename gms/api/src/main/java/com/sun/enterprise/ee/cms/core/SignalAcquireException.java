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
 * Raises exceptions occuring while acquiring signals. If such an exception is raised, a deadlock on group resources
 * through concurrent acquisition of distributed resources is prevented
 *
 * For example, if <code>FailureRecoverySignal</code> throws a <code>SignalAcquireException</code>, it means that the
 * failed server has returned to operation or that it may not be possible to fence it out of the group. This will
 * indicate a group condition wherein control of resources is atomically defined such that it may be acceptable for most
 * group communication environments for continuing operations.
 *
 * @author Shreedhar Ganapathy Date: Jan 8, 2004
 * @version $Revision$
 */
public class SignalAcquireException extends Exception {
	/**
	 *
	 */
	private static final long serialVersionUID = -1507353998630657590L;

	public SignalAcquireException() {
		super();
	}

	public SignalAcquireException(final String message) {
		super(message);
	}

	public SignalAcquireException(final Exception e) {
		super(e);
	}
}
