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
 * Created by IntelliJ IDEA. User: sheetal Date: Apr 4, 2008 Time: 1:54:34 PM This Exception class has been created to
 * report that the particular member is not in the View
 */
public class MemberNotInViewException extends GMSException {

	/**
	 *
	 */
	private static final long serialVersionUID = -8581553671167071724L;

	public MemberNotInViewException() {
		super();
	}

	public MemberNotInViewException(String message) {
		super(message);
	}

	public MemberNotInViewException(Throwable e) {
		super(e);
	}

	public MemberNotInViewException(String s, Throwable e) {
		super(s, e);
	}
}
