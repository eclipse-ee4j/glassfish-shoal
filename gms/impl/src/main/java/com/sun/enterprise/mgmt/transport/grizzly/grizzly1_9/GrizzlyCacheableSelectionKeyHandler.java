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

import java.nio.channels.SelectionKey;

import com.sun.grizzly.connectioncache.server.CacheableSelectionKeyHandler;
import com.sun.grizzly.util.Copyable;

/**
 * @author Bongjae Chang
 */
public class GrizzlyCacheableSelectionKeyHandler extends CacheableSelectionKeyHandler {

    private GrizzlyNetworkManager1_9 networkManager;

    public GrizzlyCacheableSelectionKeyHandler() {
    }

    public GrizzlyCacheableSelectionKeyHandler(int highWaterMark, int numberToReclaim, GrizzlyNetworkManager1_9 networkManager) {
        super(highWaterMark, numberToReclaim);
        this.networkManager = networkManager;
    }

    @Override
    public void cancel(SelectionKey key) {
        super.cancel(key);
        if (networkManager != null) {
            networkManager.removeRemotePeer(key);
        }
    }

    @Override
    public void copyTo(Copyable copy) {
        super.copyTo(copy);
        if (copy instanceof GrizzlyCacheableSelectionKeyHandler) {
            GrizzlyCacheableSelectionKeyHandler copyHandler = (GrizzlyCacheableSelectionKeyHandler) copy;
            copyHandler.networkManager = networkManager;
        }
    }
}
