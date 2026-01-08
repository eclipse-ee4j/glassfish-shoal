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

package org.glassfish.shoal.gms.common;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

import org.glassfish.shoal.gms.api.core.GroupManagementService;
import org.glassfish.shoal.gms.logging.GMSLogDomain;

/**
 * Produces and retains the GMSContext for the lifetime of the GMS instance
 *
 * @author Shreedhar Ganapathy Date: Jan 16, 2004
 * @version $Revision$
 */
public class GMSContextFactory {
    private static final Map<String, GMSContext> ctxCache = new HashMap<String, GMSContext>();
    private static Logger logger = GMSLogDomain.getLogger(GMSLogDomain.GMS_LOGGER);

    private GMSContextFactory() {
    }

    // TODO: Shreedhar's comment: The invocation of appropriate provider's context has got to get better
    @SuppressWarnings("unchecked")
    static GMSContext produceGMSContext(final String serverToken, final String groupName, final GroupManagementService.MemberType memberType,
            final Properties properties) {
        GMSContext ctx;
        if ((ctx = ctxCache.get(groupName)) == null) {
            ctx = new org.glassfish.shoal.gms.base.GMSContextImpl(serverToken, groupName, memberType, properties);
            ctxCache.put(groupName, ctx);
        }
        return ctx;
    }

    public static GMSContext getGMSContext(final String groupName) {
        return ctxCache.get(groupName);
    }

    public static void removeGMSContext(final String groupName) {
        ctxCache.remove(groupName);
    }
}
