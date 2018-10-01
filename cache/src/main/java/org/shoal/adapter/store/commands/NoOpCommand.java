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

import org.shoal.ha.cache.api.DataStoreException;
import org.shoal.ha.cache.impl.command.Command;
import org.shoal.ha.cache.impl.command.ReplicationCommandOpcode;

/**
 * @author Mahesh Kannan
 */
public class NoOpCommand<K, V> extends Command {

    /**
     *
     */
    private static final long serialVersionUID = 3353080048287174569L;

    private transient static final byte[] rawReadState = new byte[] { ReplicationCommandOpcode.NOOP_COMMAND, (byte) 123 };

    private transient static final NoOpCommand _noopCommand = new NoOpCommand();

    public NoOpCommand() {
        super(ReplicationCommandOpcode.NOOP_COMMAND);
        super.setKey("Noop" + System.identityHashCode(this));
    }

    public boolean beforeTransmit() {
        return true;
    }

    public Object getCommandKey() {
        return "Noop" + System.identityHashCode(this);
    }

    @Override
    public void execute(String initiator) throws DataStoreException {
    }

    public String toString() {
        return getName();
    }

    @Override
    public String getKeyMappingInfo() {
        return null;
    }

    @Override
    protected boolean isArtificialKey() {
        return true;
    }
}
