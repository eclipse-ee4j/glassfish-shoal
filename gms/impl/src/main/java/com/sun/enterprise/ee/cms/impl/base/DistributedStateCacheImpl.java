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

package com.sun.enterprise.ee.cms.impl.base;

import static java.util.logging.Level.FINER;

import java.io.Serializable;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.enterprise.ee.cms.core.DistributedStateCache;
import com.sun.enterprise.ee.cms.core.GMSCacheable;
import com.sun.enterprise.ee.cms.core.GMSException;
import com.sun.enterprise.ee.cms.core.GMSMember;
import com.sun.enterprise.ee.cms.core.MemberNotInViewException;
import com.sun.enterprise.ee.cms.impl.common.DSCMessage;
import com.sun.enterprise.ee.cms.impl.common.GMSContext;
import com.sun.enterprise.ee.cms.impl.common.GMSContextFactory;
import com.sun.enterprise.ee.cms.logging.GMSLogDomain;

/**
 * Messaging based implementation of a shared distributed state cache(DSC). Every write entry made such as adding a new
 * entry or removing an existing entry is disseminated to all group members through a message. Read-only operation i.e
 * getFromCache() is a local call. During startup of a member, a singleton instance of this class is created. This
 * instance is available to GMS client components in this member through GroupHandle.getDistributedStateCache() so that
 * the client components can read or write to this cache.
 * <p>
 * When an entry is added or removed, this implementation uses underlying GroupCommunicationProvider(GCP) to sends a
 * message to the all members of the group. The recipients in turn call corresponding method for adding or removing the
 * entry to/from their copy of the this DistributedStateCache implementation.
 * <p>
 * When new member joins the group, the join notification is received on every member. When this happens, and if this
 * member is a leader of the group, then it uses the GCP's messaging api to sends a message to the new member to pass on
 * the current state of this DSC. The remote member updates its DSC with this current state while returning its own
 * state to this member through another message. This member updates its own DSC with this new entry resulting in all
 * members getting this updated state. This brings the group-wide DSC into a synchronized state.
 * <p>
 * This initial sync-up is a heavy weight operation particularly during startup of the whole group concurrently as new
 * members are joining the group rapidly. To prevent data loss during such times, each sync activity will require a
 * blocking call to ensure that rapid group view changes during group startup will not result in data loss.
 *
 * @author Shreedhar Ganapathy Date: June 20, 2006
 * @version $Revision$
 */
public class DistributedStateCacheImpl implements DistributedStateCache {
	private final static Logger logger = GMSLogDomain.getDSCLogger();
	private static final ConcurrentHashMap<String, DistributedStateCacheImpl> ctxCache = new ConcurrentHashMap<String, DistributedStateCacheImpl>();

	private final String groupName;
	private final ConcurrentHashMap<GMSCacheable, Object> cache;
	private final AtomicReference<GMSContext> ctxRef;
	private volatile boolean firstSyncDone;
	private final Object cacheUpdated = new Object();
	private AtomicBoolean waitingForUpdate = new AtomicBoolean(false);

	// private constructor for single instantiation
	private DistributedStateCacheImpl(final String groupName) {
		this.groupName = groupName;
		this.cache = new ConcurrentHashMap<GMSCacheable, Object>();
		this.ctxRef = new AtomicReference<GMSContext>(null);
		this.firstSyncDone = false;
	}

	private void waitForCacheUpdate(long waitTimeMs) {
		boolean setWaitingForUpdate = waitingForUpdate.compareAndSet(false, true);
		synchronized (cacheUpdated) {
			try {
				cacheUpdated.wait(waitTimeMs);
			} catch (InterruptedException ie) {
				// ignore
			}
			if (setWaitingForUpdate) {
				waitingForUpdate.compareAndSet(true, false);
			}
		}
	}

