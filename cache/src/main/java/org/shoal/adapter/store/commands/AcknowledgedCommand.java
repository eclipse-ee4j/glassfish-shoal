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

package org.shoal.adapter.store.commands;

import java.io.IOException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.shoal.ha.cache.api.DataStoreException;
import org.shoal.ha.cache.impl.command.Command;
import org.shoal.ha.cache.impl.util.CommandResponse;
import org.shoal.ha.cache.impl.util.ResponseMediator;

/**
 * @author Mahesh Kannan
 */
public abstract class AcknowledgedCommand<K, V> extends Command<K, V> {

   
    private static final long serialVersionUID = -4027862351560585449L;

    private transient CommandResponse resp;

    private transient Future future;

    private long tokenId;

    private String originatingInstance;

    protected AcknowledgedCommand(byte opCode) {
        super(opCode);
    }

    protected boolean beforeTransmit() {
        if (dsc.isDoSynchronousReplication()) {
            originatingInstance = dsc.getInstanceName();
            ResponseMediator respMed = dsc.getResponseMediator();
            resp = respMed.createCommandResponse();
            tokenId = resp.getTokenId();
            future = resp.getFuture();
        }

        return true;
    }

    protected void sendAcknowledgement() {
        try {
            dsc.getCommandManager().execute(new SimpleAckCommand<K, V>(originatingInstance, tokenId));
        } catch (DataStoreException dse) {
            // TODO: But can safely ignore
        }
    }

    private void writeObject(java.io.ObjectOutputStream out) throws IOException {
        out.writeBoolean(dsc.isDoSynchronousReplication());

        if (dsc.isDoSynchronousReplication()) {
            out.writeLong(tokenId);
            out.writeUTF(originatingInstance);
        }
    }

    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {

        boolean doSync = in.readBoolean();
        if (doSync) {
            tokenId = in.readLong();
            originatingInstance = in.readUTF();
        }
    }

    @Override
    public final void onSuccess() {
        if (dsc.isDoSynchronousReplication()) {
            try {
                waitForAck();
            } catch (Exception ex) {
                System.out.println("** Got exception: " + ex);
            }
        }
    }

    @Override
    public final void onFailure() {
        if (dsc.isDoSynchronousReplication()) {
            ResponseMediator respMed = dsc.getResponseMediator();
            respMed.removeCommandResponse(tokenId);
        }
    }

    private void waitForAck() throws DataStoreException, TimeoutException {
        try {
            future.get(3, TimeUnit.SECONDS);
        } catch (TimeoutException tEx) {
            throw tEx;
        } catch (Exception inEx) {
            throw new DataStoreException(inEx);
        } finally {
            ResponseMediator respMed = dsc.getResponseMediator();
            respMed.removeCommandResponse(tokenId);
        }
    }

}
