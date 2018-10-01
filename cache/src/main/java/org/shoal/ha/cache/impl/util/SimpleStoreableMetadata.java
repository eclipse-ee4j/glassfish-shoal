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

package org.shoal.ha.cache.impl.util;

import org.glassfish.ha.store.api.Storeable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * @author Mahesh Kannan
 * 
 */
public class SimpleStoreableMetadata implements Storeable {

	private long version;

	private long lastAccessTime;

	private long maxIdleTime;

	private boolean isNew;

	private byte[] state;

	private static final String[] _attrNames = new String[] { "state" };

	private static final boolean[] _dirtyStates = new boolean[] { true };

	public SimpleStoreableMetadata() {

	}

	public SimpleStoreableMetadata(long version, long lastAccessTime, long maxIdleTime, boolean aNew, byte[] state) {
		this.version = version;
		this.lastAccessTime = lastAccessTime;
		this.maxIdleTime = maxIdleTime;
		isNew = aNew;
		this.state = state;
	}

	public byte[] getState() {
		return state;
	}

	@Override
	public String toString() {
		return "SimpleStoreableMetadata{" + "version=" + version + ", lastAccessTime=" + lastAccessTime + ", maxIdleTime=" + maxIdleTime + ", isNew=" + isNew
		        + ", state=" + Arrays.toString(state) + '}';
	}

	@Override
	public long _storeable_getVersion() {
		return version;
	}

	@Override
	public void _storeable_setVersion(long l) {
		version = l;
	}

	@Override
	public long _storeable_getLastAccessTime() {
		return lastAccessTime;
	}

	@Override
	public void _storeable_setLastAccessTime(long l) {
		lastAccessTime = l;
	}

	@Override
	public long _storeable_getMaxIdleTime() {
		return maxIdleTime;
	}

	@Override
	public void _storeable_setMaxIdleTime(long l) {
		maxIdleTime = l;
	}

	@Override
	public String[] _storeable_getAttributeNames() {
		return _attrNames;
	}

	@Override
	public boolean[] _storeable_getDirtyStatus() {
		return _dirtyStates;
	}

	@Override
	public void _storeable_writeState(OutputStream outputStream) throws IOException {
		// To change body of implemented methods use File | Settings | File Templates.
	}

	@Override
	public void _storeable_readState(InputStream inputStream) throws IOException {
		// To change body of implemented methods use File | Settings | File Templates.
	}
}
