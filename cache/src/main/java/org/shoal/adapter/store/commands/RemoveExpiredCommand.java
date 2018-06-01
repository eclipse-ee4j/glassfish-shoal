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

import org.shoal.ha.cache.api.ShoalCacheLoggerConstants;
import org.shoal.ha.cache.impl.command.Command;
import org.shoal.ha.cache.impl.command.ReplicationCommandOpcode;
import org.shoal.ha.cache.impl.util.CommandResponse;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Mahesh Kannan
 */
public class RemoveExpiredCommand<K, V>
    extends Command {

    protected static final Logger _logger = Logger.getLogger(ShoalCacheLoggerConstants.CACHE_REMOVE_COMMAND);

    private long maxIdleInMillis;

    private long tokenId;

    private String target;

    public RemoveExpiredCommand(long maxIdleInMillis, long tokenId) {
        super(ReplicationCommandOpcode.REMOVE_EXPIRED);
        this.maxIdleInMillis = maxIdleInMillis;
        this.tokenId = tokenId;

        super.setKey("RemExpired" + System.identityHashCode(this));
    }

    public void setTarget(String t) {
        this.target = t;
    }

    public boolean beforeTransmit() {
        setTargetName(target);
        return target != null;
    }

    public Object getCommandKey() {
        return "RemExpired" + System.identityHashCode(this);
    }
    
    private void writeObject(ObjectOutputStream ros) throws IOException {
        ros.writeLong(maxIdleInMillis);
        ros.writeLong(tokenId);
        ros.writeUTF(dsc.getInstanceName());
    }

    private void readObject(ObjectInputStream ris)
        throws IOException, ClassNotFoundException {
        maxIdleInMillis = ris.readLong();
        tokenId = ris.readLong();
        target = ris.readUTF();
    }

    @Override
    public void execute(String initiator) {
        int localResult = dsc.getReplicaStore().removeExpired();
        RemoveExpiredResultCommand<K, V> resultCmd = new RemoveExpiredResultCommand<K, V>(target, tokenId, localResult);
        try {
            dsc.getCommandManager().execute(resultCmd);
        } catch (Exception ex) {
            _logger.log(Level.WARNING, "Exception while trying to send result for remove_expired", ex);
        }
    }

    @Override
    protected boolean isArtificialKey() {
        return true;
    }

    public String toString() {
        return getName() + "(" + maxIdleInMillis + ")";
    }
}