	private void notifyCacheUpdate() {
		if (waitingForUpdate.get()) {
			synchronized (cacheUpdated) {
				cacheUpdated.notifyAll();
			}
		}
	}

	// return the only instance we want to return
	static DistributedStateCache getInstance(final String groupName) {
		// NOTE: assumes, that constructing an DistributedStateCacheImpl instance is light weight
		// and does not introduce any other dependencies (like registering itself somewhere, etc)

		// is there another instance already with that name?
		DistributedStateCacheImpl instance = ctxCache.get(groupName);
		if (instance == null) {
			// no, there is no one, create a new one then
			instance = new DistributedStateCacheImpl(groupName);

			if (logger.isLoggable(Level.FINE)) {
				logger.log(Level.FINE, "created a DistributedStateCache for group:" + groupName);
			}

			// put the new instance to the map, unless another thread won the race and has put its own instance already.
			// if there is another instance already, use that one instead and discard ours
			DistributedStateCacheImpl otherInstance = ctxCache.putIfAbsent(groupName, instance);
			if (otherInstance != null) {
				instance = otherInstance;
			}
		}
		return instance;
	}

	/*
	 * adds entry to local cache and calls remote members to add the entry to their cache
	 */
	public void addToCache(final String componentName, final String memberTokenId, final Serializable key, final Serializable state) throws GMSException {
		if (logger.isLoggable(Level.FINER)) {
			logger.log(Level.FINER, "Adding to DSC by local Member:" + memberTokenId + ",Component:" + componentName + ",key:" + key + ",State:" + state);
		}
		final GMSCacheable cKey = createCompositeKey(componentName, memberTokenId, key);
		addToLocalCache(cKey, state);
		addToRemoteCache(cKey, state);
	}

	public void addToCache(final String componentName, final String memberTokenId, final Serializable key, final byte[] state) throws GMSException {
		if (logger.isLoggable(FINER)) {
			logger.log(FINER, new StringBuilder().append("Adding to DSC by local Member:").append(memberTokenId).append(",Component:").append(componentName)
			        .append(",key:").append(key).append(",State:").append(state).toString());
		}
		final GMSCacheable cKey = createCompositeKey(componentName, memberTokenId, key);
		addToLocalCache(cKey, state);
		addToRemoteCache(cKey, state);
	}

	public void addToLocalCache(final String componentName, final String memberTokenId, final Serializable key, final Serializable state) {
		final GMSCacheable cKey = createCompositeKey(componentName, memberTokenId, key);
		addToLocalCache(cKey, state);
	}

	public void addToLocalCache(final String componentName, final String memberTokenId, final Serializable key, final byte[] state) {
		final GMSCacheable cKey = createCompositeKey(componentName, memberTokenId, key);
		addToLocalCache(cKey, state);
	}

	/*
	 * This is called by both addToCache() method and by the RPCInvocationHandler to handle remote sync operations.
	 */
	public void addToLocalCache(GMSCacheable cKey, final Object state) {
		cKey = getTrueKey(cKey);
		if (logger.isLoggable(Level.FINEST)) {
			logger.log(Level.FINEST, "Adding cKey=" + cKey.toString() + " state=" + state.toString());
		}
		cache.put(cKey, state);
		notifyCacheUpdate();
		printDSCContents();
	}

	private void printDSCContents() {
		if (logger.isLoggable(FINER)) {
			logger.log(FINER, getGMSContext().getServerIdentityToken() + ":DSC now contains ---------\n" + getDSCContents());
		}
	}

