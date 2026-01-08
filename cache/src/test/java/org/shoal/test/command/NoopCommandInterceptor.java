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

package org.shoal.test.command;

import java.util.concurrent.atomic.AtomicInteger;

import org.shoal.ha.cache.api.AbstractCommandInterceptor;
import org.shoal.ha.cache.api.DataStoreException;
import org.shoal.ha.cache.command.Command;

/**
 * @author Mahesh Kannan
 */
public class NoopCommandInterceptor<K, V> extends AbstractCommandInterceptor<K, V> {

    private AtomicInteger totalTransCount = new AtomicInteger();

    private AtomicInteger noopTranscount = new AtomicInteger();

    private AtomicInteger noopRecvCount = new AtomicInteger();

    @Override
    public void onTransmit(Command cmd, String initiator) throws DataStoreException {
        totalTransCount.incrementAndGet();
        System.out.println("**** NoopCommandInterceptor.onTransmit() got: " + cmd.getClass().getName());
        if (cmd instanceof NoopCommand) {
            noopTranscount.incrementAndGet();
            getDataStoreContext().getCommandManager().execute(new BatchedNoopCommand());
        } else {
            super.onTransmit(cmd, initiator);

        }
    }

    @Override
    public void onReceive(Command cmd, String initiator) throws DataStoreException {
        if (cmd instanceof NoopCommand) {
            noopRecvCount.incrementAndGet();
            super.onReceive(cmd, initiator);
        }
    }

    public int getTotalTransCount() {
        return totalTransCount.get();
    }

    public int getNoopTransCount() {
        return noopTranscount.get();
    }

    public int getReceiveCount() {
        return noopRecvCount.get();
    }
}
