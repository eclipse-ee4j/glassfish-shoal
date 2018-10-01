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

package org.shoal.ha.cache.impl.interceptor;

import org.shoal.ha.cache.api.AbstractCommandInterceptor;
import org.shoal.ha.cache.api.DataStoreContext;
import org.shoal.ha.cache.api.DataStoreException;
import org.shoal.ha.cache.api.ShoalCacheLoggerConstants;
import org.shoal.ha.cache.impl.command.Command;
import org.shoal.ha.cache.impl.util.ReplicationOutputStream;
import org.shoal.ha.group.GroupService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Mahesh Kannan
 *
 */
public final class TransmitInterceptor<K, V> extends AbstractCommandInterceptor<K, V> {

	private static final Logger _logger = Logger.getLogger(ShoalCacheLoggerConstants.CACHE_TRANSMIT_INTERCEPTOR);

	@Override
	public void onTransmit(Command<K, V> cmd, String initiator) throws DataStoreException {
		DataStoreContext<K, V> ctx = getDataStoreContext();
		ByteArrayOutputStream bos = null;
		ObjectOutputStream oos = null;
		boolean transmitted = false;
		try {
			bos = new ByteArrayOutputStream();
			oos = new ObjectOutputStream(bos);
			oos.writeObject(cmd);
			oos.close();
			byte[] data = bos.toByteArray();

			GroupService gs = ctx.getGroupService();
			gs.sendMessage(cmd.getTargetName(), ctx.getServiceName(), data);
			dsc.getDataStoreMBean().incrementGmsSendCount();
			dsc.getDataStoreMBean().incrementGmsSendBytesCount(data.length);
			if (_logger.isLoggable(Level.FINE)) {
				_logger.log(Level.FINE, storeName + ": TransmitInterceptor." + ctx.getServiceName() + ":onTransmit() Sent " + cmd + " to "
				        + (cmd.getTargetName() == null ? " ALL MEMBERS " : cmd.getTargetName()) + "; size: " + data.length);
			}
			cmd.onSuccess();
			transmitted = true;
		} catch (IOException ioEx) {
			throw new DataStoreException("Error DURING transmit...", ioEx);
		} finally {
			if (!transmitted) {
				cmd.onFailure();
			}
			try {
				oos.close();
			} catch (Exception ex) {
				_logger.log(Level.FINEST, "Ignorable error while closing ObjectOutputStream");
			}
			try {
				bos.close();
			} catch (Exception ex) {
				_logger.log(Level.FINEST, "Ignorable error while closing ByteArrayOutputStream");
			}
		}
	}

}