	private GMSContext getGMSContext() {
		// get the current set ctx
		GMSContext ctx = ctxRef.get();
		if (ctx == null) {
			// ctxRef had null as value
			// -> get a ctx from factory (assuming, we never get "null" from there)
			ctx = GMSContextFactory.getGMSContext(groupName);

			if (ctx == null) {
				// TODO: ouch, we have received null from the factory. what to do? throwing an exception? log at least
				logger.log(Level.WARNING, "dsc.no.ctx", new Object[] { groupName });
			} else {
				// set our not null ctx as the new value on ctxRef, expecting its value is still null
				boolean oldValueWasNull = ctxRef.compareAndSet(null, ctx);

				// ctxRef's value was not null anymore. another thread won the race and has set a ctx as value already.
				// -> we use that one instead (assuming, that ctxRef is not set back to null by any other thread)
				if (!oldValueWasNull) {
					ctx = ctxRef.get();
				}
			}
		}
		return ctx;
	}

	@Override
	public String toString() {
		return getDSCContents();
	}

	private String getDSCContents() {
		final StringBuffer buf = new StringBuffer();
		final ConcurrentHashMap<GMSCacheable, Object> copy;
		copy = new ConcurrentHashMap<GMSCacheable, Object>(cache);
		for (Map.Entry<GMSCacheable, Object> entry : copy.entrySet()) {
			buf.append(entry.getKey().hashCode()).append(" key=").append(entry.getKey().toString()).append(" : value=").append(entry.getValue()).append("\n");
		}
		return buf.toString();
	}

	private void addToRemoteCache(final GMSCacheable cKey, final Object state) throws GMSException {
		final DSCMessage msg = new DSCMessage(cKey, state, DSCMessage.OPERATION.ADD.toString());

		sendMessage(null, msg);
	}

	/*
	 * removes an entry from local cache and calls remote members to remove the entry from their cache
	 */
	public void removeFromCache(final String componentName, final String memberTokenId, final Serializable key) throws GMSException {
		final GMSCacheable cKey = createCompositeKey(componentName, memberTokenId, key);

		removeFromLocalCache(cKey);
		removeFromRemoteCache(cKey);
	}

	void removeFromLocalCache(GMSCacheable cKey) {
		cKey = getTrueKey(cKey);
		cache.remove(cKey);
	}

	private void removeFromRemoteCache(GMSCacheable cKey) throws GMSException {
		cKey = getTrueKey(cKey);
		final DSCMessage msg = new DSCMessage(cKey, null, DSCMessage.OPERATION.REMOVE.toString());
		sendMessage(null, msg);
	}

	/*
	 * retrieves an entry from the local cache for the given parameters
	 */
	public Object getFromCache(final String componentName, final String memberTokenId, final Serializable key) throws GMSException {
		if (key != null && componentName != null && memberTokenId != null) {
			GMSCacheable cKey = createCompositeKey(componentName, memberTokenId, key);
			cKey = getTrueKey(cKey);
			return cache.get(cKey);
		} else { // TODO: Localize
			throw new GMSException(new StringBuffer().append("DistributedStateCache: ").append("componentName, memberTokenId and key ")
			        .append("are required parameters and cannot be null").toString());
		}
	}

	public Map<GMSCacheable, Object> getAllCache() {
		return new ConcurrentHashMap<GMSCacheable, Object>(cache);
	}

	private Map<Serializable, Serializable> getEntryFromCacheForPattern(final String componentName, final String memberToken) {
		Map<Serializable, Serializable> retval = new Hashtable<Serializable, Serializable>();
		for (GMSCacheable c : cache.keySet()) {
			if (componentName.equals(c.getComponentName())) {
				if (memberToken.equals(c.getMemberTokenId())) {
					retval.put((Serializable) c.getKey(), (Serializable) cache.get(c));
				}
			}
		}
		return retval;
	}

