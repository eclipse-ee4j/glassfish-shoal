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
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.shoal.ha.cache.api.ShoalCacheLoggerConstants;
import org.shoal.ha.cache.impl.command.Command;
import org.shoal.ha.cache.impl.command.ReplicationCommandOpcode;
import org.shoal.ha.cache.impl.util.CommandResponse;
import org.shoal.ha.cache.impl.util.ResponseMediator;

/**
 * @author Mahesh Kannan
 */
public class SizeResponseCommand<K, V> extends Command {

    private static final Logger _logger = Logger.getLogger(ShoalCacheLoggerConstants.CACHE_SIZE_RESPONSE_COMMAND);

    private long tokenId;

    private int size;

    private String originatingInstance;

    private String respondingInstanceName;

    public SizeResponseCommand() {
        super(ReplicationCommandOpcode.SIZE_RESPONSE);
        super.setKey("SizeResp:" + tokenId);
    }

    public SizeResponseCommand(String originatingInstance, long tokenId, int size) {
        this();
        this.originatingInstance = originatingInstance;
        this.tokenId = tokenId;
        this.size = size;
    }

    protected boolean beforeTransmit() {
        setTargetName(originatingInstance);
        return originatingInstance != null;
    }

    public Object getCommandKey() {
        return "SizeResp:" + tokenId;
    }

    private void writeObject(ObjectOutputStream ros) throws IOException {

        ros.writeLong(tokenId);
        ros.writeInt(size);
        ros.writeUTF(dsc.getInstanceName());
    }

    private void readObject(ObjectInputStream ris) throws IOException {

        tokenId = ris.readLong();
        size = ris.readInt();
        respondingInstanceName = ris.readUTF();
    }

    @Override
    public void execute(String initiator) {

        ResponseMediator respMed = getDataStoreContext().getResponseMediator();
        CommandResponse resp = respMed.getCommandResponse(tokenId);
        if (resp != null) {
            if (_logger.isLoggable(Level.FINE)) {
                _logger.log(Level.FINE, dsc.getInstanceName() + " executed size_response tokenId=" + tokenId + " value " + size);
            }

            resp.setRespondingInstanceName(respondingInstanceName);
            resp.setResult(size);
        }
    }

    @Override
    protected boolean isArtificialKey() {
        return true;
    }

    public String toString() {
        return getName() + "(" + tokenId + ")";
    }
}
