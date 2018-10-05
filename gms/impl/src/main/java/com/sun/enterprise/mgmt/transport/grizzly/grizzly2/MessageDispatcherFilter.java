/*
 * Copyright (c) 2011, 2018 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.enterprise.mgmt.transport.grizzly.grizzly2;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;

import com.sun.enterprise.mgmt.transport.Message;

/**
 * Message dispatcher Filter.
 *
 * @author Alexey Stashok
 */
public class MessageDispatcherFilter extends BaseFilter {
    private final Attribute<Map<String, Connection>> piggyBackAttribute = Grizzly.DEFAULT_ATTRIBUTE_BUILDER
            .createAttribute(MessageDispatcherFilter.class.getName() + ".piggyBack");

    private final GrizzlyNetworkManager2 networkManager;

    public MessageDispatcherFilter(GrizzlyNetworkManager2 networkManager) {
        this.networkManager = networkManager;
    }

    @Override
    public NextAction handleRead(final FilterChainContext ctx) throws IOException {
        final Connection connection = ctx.getConnection();
        final Message message = ctx.getMessage();

        Map<String, Connection> piggyBack = piggyBackAttribute.get(connection);
        if (piggyBack == null) {
            piggyBack = new HashMap<String, Connection>();
            piggyBack.put(GrizzlyNetworkManager2.MESSAGE_CONNECTION_TAG, connection);
            piggyBackAttribute.set(connection, piggyBack);
        }

        networkManager.receiveMessage(message, piggyBack);

        return ctx.getInvokeAction();
    }
}