	public Map<Serializable, Serializable> getFromCacheForPattern(final String componentName, final String memberToken) {
		if (logger.isLoggable(Level.FINE)) {
			logger.fine("DSCImpl.getCacheFromPattern() for " + memberToken);
		}
		final Map<Serializable, Serializable> retval = new Hashtable<Serializable, Serializable>();
		if (componentName == null || memberToken == null) {
			if (logger.isLoggable(Level.FINE)) {
				logger.log(Level.FINE,
				        "DistributedStateCacheImpl.getFromCachePattern() parameter is null.  component=" + componentName + " member=" + memberToken);
			}
			return retval;
		}
		retval.putAll(getEntryFromCacheForPattern(componentName, memberToken));

		if (!retval.isEmpty()) {
			return retval;
		} else {
			final String thisMember = getGMSContext().getServerIdentityToken();
			String remoteDSCMemberToken = memberToken;
			List<String> currentMembers = getGMSContext().getGroupHandle().getAllCurrentMembers();

			// first check if the instance that set the key value pair originally is a current group member.
			if (currentMembers != null && !currentMembers.contains(remoteDSCMemberToken)) {

				// memberToken that originally set the key value pair has left group,
				// so lets try to get memberTokens distributed state cache values from oldest current member.
				// NOTE: that jts TX_LOG_DIR is the location of log directory for a failed server
				// and its value is accessed from its FailureRecoveryAgent, thus this is cover a real life
				// scenario for getting an instances' key value pair when the instance is no longer around.
				remoteDSCMemberToken = getOldestCurrentMember(thisMember);
				if (logger.isLoggable(Level.FINE)) {
					logger.log(Level.FINE, "getFromCacheForPattern componentName:" + componentName + " memberToken:" + memberToken
					        + " missing data in local cache. look up data from oldest group member:" + remoteDSCMemberToken);
				}
			}
			if (!getGMSContext().isShuttingDown()) {
				ConcurrentHashMap<GMSCacheable, Object> temp = new ConcurrentHashMap<GMSCacheable, Object>(cache);
				DSCMessage msg = new DSCMessage(temp, DSCMessage.OPERATION.ADDALLLOCAL.toString(), true);
				try {
					boolean sent = sendMessage(remoteDSCMemberToken, msg);
					if (sent) {
						final long MAX_WAIT = 6000;
						final long startTime = System.currentTimeMillis();
						final long stopTime = startTime + MAX_WAIT;
						final long WAIT_INTERVAL = 1500;
						while (retval.isEmpty() && System.currentTimeMillis() < stopTime) {
							waitForCacheUpdate(WAIT_INTERVAL);
							retval.putAll(getEntryFromCacheForPattern(componentName, memberToken));
						}
						if (logger.isLoggable(Level.FINE)) {
							final long DURATION = System.currentTimeMillis() - startTime;
							logger.fine("getFromCacheForPattern waited " + DURATION + " ms for result " + retval);
						}
					} else {
						if (logger.isLoggable(Level.FINE)) {
							logger.log(Level.FINE,
							        "DistributedStateCacheImpl.getFromCachePattern() send failed for component=" + componentName + " member=" + memberToken);
						}
					}
				} catch (GMSException e) {
					logger.log(Level.WARNING, "dsc.sync.exception", e);
				}
			} else {
				logger.log(Level.FINE,
				        "getFromCacheForPattern(" + componentName + "," + memberToken + ")" + ": returning empty map. " + "reason: isShuttingDown()="
				                + getGMSContext().isShuttingDown() + " isCurrentMember="
				                + getGMSContext().getGroupHandle().getAllCurrentMembers().contains(memberToken));
			}
		}
		if (retval.isEmpty()) {
			logger.fine("retVal is empty");
		}
		return retval;
	}

	private String getOldestCurrentMember(String exclude) {
		List<GMSMember> currentMembers = getGMSContext().getGroupHandle().getCurrentView();
		GMSMember oldestMember = null;
		if (currentMembers != null) {
			for (GMSMember member : currentMembers) {
				if (member.getMemberToken().equals(exclude)) {
					continue;
				}
				if (oldestMember == null) {
					oldestMember = member;
				}
				if (member.getStartTime() < oldestMember.getStartTime()) {
					oldestMember = member;
				}
			}
		}
		return oldestMember == null ? null : oldestMember.getMemberToken();
	}

