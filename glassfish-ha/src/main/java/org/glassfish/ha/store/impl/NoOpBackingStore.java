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

package org.glassfish.ha.store.impl;

import java.io.Serializable;

import org.glassfish.ha.store.api.BackingStore;
import org.glassfish.ha.store.api.BackingStoreConfiguration;
import org.glassfish.ha.store.api.BackingStoreException;

/**
 * @author Mahesh Kannan
 */
public class NoOpBackingStore<K extends Serializable, V extends Serializable> extends BackingStore<K, V> {

	private String myName;

	NoOpBackingStore() {

	}

	@Override
	protected void initialize(BackingStoreConfiguration<K, V> conf) throws BackingStoreException {
		super.initialize(conf);

		myName = conf == null ? null : conf.getInstanceName();
	}

	@Override
	public V load(K key, String version) throws BackingStoreException {
		return null;
	}

	public V load(K key, Long version) throws BackingStoreException {
		return null;
	}

	@Override
	public String save(K key, V value, boolean isNew) throws BackingStoreException {
		return null;
	}

	@Override
	public void remove(K key) throws BackingStoreException {

	}

	@Override
	public void updateTimestamp(K key, long time) throws BackingStoreException {

	}

	@Override
	public String updateTimestamp(K key, Long version, Long accessTime, Long maxIdleTime) throws BackingStoreException {
		return myName;
	}

	@Override
	public int removeExpired(long idleForMillis) throws BackingStoreException {
		return 0;
	}

	@Override
	public int size() throws BackingStoreException {
		return 0;
	}

	@Override
	public void destroy() throws BackingStoreException {

	}
}
