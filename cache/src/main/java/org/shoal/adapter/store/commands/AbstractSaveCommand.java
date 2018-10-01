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

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.shoal.ha.cache.api.DataStoreException;
import org.shoal.ha.cache.api.ShoalCacheLoggerConstants;

/**
 * @author Mahesh Kannan
 */
public abstract class AbstractSaveCommand<K, V> extends AcknowledgedCommand<K, V> {

	/**
	 *
	 */
	private static final long serialVersionUID = 241699054955846907L;

	protected transient static final Logger _logger = Logger.getLogger(ShoalCacheLoggerConstants.CACHE_SAVE_COMMAND);

	protected long version;

	protected long lastAccessedAt;

	protected long maxIdleTime;

	private transient String targetInstanceName;

	protected AbstractSaveCommand(byte opcode) {
		super(opcode);
	}

	public AbstractSaveCommand(byte opcode, K k, long version, long lastAccessedAt, long maxIdleTime) {
		this(opcode);
		super.setKey(k);
		this.version = version;
		this.lastAccessedAt = lastAccessedAt;
		this.maxIdleTime = maxIdleTime;
	}

	public boolean beforeTransmit() {
		targetInstanceName = dsc.getKeyMapper().getMappedInstance(dsc.getGroupName(), getKey());
		super.setTargetName(targetInstanceName);
		super.beforeTransmit();
		return getTargetName() != null;
	}

	public abstract void execute(String initiator) throws DataStoreException;

	public String toString() {
		return getName() + "(" + getKey() + ")";
	}

	@Override
	public String getKeyMappingInfo() {
		return targetInstanceName;
	}

	private void writeObject(java.io.ObjectOutputStream out) throws IOException {
		out.writeLong(version);
		out.writeLong(lastAccessedAt);
		out.writeLong(maxIdleTime);

		if (_logger.isLoggable(Level.FINE)) {
			_logger.log(Level.FINE, dsc.getServiceName() + " sending state for key = " + getKey() + "; version = " + version + "; lastAccessedAt = "
			        + lastAccessedAt + "; to " + getTargetName());
		}
	}

	public long getVersion() {
		return version;
	}

	public long getLastAccessedAt() {
		return lastAccessedAt;
	}

	public long getMaxIdleTime() {
		return maxIdleTime;
	}

	private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
		version = in.readLong();
		lastAccessedAt = in.readLong();
		maxIdleTime = in.readLong();
	}

	public boolean hasState() {
		return false;
	}

}