	public Map<GMSCacheable, Object> getFromCache(final Object key) {
		final Map<GMSCacheable, Object> retval = new Hashtable<GMSCacheable, Object>();
		for (GMSCacheable c : cache.keySet()) {
			if (key.equals(c.getComponentName()) || key.equals(c.getMemberTokenId()) || key.equals(c.getKey())) {
				retval.put(c, cache.get(c));
			}
		}
		return retval;
	}

	public boolean contains(final Object key) {
		boolean retval = false;
		for (GMSCacheable c : cache.keySet()) {
			if (logger.isLoggable(FINER)) {
				logger.log(FINER, new StringBuffer().append("key=").append(key).append(" underlying key=").append(c.getKey()).toString());
			}
			if (key.equals(c.getKey())) {
				retval = true;
			}
		}
		return retval;
	}

	public boolean contains(final String componentName, final Object key) {
		boolean retval = false;
		for (GMSCacheable c : cache.keySet()) {
			if (logger.isLoggable(FINER)) {
				logger.log(FINER, new StringBuffer().append("comp=").append(componentName).append(" underlying comp=").append(c.getComponentName()).toString());
				logger.log(FINER, new StringBuffer().append("key=").append(key).append(" underlying key=").append(c.getKey()).toString());
			}
			if (key.equals(c.getKey()) && componentName.equals(c.getComponentName())) {
				retval = true;
			}
		}
		return retval;
	}

	/*
	 * adds all entries from a collection to the cache. This is used to sync states with the group when this instance is
	 * joining an existing group.
	 *
	 * @param map - containing a GMSCacheable as key and an Object as value.
	 */
	void addAllToLocalCache(final Map<GMSCacheable, Object> map) {
		if (map != null && map.size() > 0) {
			cache.putAll(map);
			notifyCacheUpdate();
		}
		indicateFirstSyncDone();

		logger.log(FINER, "done adding all to Distributed State Cache");
	}

	void addAllToRemoteCache() throws GMSException {
		ConcurrentHashMap<GMSCacheable, Object> temp;
		temp = new ConcurrentHashMap<GMSCacheable, Object>(cache);
		final DSCMessage msg = new DSCMessage(temp, DSCMessage.OPERATION.ADDALLREMOTE.toString(), false);
		sendMessage(null, msg);

	}

	/**
	 * removes all entries pertaining to a particular component and member token id, except those marked by special keys.
	 * <p>
	 * A null component name would indicate that all entries pertaining to a member be removed except for the ones specified
	 * by the special keys.
	 * <p>
	 * Null special keys would indicate that all entries pertaining to a member token id be removed.
	 * <p>
	 * This operation could also be used to sync states of the group when an instance has failed/left the group.
	 *
	 * @param componentName component name
	 * @param memberTokenId member token id
	 * @param exempted - an List of Serializable keys that are exempted from being removed from the cache as part of this
	 * operation.
	 */
	void removeAllFromCache(final String componentName, final String memberTokenId, final List<Serializable> exempted) {
		// TODO: Implement this for clean bookkeeping
	}

	Hashtable<GMSCacheable, Object> getAllEntries() {
		final Hashtable<GMSCacheable, Object> temp = new Hashtable<GMSCacheable, Object>();
		for (GMSCacheable key : cache.keySet()) {
			temp.put(key, cache.get(key));
		}
		return temp;
	}

	void syncCache(final String memberToken, final boolean isCoordinator) throws GMSException {
		final ConcurrentHashMap<GMSCacheable, Object> temp;
		temp = new ConcurrentHashMap<GMSCacheable, Object>(cache);

		final DSCMessage msg = new DSCMessage(temp, DSCMessage.OPERATION.ADDALLLOCAL.toString(), isCoordinator);
		if (!memberToken.equals(getGMSContext().getServerIdentityToken())) {
			if (logger.isLoggable(Level.FINER)) {
				logger.log(Level.FINER, "Sending sync message from DistributedStateCache to member " + memberToken);
			}
			sendMessage(memberToken, msg);
		}
		if (isCoordinator) {
			indicateFirstSyncDone();
		}
	}

