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

package org.shoal.ha.cache.store.backing.commands;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.shoal.ha.cache.api.DataStoreException;
import org.shoal.ha.cache.api.ShoalCacheLoggerConstants;
import org.shoal.ha.cache.command.Command;
import org.shoal.ha.cache.command.ReplicationCommandOpcode;
import org.shoal.ha.cache.util.CommandResponse;
import org.shoal.ha.cache.util.ResponseMediator;

/**
 * @author Mahesh Kannan
 */
public class LoadResponseCommand<K, V> extends Command<K, V> {

    private static final Logger _logger = Logger.getLogger(ShoalCacheLoggerConstants.CACHE_LOAD_RESPONSE_COMMAND);

    private long version;

    private byte[] rawV;

    private long tokenId;

    private String originatingInstance;

    private String respondingInstanceName;

    public LoadResponseCommand() {
        super(ReplicationCommandOpcode.LOAD_RESPONSE);
    }

    public LoadResponseCommand(K key, long version, byte[] rawV) {
        this();
        super.setKey(key);
        this.version = version;
        this.rawV = rawV;
    }

    public void setTokenId(long tokenId) {
        this.tokenId = tokenId;
    }

    public long getVersion() {
        return version;
    }

    public void setOriginatingInstance(String originatingInstance) {
        this.originatingInstance = originatingInstance;
    }

    public byte[] getRawV() {
        return rawV;
    }

    protected boolean beforeTransmit() {
        setTargetName(originatingInstance);
        return originatingInstance != null;
    }

    private void writeObject(ObjectOutputStream out) throws IOException {

        out.writeLong(version);
        out.writeLong(tokenId);
        out.writeUTF(originatingInstance);
        out.writeUTF(dsc.getInstanceName());

        out.writeBoolean(rawV != null);
        if (rawV != null) {
            out.writeInt(rawV.length);
            out.write(rawV);
        }
        if (_logger.isLoggable(Level.FINE)) {
            _logger.log(Level.FINE, dsc.getInstanceName() + getName() + " sending load_response command for " + getKey() + " to " + originatingInstance
                    + "; version = " + version + "; state = " + (rawV == null ? "NOT_FOUND" : rawV.length));
        }
    }

    private void readObject(ObjectInputStream ris) throws IOException {
        version = ris.readLong();
        tokenId = ris.readLong();
        originatingInstance = ris.readUTF();
        respondingInstanceName = ris.readUTF();
        boolean notNull = ris.readBoolean();
        if (notNull) {
            int vLen = ris.readInt();
            rawV = new byte[vLen];
            ris.readFully(rawV);
        }
    }

    @Override
    public void execute(String initiator) throws DataStoreException {

        ResponseMediator respMed = getDataStoreContext().getResponseMediator();
        CommandResponse resp = respMed.getCommandResponse(tokenId);
        if (resp != null) {
            if (_logger.isLoggable(Level.FINE)) {
                _logger.log(Level.FINE,
                        dsc.getInstanceName() + " received load_response key=" + getKey() + "; version=" + version + "; from " + respondingInstanceName);
            }

            resp.setRespondingInstanceName(respondingInstanceName);
            resp.setResult(this);
        }
    }

    public String toString() {
        return getName() + "(" + getKey() + ")";
    }
}
