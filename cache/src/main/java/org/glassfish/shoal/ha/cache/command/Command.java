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

package org.glassfish.shoal.ha.cache.command;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.ha.store.util.KeyTransformer;
import org.glassfish.shoal.ha.cache.api.DataStoreContext;
import org.glassfish.shoal.ha.cache.api.DataStoreException;
import org.glassfish.shoal.ha.cache.api.ShoalCacheLoggerConstants;

/**
 * @author Mahesh Kannan
 *
 */
public abstract class Command<K, V> implements Serializable {

   
    private static final long serialVersionUID = 6608726132108978791L;

    private transient static final Logger _logger = Logger.getLogger(ShoalCacheLoggerConstants.CACHE_SAVE_COMMAND);

    private byte opcode;

    private byte[] rawKey;

    private transient K key;

    protected transient DataStoreContext<K, V> dsc;

    private transient CommandManager<K, V> cm;

    private transient String commandName;

    protected transient String targetInstanceName;

    protected Command(byte opcode) {
        this.opcode = opcode;
    }

    public final void initialize(DataStoreContext<K, V> rs) {
        this.dsc = rs;
        this.cm = rs.getCommandManager();

        this.commandName = this.getClass().getName();
        int index = commandName.lastIndexOf('.');
        commandName = commandName.substring(index + 1);
    }

    protected final void setKey(K k) {
        this.key = k;
    }

    public final K getKey() {
        if (key == null && rawKey != null) {
            KeyTransformer<K> kt = dsc.getKeyTransformer();
            if (kt != null) {
                key = kt.byteArrayToKey(rawKey, 0, rawKey.length);
            }
        }

        return key;
    }

    protected final DataStoreContext<K, V> getDataStoreContext() {
        return dsc;
    }

    protected final CommandManager<K, V> getCommandManager() {
        return cm;
    }

    public String getTargetName() {
        return targetInstanceName;
    }

    public final byte getOpcode() {
        return opcode;
    }

    protected void setTargetName(String val) {
        this.targetInstanceName = val;
    }

    public void prepareTransmit(DataStoreContext<K, V> ctx) throws IOException {

        if (!beforeTransmit()) {
            _logger.log(Level.FINE, "Aborting command transmission for " + getName() + " because beforeTransmit returned false");
        }
    }

    protected static byte[] captureState(Object obj) throws DataStoreException {
        byte[] result = null;
        ByteArrayOutputStream bos = null;
        ObjectOutputStream oos = null;
        try {
            bos = new ByteArrayOutputStream();
            oos = new ObjectOutputStream(bos);
            oos.writeObject(obj);
            oos.close();

            result = bos.toByteArray();
        } catch (Exception ex) {
            throw new DataStoreException("Error during prepareToTransmit()", ex);
        } finally {
            try {
                oos.close();
            } catch (Exception ex) {
            }
            try {
                bos.close();
            } catch (Exception ex) {
            }
        }

        return result;
    }

    public String getKeyMappingInfo() {
        return targetInstanceName == null ? "" : targetInstanceName;
    }

    public final String getName() {
        return commandName + ":" + opcode;
    }

    public abstract void execute(String initiator) throws DataStoreException;

    private void writeObject(java.io.ObjectOutputStream out) throws IOException {
        out.writeByte(opcode);
        writeKey(out);
    }

    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        opcode = in.readByte();
        readKey(in);
    }

    public void onSuccess() {

    }

    public void onFailure() {

    }

    public String toString() {
        return getName() + "(" + getKey() + ")";
    }

    private void writeKey(ObjectOutputStream out) throws IOException {
        if (isArtificialKey()) {
            out.writeObject(key);
        } else {
            KeyTransformer<K> kt = dsc.getKeyTransformer();
            out.writeBoolean(kt != null);
            if (kt != null) {
                out.writeObject(kt.keyToByteArray(key));
            } else {
                out.writeObject(key);
            }
        }
    }

    private void readKey(ObjectInputStream in) throws IOException, ClassNotFoundException {
        if (isArtificialKey()) {
            key = (K) in.readObject();
        } else {
            boolean needToTransformKey = in.readBoolean();
            if (!needToTransformKey) {
                key = (K) in.readObject();
            } else {
                rawKey = (byte[]) in.readObject();
            }
        }
    }

    protected boolean isArtificialKey() {
        return false;
    }

    protected abstract boolean beforeTransmit() throws IOException;

}