	void syncCache(final PeerID peerID, final boolean isCoordinator) throws GMSException {
		final ConcurrentHashMap<GMSCacheable, Object> temp;
		temp = new ConcurrentHashMap<GMSCacheable, Object>(cache);

		final DSCMessage msg = new DSCMessage(temp, DSCMessage.OPERATION.ADDALLLOCAL.toString(), isCoordinator);
		if (!peerID.getInstanceName().equals(getGMSContext().getServerIdentityToken())) {
			if (logger.isLoggable(Level.FINER)) {
				logger.log(Level.FINER, "Sending sync message from DistributedStateCache to member " + peerID);
			}
			boolean result = ((GroupCommunicationProviderImpl) this.ctxRef.get().getGroupCommunicationProvider()).sendMessage(peerID, msg);
			if (logger.isLoggable(Level.FINE)) {
				logger.log(Level.FINE, "synch cache sent result:" + result + " with member:" + peerID.getInstanceName() + " id:" + peerID);
			}
		}
		if (isCoordinator) {
			indicateFirstSyncDone();
		}
	}

	private GMSCacheable getTrueKey(GMSCacheable cKey) {
		final Set<GMSCacheable> keys;
		keys = cache.keySet();

		for (GMSCacheable comp : keys) {
			if (comp.equals(cKey)) {
				cKey = comp;
				break;
			}
		}
		return cKey;
	}

	private static GMSCacheable createCompositeKey(final String componentName, final String memberTokenId, final Object key) {
		return new GMSCacheable(componentName, memberTokenId, key);
	}

	/**
	 * Send <code>msg</code> to <code>member</code>.
	 *
	 * @param member if null, send <code>msg</code> to all members of the group
	 * @param msg
	 * @return boolean <code>true</code> if the message has been sent otherwise <code>false</code>. <code>false</code>. is
	 * commonly returned for non-error related congestion, meaning that you should be able to send the message after waiting
	 * some amount of time.
	 * @throws com.sun.enterprise.ee.cms.core.GMSException
	 *
	 */
	private boolean sendMessage(final String member, final DSCMessage msg) throws GMSException {
		boolean sent = false;
		try {
			getGMSContext().getGroupCommunicationProvider().sendMessage(member, msg, true);
			sent = true;
		} catch (MemberNotInViewException e) {
			if (logger.isLoggable(Level.FINE)) {
				logger.log(Level.FINE, "Member " + member + " is not in the view anymore. Hence not performing sendMessage operation", e);
			}
		} catch (GMSException ge) {
			logger.log(Level.WARNING, "dsc.send.failed", new Object[] { member, msg });
		}
		return sent;
	}

	private void indicateFirstSyncDone() {
		// - we can use volatile variable instead of AtomicBoolean.
		// - we change only from "false" to "true" and never back.
		// => it is ok, in a race condition, that many threads write "true", if they have read "false" before
		if (!firstSyncDone) {
			firstSyncDone = true;
		}
	}

	public boolean isFirstSyncDone() {
		return firstSyncDone;
	}

	/**
	 * Empties the DistributedStateCache. This is typically called in a group shutdown context so that the group's stale
	 * data is not retained for any later lives of the group.
	 */
	public void removeAll() {
		cache.clear();
		ctxCache.remove(groupName);
		if (logger.isLoggable(Level.FINE)) {
			logger.fine("removed distributed state cache for group: " + groupName);
		}
	}

	public void removeAllForMember(final String memberToken) {
		final Set<GMSCacheable> keys = getFromCache(memberToken).keySet();
		for (final GMSCacheable key : keys) {
			removeFromLocalCache(key);
		}
	}
}
