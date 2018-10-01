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
import org.shoal.ha.cache.api.ShoalCacheLoggerConstants;
import org.shoal.ha.cache.impl.command.Command;
import org.shoal.ha.cache.impl.command.ReplicationCommandOpcode;
import org.shoal.ha.cache.impl.util.CommandResponse;
import org.shoal.ha.cache.impl.util.ResponseMediator;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * @author Mahesh Kannan
 */
public class SizeRequestCommand<K, V> extends Command {

	private static final Logger _logger = Logger.getLogger(ShoalCacheLoggerConstants.CACHE_SIZE_REQUEST_COMMAND);

	private long tokenId;

	private String targetInstanceName;

	private Future future;

	public SizeRequestCommand() {
		super(ReplicationCommandOpcode.SIZE_REQUEST);
		super.setKey("SizeReq:" + tokenId);
	}

	public SizeRequestCommand(String targetInstanceName) {
		this();

		this.targetInstanceName = targetInstanceName;
	}

	protected boolean beforeTransmit() {
		ResponseMediator respMed = dsc.getResponseMediator();
		CommandResponse resp = respMed.createCommandResponse();
		tokenId = resp.getTokenId();
		future = resp.getFuture();

		setTargetName(targetInstanceName);
		return targetInstanceName != null;
	}

	private void writeObject(ObjectOutputStream ros) throws IOException {

		ros.writeUTF(dsc.getInstanceName());
		ros.writeUTF(targetInstanceName);
		ros.writeLong(tokenId);
	}

	private void readObject(ObjectInputStream ris) throws IOException {

		targetInstanceName = ris.readUTF();
		String myName = ris.readUTF(); // Don't remove
		tokenId = ris.readLong();
	}

	@Override
	public void execute(String initiator) throws DataStoreException {

		int size = dsc.getReplicaStore().size();
		SizeResponseCommand<K, V> srCmd = new SizeResponseCommand<K, V>(targetInstanceName, tokenId, size);
		dsc.getCommandManager().execute(srCmd);
	}

	public String toString() {
		return getName() + "; tokenId=" + tokenId;
	}

	public int getResult() {
		int result = 0;
		try {
			result = (Integer) future.get(3, TimeUnit.SECONDS);
		} catch (Exception dse) {
			// TODO
		}

		return result;
	}

	@Override
	protected boolean isArtificialKey() {
		return true;
	}

}
