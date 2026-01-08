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

import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.shoal.ha.cache.api.AbstractCommandInterceptor;
import org.shoal.ha.cache.api.DataStoreContext;
import org.shoal.ha.cache.api.DataStoreException;
import org.shoal.ha.cache.api.ShoalCacheLoggerConstants;
import org.shoal.ha.cache.command.Command;
import org.shoal.ha.cache.command.ReplicationCommandOpcode;
import org.shoal.ha.cache.store.backing.commands.NoOpCommand;

/**
 * @author Mahesh Kannan
 *
 */
public class ReplicationCommandTransmitterManager<K, V> extends AbstractCommandInterceptor<K, V> {

    private static final Logger _logger = Logger.getLogger(ShoalCacheLoggerConstants.CACHE_COMMAND);

    private ConcurrentHashMap<String, CommandCollector<K, V>> transmitters = new ConcurrentHashMap<String, CommandCollector<K, V>>();

    private CommandCollector<K, V> broadcastTransmitter;

    public ReplicationCommandTransmitterManager() {
    }

    @Override
    public void initialize(DataStoreContext<K, V> dsc) {
        super.initialize(dsc);
        broadcastTransmitter = new ReplicationCommandTransmitterWithList<K, V>();
        broadcastTransmitter.initialize(null, dsc);

        _logger.log(Level.FINE, "ReplicationCommandTransmitterManager(" + dsc.getServiceName() + ") instantiated with: " + dsc.isUseMapToCacheCommands() + " : "
                + dsc.isSafeToDelayCaptureState());
    }

    @Override
    public void onTransmit(Command<K, V> cmd, String initiator) throws DataStoreException {
        switch (cmd.getOpcode()) {
        case ReplicationCommandOpcode.REPLICATION_FRAME_PAYLOAD:
            super.onTransmit(cmd, initiator);
            break;

        default:
            String target = cmd.getTargetName();
            if (target != null) {
                CommandCollector<K, V> rft = transmitters.get(target);
                if (rft == null) {
                    rft = dsc.isUseMapToCacheCommands() ? new ReplicationCommandTransmitterWithMap<K, V>() : new ReplicationCommandTransmitterWithList<K, V>();
                    rft.initialize(target, getDataStoreContext());
                    CommandCollector oldRCT = transmitters.putIfAbsent(target, rft);
                    if (oldRCT != null) {
                        rft = oldRCT;
                    }
                }
                if (cmd.getOpcode() == ReplicationCommandOpcode.REMOVE) {
                    rft.removeCommand(cmd);
                } else {
                    rft.addCommand(cmd);
                }
            } else {
                broadcastTransmitter.addCommand(cmd);
            }
            break;
        }
    }

    public void close() {
        for (CommandCollector<K, V> cc : transmitters.values()) {
            cc.close();
        }

        try {
            broadcastTransmitter.addCommand(new NoOpCommand());
        } catch (DataStoreException dsEx) {
        }
        broadcastTransmitter.close();
    }

}
