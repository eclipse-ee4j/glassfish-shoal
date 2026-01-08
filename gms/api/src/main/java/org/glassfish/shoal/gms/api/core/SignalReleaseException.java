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

package org.glassfish.shoal.gms.api.core;

/**
 * Raises exceptions occuring while releasing signals.
 *
 * This will occur on rare conditions, for example, when it is impossible to stop fencing a previously failed server.
 *
 * @author Shreedhar Ganapathy Date: Jan 8, 2004
 * @version $Revision$
 */
public class SignalReleaseException extends Exception {
   
    private static final long serialVersionUID = 4568715109280384599L;

    public SignalReleaseException() {
        super();
    }

    public SignalReleaseException(final String message) {
        super(message);
    }

    public SignalReleaseException(final Exception e) {
        super(e);
    }
}
