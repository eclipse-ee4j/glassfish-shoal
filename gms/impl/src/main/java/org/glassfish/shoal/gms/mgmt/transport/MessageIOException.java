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

/**
 * IOException which related to parsing {@link Message}
 *
 * @author Bongjae Chang
 */
public class MessageIOException extends IOException {


    private static final long serialVersionUID = -3243045948393446810L;

    public MessageIOException(String msg) {
        super(msg);
    }

    public MessageIOException(Throwable t) {
        // super( t ); // JDK 1.6
        super(t == null ? "" : t.getMessage());
    }

    public MessageIOException(String msg, Throwable t) {
        // super( msg, t ); // JDK 1.6
        super(msg);
    }
}
