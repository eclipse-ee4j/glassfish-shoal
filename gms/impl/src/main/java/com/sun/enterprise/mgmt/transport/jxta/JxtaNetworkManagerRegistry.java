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

package com.sun.enterprise.mgmt.transport.jxta;

import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;

/**
 * This is a registry that holds the network manager instances based on group name.
 *
 * @author Shreedhar Ganapathy
 *         Date: Oct 12, 2006
 *         Time: 10:46:25 AM
 */
public class JxtaNetworkManagerRegistry {
    private static final Map<String, JxtaNetworkManagerProxy> registry = new HashMap<String, JxtaNetworkManagerProxy>();

    private JxtaNetworkManagerRegistry() {

    }

    /**
     * Adds a Network Manager Proxy to the registry for the given GroupName and NetworkManager Instance
     *
     * @param groupName - name of the group
     * @param manager   - Network Manager instance for which a proxy is registered
     */
    static void add(final String groupName, final JxtaNetworkManager manager) {
        synchronized ( registry ) {
            registry.put(groupName, new JxtaNetworkManagerProxy(manager));
        }
    }

    /**
     * returns a NetworkManagerProxy for the given groupName
     *
     * @param groupName name of the group
     * @return NetworkManagerProxy instance wrapping a network manager corresponding to the group
     */
    static JxtaNetworkManagerProxy getNetworkManagerProxy(final String groupName) {
        return registry.get(groupName);
    }

    /**
     * removes the NetworkManagerProxy instance from the registry.
     *
     * @param groupName name of the group
     */
    public static void remove(final String groupName) {
        synchronized ( registry ) {
            registry.remove(groupName);
        }
    }

    /**
     * Returns all registered domain names
     * @return an interator of domain names
     */
    public static Iterator<String> getGroups() {
        return registry.keySet().iterator();
    }
}
