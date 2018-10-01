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
import org.shoal.ha.cache.impl.util.*;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Mahesh Kannan
 */
public class LoadRequestCommand<K, V> extends Command<K, V> {

	private static final Logger _logger = Logger.getLogger(ShoalCacheLoggerConstants.CACHE_LOAD_REQUEST_COMMAND);

	private transient CommandResponse resp;

	private transient Future future;

	private long minVersion = -1;

	private long tokenId;

	private String originatingInstance;

	private String target;

	public LoadRequestCommand() {
		super(ReplicationCommandOpcode.LOAD_REQUEST);
	}

	public LoadRequestCommand(K key, long minVersion, String t) {
		this();
		super.setKey(key);
		this.minVersion = minVersion;
		this.target = t;
	}

	protected boolean beforeTransmit() {
		setTargetName(target);
		originatingInstance = dsc.getInstanceName();
		ResponseMediator respMed = dsc.getResponseMediator();
		resp = respMed.createCommandResponse();

		future = resp.getFuture();

		return target != null;
	}

	private void writeObject(java.io.ObjectOutputStream out) throws IOException {
		out.writeLong(minVersion);
		out.writeLong(resp.getTokenId());
		out.writeUTF(originatingInstance);
		if (_logger.isLoggable(Level.FINE)) {
			_logger.log(Level.FINE, dsc.getInstanceName() + getName() + " sending load_request command for " + getKey() + "to " + target);
		}
	}

	private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
		minVersion = in.readLong();
		tokenId = in.readLong();
		originatingInstance = in.readUTF();
	}

	@Override
	public void execute(String initiator) {

		try {
			if (_logger.isLoggable(Level.FINE)) {
				_logger.log(Level.FINE, dsc.getInstanceName() + getName() + " received load_request command for " + getKey() + "from " + initiator);
			}
			DataStoreEntry<K, V> e = dsc.getReplicaStore().getEntry(getKey());

			if (e != null) {
				synchronized (e) {
					if (!originatingInstance.equals(dsc.getInstanceName())) {
						LoadResponseCommand<K, V> rsp = dsc.getDataStoreEntryUpdater().createLoadResponseCommand(e, getKey(), minVersion);
						rsp.setTokenId(tokenId);
						rsp.setOriginatingInstance(originatingInstance);

						getCommandManager().execute(rsp);
					} else {
						resp.setResult(dsc.getDataStoreEntryUpdater().getV(e));
					}
				}
			} else {
				if (!originatingInstance.equals(dsc.getInstanceName())) {
					LoadResponseCommand<K, V> rsp = dsc.getDataStoreEntryUpdater().createLoadResponseCommand(null, getKey(), minVersion);
					rsp.setTokenId(tokenId);
					rsp.setOriginatingInstance(originatingInstance);

					getCommandManager().execute(rsp);
				} else {
					resp.setResult(null);
				}
			}

		} catch (DataStoreException dsEx) {
			resp.setException(dsEx);
		}
	}

	public String getRespondingInstanceName() {
		return resp.getRespondingInstanceName();
	}

	public V getResult(long waitFor, TimeUnit unit) throws DataStoreException {
		try {
			Object result = future.get(waitFor, unit);
			if (result instanceof Exception) {
				throw new DataStoreException((Exception) result);
			}
			LoadResponseCommand<K, V> respCmd = (LoadResponseCommand<K, V>) result;
			if (respCmd.getVersion() >= minVersion) {
				result = dsc.getDataStoreEntryUpdater().extractVFrom(respCmd);
			} else {
				result = null;
			}
			return (V) result;
		} catch (DataStoreException dsEx) {
			throw dsEx;
		} catch (InterruptedException inEx) {
			_logger.log(Level.WARNING, "LoadRequestCommand Interrupted while waiting for result", inEx);
			throw new DataStoreException(inEx);
		} catch (TimeoutException timeoutEx) {
			_logger.log(Level.WARNING, "LoadRequestCommand timed out while waiting for result " + timeoutEx);
			return null;
		} catch (ExecutionException exeEx) {
			_logger.log(Level.WARNING, "LoadRequestCommand got an exception while waiting for result", exeEx);
			throw new DataStoreException(exeEx);
		}
	}

	public String toString() {
		return getName() + "(" + getKey() + ")";
	}

}
