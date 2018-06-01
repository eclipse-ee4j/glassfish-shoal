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

package com.sun.enterprise.mgmt.transport.grizzly.grizzly1_9;

import com.sun.grizzly.ProtocolFilter;
import com.sun.grizzly.Context;
import com.sun.grizzly.ProtocolParser;
import com.sun.enterprise.mgmt.transport.Message;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.Map;
import java.util.HashMap;

/**
 * @author Bongjae Chang
 */
public class GrizzlyMessageDispatcherFilter implements ProtocolFilter {

    private final GrizzlyNetworkManager1_9 networkManager;

    public GrizzlyMessageDispatcherFilter( GrizzlyNetworkManager1_9 networkManager ) {
        this.networkManager = networkManager;
    }

    public boolean execute( Context ctx ) throws IOException {
        Object obj = ctx.removeAttribute( ProtocolParser.MESSAGE );
        if( !( obj instanceof Message ) )
            throw new IOException( "received message is not valid: " + obj );
        final Message incomingMessage = (Message)obj;
        final SelectionKey selectionKey = ctx.getSelectionKey();
        Map<String, Object> piggyback = null;
        if( selectionKey != null ) {
            piggyback = new HashMap<String, Object>();
            piggyback.put( GrizzlyNetworkManager1_9.MESSAGE_SELECTION_KEY_TAG, selectionKey );
        }
        networkManager.receiveMessage( incomingMessage, piggyback );
        return false;
    }

    public boolean postExecute( Context context ) throws IOException {
        return true;
    }
}
