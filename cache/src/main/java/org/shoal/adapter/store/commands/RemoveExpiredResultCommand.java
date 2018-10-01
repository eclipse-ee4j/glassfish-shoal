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
import org.shoal.ha.cache.impl.util.ResponseMediator;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Mahesh Kannan
 */
public class RemoveExpiredResultCommand<K, V> extends Command<String, V> {

	protected static final Logger _logger = Logger.getLogger(ShoalCacheLoggerConstants.CACHE_REMOVE_COMMAND);

	private String target;

	private long tokenId;

	private int result = 0;

	public RemoveExpiredResultCommand(String target, long tokenId, int result) {
		super(ReplicationCommandOpcode.REMOVE_EXPIRED_RESULT);
		this.target = target;
		this.tokenId = tokenId;
		this.result = result;

		super.setKey("RemExpResp:" + tokenId);
	}

	public boolean beforeTransmit() {
		setTargetName(target);
		return target != null;
	}

	private void writeObject(ObjectOutputStream ros) throws IOException {
		ros.writeLong(tokenId);
		ros.writeInt(result);
	}

	private void readObject(ObjectInputStream ris) throws IOException, ClassNotFoundException {
		tokenId = ris.readLong();
		result = ris.readInt();
	}

	@Override
	public void execute(String initiator) {
		ResponseMediator respMed = getDataStoreContext().getResponseMediator();
		CommandResponse resp = respMed.getCommandResponse(tokenId);
		if (resp != null) {
			if (_logger.isLoggable(Level.FINE)) {
				_logger.log(Level.FINE, dsc.getInstanceName() + "For tokenId = " + tokenId + " received remove_expired_response value=" + result);
			}

			int pendingUpdates = 0;
			synchronized (resp) {
				Integer existingValue = (Integer) resp.getTransientResult();
				Integer newResult = new Integer(existingValue.intValue() + result);
				resp.setTransientResult(newResult);
				pendingUpdates = resp.decrementAndGetExpectedUpdateCount();
			}

			if (pendingUpdates == 0) {
				resp.setResult(resp.getTransientResult());
			}
		} else {
			_logger.log(Level.FINE, "RemoveExpiredResult: TOKEN already removed for tokenId = " + tokenId);
		}
	}

	@Override
	protected boolean isArtificialKey() {
		return true;
	}

	public String toString() {
		return getName() + "(result=" + result + ")";
	}
}
