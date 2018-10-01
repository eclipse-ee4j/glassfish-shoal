/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package org.shoal.ha.cache.impl.store;

import java.util.Iterator;
import java.util.TreeSet;
import java.util.logging.Level;

import org.shoal.adapter.store.commands.AbstractSaveCommand;
import org.shoal.adapter.store.commands.LoadResponseCommand;
import org.shoal.adapter.store.commands.SaveCommand;
import org.shoal.adapter.store.commands.TouchCommand;
import org.shoal.ha.cache.api.DataStoreException;

/**
 * An entry updater used for plain Serializable POJOs
 *
 * @author Mahesh Kannan
 */
public class SimpleDataStoreEntryUpdater<K, V> extends DataStoreEntryUpdater<K, V> {

	@Override
	public SaveCommand<K, V> createSaveCommand(DataStoreEntry<K, V> entry, K k, V v) {
		SaveCommand<K, V> cmd = new SaveCommand<K, V>(k, v, entry.incrementAndGetVersion(), System.currentTimeMillis(), ctx.getDefaultMaxIdleTimeInMillis());

		// Update this entry's meta info
		super.updateMetaInfoInDataStoreEntry(entry, cmd);
		entry.setIsReplicaNode(false);

		return cmd;
	}

	@Override
	public LoadResponseCommand<K, V> createLoadResponseCommand(DataStoreEntry<K, V> entry, K k, long minVersion) {
		LoadResponseCommand<K, V> cmd = null;
		if (entry != null && entry.isReplicaNode() && entry.getVersion() >= minVersion) {
			if (_logger.isLoggable(Level.FINE)) {
				_logger.log(Level.FINE, "SimpleDataStoreEntryUpdater.createLoadResp " + " entry.version " + entry.getVersion() + ">= " + minVersion
				        + "; rawV.length = " + entry.getRawV());
			}
			cmd = new LoadResponseCommand<K, V>(k, entry.getVersion(), entry.getRawV());
		} else {
			if (_logger.isLoggable(Level.FINE)) {
				String entryMsg = (entry == null) ? "NULL ENTRY" : (entry.getVersion() + " < " + minVersion);
				_logger.log(Level.FINE,
				        "SimpleDataStoreEntryUpdater.createLoadResp " + entryMsg + "; rawV.length = " + (entry == null ? " null " : "" + entry.getRawV()));
			}
			cmd = new LoadResponseCommand<K, V>(k, Long.MIN_VALUE, null);
		}
		return cmd;
	}

	@Override
	public void executeSave(DataStoreEntry<K, V> entry, SaveCommand<K, V> cmd) {
		if (entry != null && entry.getVersion() < cmd.getVersion()) {
			if (_logger.isLoggable(Level.FINE)) {
				_logger.log(Level.FINE, "SimpleDataStoreEntryUpdater.executeSave. SAVING ... " + "entry = " + entry + "; entry.version = " + entry.getVersion()
				        + "; cmd.version = " + cmd.getVersion() + "; cmd.maxIdle = " + cmd.getMaxIdleTime());
			}
			entry.setIsReplicaNode(true);
			super.updateMetaInfoInDataStoreEntry(entry, cmd);
			entry.setRawV(cmd.getRawV());
			updateFromPendingUpdates(entry);
//            super.printEntryInfo("Updated", entry, cmd.getKey());
		} else {
			if (_logger.isLoggable(Level.FINE)) {
				_logger.log(Level.FINE, "SimpleDataStoreEntryUpdater.executeSave. IGNORING ... " + "entry = " + entry + "; entry.version = "
				        + entry.getVersion() + "; cmd.version = " + cmd.getVersion());
			}
		}
	}

	@Override
	public void executeTouch(DataStoreEntry<K, V> entry, TouchCommand<K, V> touchCmd) throws DataStoreException {

		entry.addPendingUpdate(touchCmd);
		// TODO: For 'full save' mode there is no need to keep multiple touch commands
		updateFromPendingUpdates(entry);
		entry.setIsReplicaNode(true);
	}

	private void updateFromPendingUpdates(DataStoreEntry<K, V> entry) {
		TreeSet<AbstractSaveCommand<K, V>> pendingUpdates = entry.getPendingUpdates();
		if (pendingUpdates != null) {
			Iterator<AbstractSaveCommand<K, V>> iter = entry.getPendingUpdates().iterator();

			while (iter.hasNext()) {
				AbstractSaveCommand<K, V> pendingCmd = iter.next();
				if (entry.getVersion() > pendingCmd.getVersion()) {
					iter.remove();
					if (_logger.isLoggable(Level.FINE)) {
						_logger.log(Level.FINE, "**Ignoring Pending touch because " + entry.getVersion() + " > " + pendingCmd.getVersion());
					}
				} else if (entry.getVersion() + 1 == pendingCmd.getVersion()) {
					iter.remove();
					super.updateMetaInfoInDataStoreEntry(entry, pendingCmd);
					if (_logger.isLoggable(Level.FINE)) {
						_logger.log(Level.FINE, "**Updated with Pending touch because, cmd.version = " + entry.getVersion() + " & pending.version = "
						        + pendingCmd.getVersion());
					}
				} else {
					if (_logger.isLoggable(Level.FINE)) {
						_logger.log(Level.FINE,
						        "**Added Touch as pending because, cmd.version = " + entry.getVersion() + " & pending.version = " + pendingCmd.getVersion());
					}
					break;
				}
			}
		}
	}

	@Override
	public V getV(DataStoreEntry<K, V> entry) throws DataStoreException {
		V v = entry.getV();
		if (entry != null && v == null && entry.getRawV() != null) {
			if (_logger.isLoggable(Level.FINE)) {
				_logger.log(Level.FINE, "SimpleDataStoreEntryUpdater.getV(): Reading from raw data: " + entry.getRawV().length);
			}

			v = super.deserializeV(entry.getRawV());
		}

		return v;
	}

	@Override
	public byte[] getState(V v) throws DataStoreException {
		return captureState(v);
	}

	@Override
	public V extractVFrom(LoadResponseCommand<K, V> cmd) throws DataStoreException {
		byte[] rawV = cmd.getRawV();
		return rawV == null ? null : super.deserializeV(rawV);
	}
}
