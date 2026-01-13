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

package org.glassfish.shoal.ha.cache.store.backing.commands;

import java.util.logging.Logger;

import org.glassfish.shoal.ha.cache.api.ShoalCacheLoggerConstants;
import org.glassfish.shoal.ha.cache.command.ReplicationCommandOpcode;

/**
 * @author Mahesh Kannan
 */
public class RemoveCommand<K, V> extends AcknowledgedCommand<K, V> {

   
    private static final long serialVersionUID = -8067458552974086794L;

    protected static final Logger _logger = Logger.getLogger(ShoalCacheLoggerConstants.CACHE_REMOVE_COMMAND);

    private String target;

    public RemoveCommand(K k) {
        super(ReplicationCommandOpcode.REMOVE);
        super.setKey(k);
    }

    public void setTarget(String t) {
        this.target = t;
    }

    public boolean beforeTransmit() {
        setTargetName(target);
        super.beforeTransmit();
        return target != null;
    }

    @Override
    public void execute(String initiator) {
        dsc.getReplicaStore().remove(getKey());
        if (dsc.isDoSynchronousReplication()) {
            super.sendAcknowledgement();
        }

        dsc.getDataStoreMBean().incrementExecutedRemoveCount();
    }

}
