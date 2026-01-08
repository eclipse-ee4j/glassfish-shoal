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

package org.glassfish.shoal.gms.mgmt.transport.grizzly;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * @author Bongjae Chang
 */
public class GrizzlyUtil {

    private static final boolean IS_SUPPORT_NIO_MULTICAST = (getNIOMulticastMethod() != null);

    private static Logger logger = Logger.getLogger("org.glassfish.grizzly");

    private GrizzlyUtil() {
    }

    public static Logger getLogger() {
        return logger;
    }

    public static boolean isSupportNIOMulticast() {
        return IS_SUPPORT_NIO_MULTICAST;
    }

    private static Method getNIOMulticastMethod() {
        Method method = null;
        try {
            // TODO: consider re-enabling using JDK 7 NIO Multicast after more testing.
            // See Glassfish issue 16173 for details.

            // uncomment next line to enable using JDK 7 NIO multicast when it is available.
            // method = DatagramChannel.class.getMethod( "join", InetAddress.class, NetworkInterface.class );
        } catch (Throwable t) {
            method = null;
        }
        return method;
    }
}
