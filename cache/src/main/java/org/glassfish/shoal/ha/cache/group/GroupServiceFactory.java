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

package org.glassfish.shoal.ha.cache.group;

import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Mahesh Kannan
 *
 */
public class GroupServiceFactory {

    private ConcurrentHashMap<String, GroupServiceProvider> groupHandles = new ConcurrentHashMap<String, GroupServiceProvider>();

    private static final GroupServiceFactory _instance = new GroupServiceFactory();

    private GroupServiceFactory() {
    }

    public static GroupServiceFactory getInstance() {
        return _instance;
    }

    public synchronized GroupService getGroupService(String myName, String groupName, boolean startGMS) {
        String key = makeKey(myName, groupName);
        GroupServiceProvider server = groupHandles.get(key);
        if (server == null) {
            server = new GroupServiceProvider(myName, groupName, startGMS);
            groupHandles.put(key, server);
        }

        return server;
    }

    private static String makeKey(String myName, String groupName) {
        return myName + ":" + groupName;
    }

    public void shutdown(String myName, String groupName) {
        String key = makeKey(myName, groupName);
        GroupServiceProvider server = groupHandles.remove(key);
        if (server != null) {
            server.shutdown();
        }
    }

    public static void main(String[] args) throws Exception {
        GroupServiceFactory factory = GroupServiceFactory.getInstance();
        factory.getGroupService(args[0], args[1], true/* startGMS */);
    }
}
