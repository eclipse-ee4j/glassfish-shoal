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

package org.shoal.ha.cache.interceptor;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.ha.store.util.KeyTransformer;
import org.shoal.ha.cache.api.DataStoreException;
import org.shoal.ha.cache.api.ShoalCacheLoggerConstants;
import org.shoal.ha.cache.command.Command;
import org.shoal.ha.cache.command.ReplicationCommandOpcode;

/**
 * @author Mahesh Kannan
 */
public class ReplicationFramePayloadCommand<K, V> extends Command {

   
    private static final long serialVersionUID = -7673740871785789916L;

    private transient static final Logger _logger = Logger.getLogger(ShoalCacheLoggerConstants.CACHE_REPLICATION_FRAME_COMMAND);

    private String targetInstanceName;

    private List<Command<K, V>> commands = new ArrayList<Command<K, V>>();

    private Collection<K> removedKeys = new ArrayList<K>();

    private List<byte[]> rawRemovedKeys = new ArrayList<byte[]>();

    public ReplicationFramePayloadCommand() {
        super(ReplicationCommandOpcode.REPLICATION_FRAME_PAYLOAD);
        setKey("RepFP:" + System.identityHashCode(this));
    }

    public void addComamnd(Command<K, V> cmd) {
        commands.add(cmd);
    }

    public void setTargetInstance(String target) {
        targetInstanceName = target;
    }

    void setRemovedKeys(Collection<K> removedKeys) {
        this.removedKeys = removedKeys;
    }

    protected boolean beforeTransmit() throws DataStoreException {
        setTargetName(targetInstanceName);
        return targetInstanceName != null;
    }

    private void writeObject(ObjectOutputStream ros) throws IOException {
        try {
            ros.writeObject(commands);
            ros.writeBoolean(dsc.getKeyTransformer() == null);
            if (dsc.getKeyTransformer() == null) {
                ros.writeObject(removedKeys);
            } else {
                KeyTransformer<K> kt = dsc.getKeyTransformer();
                int sz = removedKeys.size();
                rawRemovedKeys = new ArrayList<byte[]>();
                for (K k : removedKeys) {
                    rawRemovedKeys.add(kt.keyToByteArray(k));
                }

                ros.writeObject(rawRemovedKeys);
            }
        } catch (IOException ioEx) {
            _logger.log(Level.INFO, "Error during ReplicationFramePayloadCommand.writeObject ", ioEx);
            throw ioEx;
        }
    }

    private void readObject(ObjectInputStream ris) throws IOException, ClassNotFoundException {
        try {
            commands = (List<Command<K, V>>) ris.readObject();
            boolean ktAbsent = ris.readBoolean();
            if (ktAbsent) {
                removedKeys = (Collection<K>) ris.readObject();
            } else {
                rawRemovedKeys = (List<byte[]>) ris.readObject();
            }
        } catch (IOException ioEx) {
            _logger.log(Level.INFO, "Error during ReplicationFramePayloadCommand.readObject ", ioEx);
            throw ioEx;
        }

    }

    @Override
    public void execute(String initiator) throws DataStoreException {
        /*
         * int sz = list.size(); commands = new ArrayList<Command<K, V>>(); for (int i = 0; i < sz; i++) { ByteArrayInputStream
         * bis = null; ObjectInputStreamWithLoader ois = null; try { bis = new ByteArrayInputStream(list.get(i)); ois = new
         * ObjectInputStreamWithLoader(bis, dsc.getClassLoader()); Command<K, V> cmd = (Command<K, V>) ois.readObject();
         *
         * commands.add(cmd); cmd.initialize(dsc); } catch (Exception ex) { _logger.log(Level.WARNING, "Error during execute ",
         * ex); } finally { try { ois.close(); } catch (Exception ex) {} try { bis.close(); } catch (Exception ex) {} } }
         */

        if (rawRemovedKeys != null) {
            KeyTransformer<K> kt = dsc.getKeyTransformer();
            removedKeys = new ArrayList<K>();
            for (byte[] bytes : rawRemovedKeys) {
                K k = kt.byteArrayToKey(bytes, 0, bytes.length);
                removedKeys.add(k);
            }
        }

        for (Command<K, V> cmd : commands) {
            cmd.initialize(dsc);
            getCommandManager().executeCommand(cmd, false, initiator);
        }

        int executedRemoveCount = 0;
        if (removedKeys != null) {
            for (K k : removedKeys) {
                dsc.getReplicaStore().remove(k);
                executedRemoveCount++;
            }

            if (dsc.getDataStoreMBean() != null) {
                dsc.getDataStoreMBean().updateExecutedRemoveCount(executedRemoveCount);
            }
        }
    }

    @Override
    public void onFailure() {
        int sz = commands.size();
        for (int i = 0; i < sz; i++) {
            Command cmd = commands.get(i);
            cmd.onFailure();
        }
    }

    @Override
    protected boolean isArtificialKey() {
        return true;
    }

    public String toString() {
        return "ReplicationFramePayloadCommand: contains " + commands.size() + " commands";
    }
}
