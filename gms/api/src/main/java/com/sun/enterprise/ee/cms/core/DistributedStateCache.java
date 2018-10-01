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

package com.sun.enterprise.ee.cms.core;

import java.io.Serializable;
import java.util.Map;

/**
 * Represents a Caching facility that allows each member of the group to use key-value pairs representing the key to
 * their cachable state and the value being serializable objects representing a state to be cached. This allows a group
 * member to cache some state so that all members can access this cache through a distributed datastructure. When new
 * members arrive to the group, the underlying implementation could choose to ensure that they get the current cache of
 * states to sync with the group. Underlying implementations could also choose to share the cache with all members of
 * the group or pick specific replicas.
 * 
 * @author Shreedhar Ganapathy Date: Dec 7, 2004
 * @version $Revision$
 */
public interface DistributedStateCache {
	/**
	 * Caches a serializable object representing state to be cached. The key to this cache for the implementation is the
	 * composite key comprising the component name, the memberTokenId and the component-provided key itself.
	 * 
	 * @param componentName - name of the GMS client component that is storing this cache
	 * @param memberTokenId - member token Id of this member
	 * @param key - a serializable key that differentiates this cache from other caches of the same component and member.
	 * @param state - a serializable object representing the cachable state
	 * @throws GMSException
	 */
	void addToCache(String componentName, String memberTokenId, Serializable key, Serializable state) throws GMSException;

	/**
	 * <b>Locally</b> caches a serializable object representing state to be cached. The key to this cache for the
	 * implementation is the composite key comprising the component name, the memberTokenId and the component-provided key
	 * itself. <b>NOTE: </b>Calling this method does not result in information being shared with remote members. This is for
	 * local storage only.
	 *
	 * @param componentName Name of GMS Client component
	 * @param memberTokenId Member's identity
	 * @param key - an Object
	 * @param state - an Object
	 */
	void addToLocalCache(String componentName, String memberTokenId, Serializable key, Serializable state);

	/**
	 * retrieves the cache for the given composite key of component name, member token id and the key. Returns a cached
	 * Serializable object.
	 * 
	 * @param componentName Name of GMS Client component
	 * @param memberTokenId Member's identity
	 * @param key - a serializable key that differentiates this cache entry from other cache entries.
	 * @return Object
	 * @throws GMSException
	 */
	Object getFromCache(String componentName, String memberTokenId, Serializable key) throws GMSException;

	/**
	 * returns the current cache state to caller.
	 * 
	 * @return Map - containing the cache of this DSC instance
	 */
	Map getAllCache();

	/**
	 * removes an entry from the cache for the given composite key of the component name, member token id and the specific
	 * key.
	 * 
	 * @param componentName Name of GMS Client component
	 * @param memberTokenId Member's identity
	 * @param key The component provided key
	 * @throws GMSException
	 */
	void removeFromCache(String componentName, String memberTokenId, Serializable key) throws GMSException;

	/**
	 * returns a Map containing key-value pairs matching entries that have keys with the given componentName and memberToken
	 * 
	 * @param componentName Name of GMS Client component
	 * @param memberToken Member's identity
	 * @return Map - containing key value pairs for all entries that have the given component name and memberToken
	 */
	public Map<Serializable, Serializable> getFromCacheForPattern(final String componentName, final String memberToken);

	/**
	 * returns a Map containing entries that are in the DSC where either the componentName or the memberToken or the key is
	 * the same as the key specified in the argument.
	 * 
	 * @param key The key here is one of MemberToken, ComponentName, or the component-provided key itself
	 * @return Map
	 */
	public Map<GMSCacheable, Object> getFromCache(final Object key);

	/**
	 * returns true if the DSC contains an entry wherein the component key portion of the composite key in the DSC is the
	 * same as the key specified in the argument
	 * 
	 * @param key component-provided key
	 * @return boolean true if the component-provided key exists in cache
	 */
	public boolean contains(final Object key);

	/**
	 * returns true if the DSC contains an entry wherein the componentName and the componet key portion of the composite key
	 * in the DSC is the same as the parameters specified in the argument
	 *
	 * @param componentName Name of component
	 * @param key component-provided key
	 * @return boolean true if the key for the specified component exists
	 */
	public boolean contains(final String componentName, final Object key);

	/**
	 * returns true if this cache has been sync'd with any other member For implementations that do not intend to have a
	 * synchronized cache on all members, this method can be a no-op
	 * 
	 * @return boolean
	 */
	boolean isFirstSyncDone();

	/**
	 * Empties the DistributedStateCache. This is typically called in a group shutdown context so that the group's stale
	 * date is not retained for any later lives of the group.
	 */
	void removeAll();

	/**
	 * Empties the DistributedStateCache entries pertaining to a particular member. If this member's id appears in any part
	 * of the key or value of the cache, then that entry is removed. Typically, this is called when a particular member is
	 * being administratively shutdown and remaining members internally call this api when notified of this impending
	 * shutdown.
	 * 
	 * @param memberToken member's identity
	 */
	void removeAllForMember(String memberToken);
}
