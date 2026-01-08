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
import java.util.logging.Level;

import org.shoal.ha.cache.api.DataStoreException;
import org.shoal.ha.cache.command.ReplicationCommandOpcode;
import org.shoal.ha.cache.store.DataStoreEntry;

/**
 * @author Mahesh Kannan
 */
public class SaveCommand<K, V> extends AbstractSaveCommand<K, V> {

   
    private static final long serialVersionUID = -1681470355087702983L;

    private transient V v;

    private transient byte[] rawV;

    public SaveCommand() {
        super(ReplicationCommandOpcode.SAVE);
    }

    public SaveCommand(K k, V v, long version, long lastAccessedAt, long maxIdleTime) {
        super(ReplicationCommandOpcode.SAVE, k, version, lastAccessedAt, maxIdleTime);
        this.v = v;
    }

    @Override
    public void execute(String initiator) throws DataStoreException {

        if (_logger.isLoggable(Level.FINE)) {
            _logger.log(Level.FINE,
                    dsc.getServiceName() + getName() + " received save_command for key = " + getKey() + " from " + initiator + "; version = " + getVersion());
        }

        DataStoreEntry<K, V> entry = dsc.getReplicaStore().getOrCreateEntry(getKey());
        synchronized (entry) {
            dsc.getDataStoreEntryUpdater().executeSave(entry, this);
        }

        if (dsc.isDoSynchronousReplication()) {
            _logger.log(Level.FINE, "SaveCommand Sending SIMPLE_ACK");
            super.sendAcknowledgement();
        }

        dsc.getDataStoreMBean().incrementExecutedSaveCount();
    }

    public String toString() {
        return getName() + "(" + getKey() + ")";
    }

    private void writeObject(java.io.ObjectOutputStream out) throws IOException {

        rawV = dsc.getDataStoreEntryUpdater().getState(v);
        out.writeObject(rawV);

        if (_logger.isLoggable(Level.FINE)) {
            _logger.log(Level.FINE, dsc.getServiceName() + " sending save_command for key = " + getKey() + "; version = " + version + "; lastAccessedAt = "
                    + lastAccessedAt + "; to " + getTargetName());
        }
    }

    public boolean hasState() {
        return true;
    }

    public byte[] getRawV() {
        return rawV;
    }

    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {

        rawV = (byte[]) in.readObject();
    }

}
