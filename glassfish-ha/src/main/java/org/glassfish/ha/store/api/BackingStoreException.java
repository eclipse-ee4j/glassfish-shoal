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

package org.glassfish.ha.store.api;

/**
 * This exception is thrown to signal any BackingStore specific exception.
 * <code>BackingStore.getCause()</code> can be used to get the actual store
 * specific exception.
 *
 * @author Mahesh.Kannan@Sun.Com
 * @author Larry.White@Sun.Com
 *
 */
public class BackingStoreException extends Exception {

    /**
     * Creates a BackingStoreException with null as its detail message. The
     * cause is not initialized, and may subsequently be initialized by a call
     * to <code>Throwable.initCause(java.lang.Throwable)</code>.
     */
    public BackingStoreException() {
    }

    /**
     * constructs a BackingStoreException with the specified detail message
     * 
     * @param message
     *            the detail message. The detail message is saved for later
     *            retrieval by the <code>Throwable.getMessage()</code> method.
     */
    public BackingStoreException(String message) {
        super(message);
    }

    /**
     * Constructs a new BackingStoreException exception with the specified cause
     * and a detail message of (cause==null ? null : cause.toString())
     * 
     * @param message
     *            the detail message. The detail message is saved for
     * @param th
     *            the cause
     */
    public BackingStoreException(String message, Throwable th) {
        super(message, th);
    }

}
