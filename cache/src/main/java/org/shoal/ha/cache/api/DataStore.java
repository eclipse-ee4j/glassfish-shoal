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

package org.shoal.ha.cache.api;

/**
 * A DataStore allows (#{Serializable} / #{Storable}) objects to be placed in the cache. The cache itself is created and
 * configured using a #{DataStoreFactory}.
 * 
 * @author Mahesh Kannan
 */
public interface DataStore<K, V> {

	/**
	 * Creates or Replaces the object associated with key k.
	 *
	 * @param k The Key
	 * @param v The value. The value must be either serializable of the DataStoreEntryHelper that is associated with this
	 * cache must be able to transform this into a serializable.
	 */
	public String put(K k, V v) throws DataStoreException;

	/**
	 * Returns the value to which the specified key is mapped in this cache.
	 *
	 * @param k The key
	 * @return The value if the association exists or null.
	 */
	public V get(K k) throws DataStoreException;

	/**
	 * Removes the mapping between the key and the object.
	 *
	 * @param k The key
	 */
	public void remove(K k) throws DataStoreException;

	/**
	 * Updates the timestamp associated with this entry. see #{removeIdleEntries}
	 *
	 * @param k The key
	 */
	public String touch(K k, long version, long timeStamp, long ttl) throws DataStoreException;

	/**
	 * Removes all entries that were not accessed for more than 'idlefor' millis
	 *
	 * @param idleFor Time in milli seconds
	 */
	public int removeIdleEntries(long idleFor);

	/**
	 * Close this datastore. This causes all data to be removed(?)
	 */
	public void close();

	public void destroy();

	public int size();

}
