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

import org.shoal.ha.cache.impl.store.DataStoreEntry;
import org.shoal.ha.cache.api.DataStoreException;
import org.shoal.ha.cache.api.ShoalCacheLoggerConstants;
import org.shoal.ha.cache.impl.command.Command;
import org.shoal.ha.cache.impl.command.ReplicationCommandOpcode;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Mahesh Kannan
 */
public class TouchCommand<K, V> extends AbstractSaveCommand<K, V> {

	public TouchCommand() {
		super(ReplicationCommandOpcode.TOUCH);
	}

	public TouchCommand(K k, long version, long accessTime, long maxIdleTime) {
		super(ReplicationCommandOpcode.TOUCH, k, version, accessTime, maxIdleTime);
	}

	@Override
	public void execute(String initiator) throws DataStoreException {

		if (_logger.isLoggable(Level.FINE)) {
			_logger.log(Level.FINE, dsc.getServiceName() + getName() + " received touch_command for key = " + getKey() + " from " + initiator);
		}

		if (_logger.isLoggable(Level.FINE)) {
			_logger.log(Level.FINE, dsc.getServiceName() + getName() + " received touch_command for key = " + getKey() + " from " + initiator + "; version = "
			        + getVersion() + "; " + dsc.getDataStoreEntryUpdater().getClass().getCanonicalName());
		}

		DataStoreEntry<K, V> entry = dsc.getReplicaStore().getOrCreateEntry(getKey());
		synchronized (entry) {
			dsc.getDataStoreEntryUpdater().executeTouch(entry, this);
		}

		if (dsc.isDoSynchronousReplication()) {
			_logger.log(Level.FINE, "TouchCommand Sending SIMPLE_ACK");
			super.sendAcknowledgement();
		}
	}
}
