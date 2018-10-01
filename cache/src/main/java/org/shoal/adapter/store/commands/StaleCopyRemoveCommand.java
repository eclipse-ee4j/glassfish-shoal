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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Mahesh Kannan
 */
public class StaleCopyRemoveCommand<K, V> extends Command<K, V> {

	protected static final Logger _logger = Logger.getLogger(ShoalCacheLoggerConstants.CACHE_STALE_REMOVE_COMMAND);

	private String staleTargetName;

	public StaleCopyRemoveCommand() {
		super(ReplicationCommandOpcode.STALE_REMOVE);
	}

	public StaleCopyRemoveCommand(K k) {
		this();
		super.setKey(k);
	}

	public String getStaleTargetName() {
		return staleTargetName;
	}

	public void setStaleTargetName(String targetName) {
		this.staleTargetName = targetName;
	}

	@Override
	protected boolean beforeTransmit() {
		setTargetName(staleTargetName);
		return staleTargetName != null;
	}

	@Override
	public void execute(String initiator) {
		dsc.getReplicaStore().remove(getKey());
		if (_logger.isLoggable(Level.FINE)) {
			_logger.log(Level.FINE, "*********************  REMOVED STALE REPLICA: " + getKey() + "   ** SENT BY: " + initiator);
		}
	}

}
