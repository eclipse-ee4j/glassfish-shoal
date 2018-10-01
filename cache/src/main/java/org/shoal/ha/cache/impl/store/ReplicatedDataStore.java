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

package org.shoal.ha.cache.impl.store;

import org.glassfish.ha.store.api.Storeable;
import org.glassfish.ha.store.util.KeyTransformer;
import org.glassfish.ha.store.util.SimpleMetadata;
import org.shoal.adapter.store.commands.*;
import org.shoal.ha.cache.impl.interceptor.ReplicationCommandTransmitterManager;
import org.shoal.ha.cache.impl.interceptor.ReplicationFramePayloadCommand;
import org.shoal.ha.cache.impl.util.CommandResponse;
import org.shoal.ha.cache.impl.util.ResponseMediator;
import org.shoal.ha.cache.impl.util.StringKeyTransformer;
import org.shoal.ha.group.GroupMemberEventListener;
import org.shoal.ha.mapper.DefaultKeyMapper;
import org.shoal.ha.group.GroupService;
import org.shoal.ha.mapper.KeyMapper;
import org.shoal.ha.cache.api.*;
import org.shoal.ha.cache.impl.command.Command;
import org.shoal.ha.cache.impl.command.CommandManager;

import javax.management.*;
import java.io.Serializable;
import java.lang.management.ManagementFactory;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Mahesh Kannan
 */
public class ReplicatedDataStore<K, V extends Serializable> implements DataStore<K, V> {

	private static final int MAX_REPLICA_TRIES = 1;

	private static final Logger _logger = Logger.getLogger(ShoalCacheLoggerConstants.CACHE_DATA_STORE);

	private static final Logger _loadLogger = Logger.getLogger(ShoalCacheLoggerConstants.CACHE_LOAD_REQUEST_COMMAND);

	private static final Logger _saveLogger = Logger.getLogger(ShoalCacheLoggerConstants.CACHE_SAVE_COMMAND);

	private String storeName;

	private String instanceName;

	private String groupName;

	private GroupService gs;

	private CommandManager<K, V> cm;

	private DataStoreContext<K, V> dsc;

	private ReplicaStore<K, V> replicaStore;

	private long defaultIdleTimeoutInMillis;

	private ReplicatedDataStoreStatsHolder<K, V> dscMBean;

	private String debugName = "ReplicatedDataStore";

	private MBeanServer mbs;
	private ObjectName mbeanObjectName;

	private AtomicBoolean closed = new AtomicBoolean(false);

	public ReplicatedDataStore(DataStoreContext<K, V> conf, GroupService gs) {
		this.dsc = conf;
		this.storeName = conf.getStoreName();
		this.gs = gs;
		this.instanceName = gs.getMemberName();
		this.groupName = gs.getGroupName();

		initialize();
		postInitialization();

		debugName = conf.getStoreName() + ": ";
	}

	public DataStoreContext<K, V> getDataStoreContext() {
		return dsc;
	}

	private void initialize() {

		dsc.setGroupService(gs);

		if (dsc.getClassLoader() == null) {
			ClassLoader loader = dsc.getValueClazz().getClassLoader();
			if (loader == null) {
				loader = ClassLoader.getSystemClassLoader();
			}
			dsc.setClassLoader(loader);
		}

		if (dsc.getKeyMapper() == null) {
			dsc.setKeyMapper(new DefaultKeyMapper(dsc.getInstanceName(), dsc.getGroupName()));
		}

		if (dsc.getKeyMapper() instanceof GroupMemberEventListener) {
			GroupMemberEventListener groupListener = (GroupMemberEventListener) dsc.getKeyMapper();
			gs.registerGroupMemberEventListener(groupListener);
		}

		if (dsc.getKeyTransformer() == null) {
			KeyTransformer kt = null;
			if (dsc.getKeyClazz() == String.class) {
				kt = new StringKeyTransformer();
			} else {
				// kt = new DefaultKeyTransformer(dsc.getClassLoader());
			}
			dsc.setKeyTransformer(kt);
		}
	}

	private void postInitialization() {

		Class<V> vClazz = dsc.getValueClazz();
		DataStoreEntryUpdater<K, V> dseUpdater = null;

		if (SimpleMetadata.class.isAssignableFrom(vClazz)) {
			dseUpdater = new SimpleStoreableDataStoreEntryUpdater();
		} else if (Storeable.class.isAssignableFrom(vClazz)) {
			dseUpdater = new StoreableDataStoreEntryUpdater();
			dsc.setUseMapToCacheCommands(false);
		} else {
			dseUpdater = new SimpleDataStoreEntryUpdater();
		}
		dseUpdater.initialize(dsc);
		dsc.setDataStoreEntryUpdater(dseUpdater);

		if (_logger.isLoggable(Level.FINE)) {
			_logger.log(Level.FINE, "ReplicatedDataStore For {" + dsc.getStoreName() + "} using DataStoreEntryUpdater = "
			        + dsc.getDataStoreEntryUpdater().getClass().getName());
		}

		this.cm = new CommandManager<K, V>();
		dsc.setCommandManager(cm);
		cm.initialize(dsc);

		if (dsc.getCommands() != null) {
			for (Command<K, ? super V> cmd : dsc.getCommands()) {
				cm.registerCommand(cmd);
			}
		}

		cm.registerExecutionInterceptor(new ReplicationCommandTransmitterManager<K, V>());
		cm.registerCommand(new ReplicationFramePayloadCommand<K, V>());

		KeyMapper keyMapper = dsc.getKeyMapper();
		if ((keyMapper != null) && (keyMapper instanceof DefaultKeyMapper)) {
			gs.registerGroupMemberEventListener((DefaultKeyMapper) keyMapper);
		}

		dsc.setResponseMediator(new ResponseMediator());
		replicaStore = new ReplicaStore<K, V>(dsc);
		dsc.setReplicaStore(replicaStore);

		gs.registerGroupMessageReceiver(storeName, cm);

		initIdleEntryProcessor();
		replicaStore = dsc.getReplicaStore();
		replicaStore.setIdleEntryDetector(dsc.getIdleEntryDetector());

		if (_logger.isLoggable(Level.FINE)) {
			_logger.log(Level.FINE, "Created ReplicatedDataStore with configuration = " + dsc);
		}

		dscMBean = new ReplicatedDataStoreStatsHolder<K, V>(dsc);
		dsc.setDataStoreMBean(dscMBean);

		boolean registerInMBeanServer = Boolean.getBoolean("org.shoal.ha.cache.mbean.register");
		if (registerInMBeanServer) {
			try {
				mbeanObjectName = new ObjectName("org.shoal.ha.cache.jmx.ReplicatedDataStore" + ":name=" + dsc.getStoreName() + "_" + dsc.getInstanceName());

				mbs = ManagementFactory.getPlatformMBeanServer();
				mbs.registerMBean(new StandardMBean(dscMBean, DataStoreMBean.class), mbeanObjectName);
			} catch (MalformedObjectNameException malEx) {
				_logger.log(Level.INFO, "Couldn't register MBean for " + dscMBean.getStoreName() + " : " + malEx);
			} catch (InstanceAlreadyExistsException malEx) {
				_logger.log(Level.INFO, "Couldn't register MBean for " + dscMBean.getStoreName() + " : " + malEx);
			} catch (MBeanRegistrationException malEx) {
				_logger.log(Level.INFO, "Couldn't register MBean for " + dscMBean.getStoreName() + " : " + malEx);
			} catch (NotCompliantMBeanException malEx) {
				_logger.log(Level.INFO, "Couldn't register MBean for " + dscMBean.getStoreName() + " : " + malEx);
			}
		}
	}

	private void initIdleEntryProcessor() {
		try {
			if (Storeable.class.isAssignableFrom(dsc.getValueClazz())) {
				dsc.setIdleEntryDetector(new IdleEntryDetector<K, V>() {
					@Override
					public boolean isIdle(DataStoreEntry<K, V> entry, long nowInMillis) {
						_logger.log(Level.FINE, "AccessTimeInfo: getLastAccessedAt=" + entry.getLastAccessedAt() + "; maxIdleTimeInMillis="
						        + entry.getMaxIdleTime() + " < now=" + nowInMillis);
						return (entry.getMaxIdleTime() > 0) && entry.getLastAccessedAt() + entry.getMaxIdleTime() < nowInMillis;
					}
				});
			} else {
				if (dsc.getDefaultMaxIdleTimeInMillis() > 0) {
					final long defaultMaxIdleTimeInMillis = dsc.getDefaultMaxIdleTimeInMillis();
					dsc.setIdleEntryDetector(new IdleEntryDetector<K, V>() {
						@Override
						public boolean isIdle(DataStoreEntry<K, V> entry, long nowInMillis) {
							_logger.log(Level.FINE, "AccessTimeInfo: getLastAccessedAt=" + entry.getLastAccessedAt() + "; defaultMaxIdleTimeInMillis="
							        + defaultMaxIdleTimeInMillis + " < now=" + nowInMillis);
							return (defaultMaxIdleTimeInMillis > 0) && entry.getLastAccessedAt() + defaultMaxIdleTimeInMillis < nowInMillis;
						}
					});
				}
			}
		} catch (Exception ex) {
			// TODO
		}
	}

	@Override
	public String put(K k, V v) throws DataStoreException {
		String result = "";

		try {
			dsc.acquireReadLock();
			if (closed.get()) {
				throw new DataStoreAlreadyClosedException("put() failed. Store " + dsc.getStoreName() + " already closed");
			}

			DataStoreEntry<K, V> entry = replicaStore.getOrCreateEntry(k);
			synchronized (entry) {
				if (!entry.isRemoved()) {
					if (dsc.isCacheLocally()) {
						entry.setV(v);
					}
					KeyMapper keyMapper = dsc.getKeyMapper();

					// fix for GLASSFISH-18085
					String[] members = keyMapper.getCurrentMembers();
					if (members.length == 0) {
						_saveLogger.log(Level.FINE, "Skipped replication of " + k + " since there is only one instance running in the cluster.");
						return result;
					}

					SaveCommand<K, V> cmd = dsc.getDataStoreEntryUpdater().createSaveCommand(entry, k, v);
					cm.execute(cmd);
					dscMBean.incrementSaveCount();

					String staleLocation = entry.setReplicaInstanceName(cmd.getTargetName());

					result = cmd.getKeyMappingInfo();

					if ((staleLocation != null) && (!staleLocation.equals(cmd.getTargetName()))) {
						StaleCopyRemoveCommand<K, V> staleCmd = new StaleCopyRemoveCommand<K, V>(k);
						staleCmd.setStaleTargetName(staleLocation);
						cm.execute(staleCmd);
					}
				} else {
					_logger.log(Level.WARNING, "ReplicatedDataStore.put(" + k + ") AFTER remove?");
					return result;
				}
			}

			if (_saveLogger.isLoggable(Level.FINE)) {
				_saveLogger.log(Level.FINE, debugName + " done save(" + k + ") to " + result);
			}
		} finally {
			dsc.releaseReadLock();
		}
		return result;
	}

	@Override
	public V get(K key) throws DataStoreException {
		dscMBean.incrementLoadCount();
		V v = null;

		try {
			dsc.acquireReadLock();
			if (closed.get()) {
				throw new DataStoreAlreadyClosedException("get() failed. Store " + dsc.getStoreName() + " already closed");
			}

			dscMBean.incrementLoadCount();
			boolean foundLocally = false;
			DataStoreEntry<K, V> entry = replicaStore.getEntry(key);
			if (entry != null) {
				if (!entry.isRemoved()) {
					v = dsc.getDataStoreEntryUpdater().getV(entry);
					if (v != null) {
						foundLocally = true;
						dscMBean.incrementLocalLoadSuccessCount();
						if (_loadLogger.isLoggable(Level.FINE)) {
							_loadLogger.log(Level.FINE, debugName + "load(" + key + "); FOUND IN LOCAL CACHE!!");
						}
					}
				} else {
					return null; // Because it is already removed
				}
			}

			if (v == null) {
				KeyMapper keyMapper = dsc.getKeyMapper();
				String replicachoices = keyMapper.getReplicaChoices(dsc.getGroupName(), key);
				String[] replicaHint = replicachoices.split(":");
				if (_loadLogger.isLoggable(Level.FINE)) {
					_loadLogger.log(Level.FINE, debugName + "load(" + key + "); ReplicaChoices: " + replicachoices);
				}

				// fix for GLASSFISH-18085
				String[] members = keyMapper.getCurrentMembers();
				if (members.length == 0) {
					_loadLogger.log(Level.FINE, "Skipped replication of " + key + " since there is only one instance running in the cluster.");
					return null;
				}
				String respondingInstance = null;
				for (int replicaIndex = 0; (replicaIndex < replicaHint.length) && (replicaIndex < MAX_REPLICA_TRIES); replicaIndex++) {
					String target = replicaHint[replicaIndex];
					if (target == null || target.trim().length() == 0 || target.equals(dsc.getInstanceName())) {
						continue;
					}
					LoadRequestCommand<K, V> command = new LoadRequestCommand<K, V>(key, entry == null ? DataStoreEntry.MIN_VERSION : entry.getVersion(),
					        target);
					if (_loadLogger.isLoggable(Level.FINE)) {
						_loadLogger.log(Level.FINE,
						        debugName + "load(" + key + ") Trying to load from Replica[" + replicaIndex + "]: " + replicaHint[replicaIndex]);
					}

					cm.execute(command);
					v = command.getResult(3, TimeUnit.SECONDS);
					if (v != null) {
						respondingInstance = command.getRespondingInstanceName();
						dscMBean.incrementSimpleLoadSuccessCount();
						break;
					}
				}

				if (v == null) {
					if (_loadLogger.isLoggable(Level.FINE)) {
						_loadLogger.log(Level.FINE, debugName + "*load(" + key + ") Performing broadcast load");
					}
					String[] targetInstances = dsc.getKeyMapper().getCurrentMembers();
					for (String targetInstance : targetInstances) {
						if (targetInstance.equals(dsc.getInstanceName())) {
							continue;
						}
						LoadRequestCommand<K, V> lrCmd = new LoadRequestCommand<K, V>(key, entry == null ? DataStoreEntry.MIN_VERSION : entry.getVersion(),
						        targetInstance);
						if (_loadLogger.isLoggable(Level.FINE)) {
							_loadLogger.log(Level.FINE, debugName + "*load(" + key + ") Trying to load from " + targetInstance);
						}

						cm.execute(lrCmd);
						v = lrCmd.getResult(3, TimeUnit.SECONDS);
						if (v != null) {
							respondingInstance = targetInstance;
							dscMBean.incrementBroadcastLoadSuccessCount();
							break;
						}
					}
				}

				if (v != null) {
					entry = replicaStore.getEntry(key);
					if (entry != null) {
						synchronized (entry) {
							if (!entry.isRemoved()) {
								if (dsc.isCacheLocally()) {
									entry.setV(v);
								}

								entry.setLastAccessedAt(System.currentTimeMillis());
								entry.setReplicaInstanceName(respondingInstance);
								// Note: Do not remove the stale replica now. We will
								// do that in save
								if (_loadLogger.isLoggable(Level.FINE)) {
									_loadLogger.log(Level.FINE, debugName + "load(" + key + "; Successfully loaded data from " + respondingInstance);
								}

								dscMBean.incrementLoadSuccessCount();
							} else {
								if (_loadLogger.isLoggable(Level.FINE)) {
									_loadLogger.log(Level.FINE, debugName + "load(" + key + "; Got data from " + respondingInstance
									        + ", but another concurrent thread removed the entry");
								}
								dscMBean.incrementLoadFailureCount();
							}
						}
					}
				} else {
					dscMBean.incrementLoadFailureCount();
				}
			}

			if (_loadLogger.isLoggable(Level.FINE)) {
				_loadLogger.log(Level.FINE, debugName + "load(" + key + ") Final result: " + v);
			}

			if ((v != null) && foundLocally) {
				// Because we did a successful load, to ensure that the data lives in another instance
				// lets do a save

				try {
					String secondaryReplica = put(key, v);
					if (_logger.isLoggable(Level.FINE)) {
						_saveLogger.log(Level.FINE, "(SaveOnLoad) Saved the data to replica: " + secondaryReplica);
					}
				} catch (DataStoreException dsEx) {
					_saveLogger.log(Level.WARNING, "(SaveOnLoad) Failed to save data after a load", dsEx);
				}

			}
			return v;
		} finally {
			dsc.releaseReadLock();
		}
	}

	@Override
	public void remove(K k) throws DataStoreException {

		try {
			dsc.acquireReadLock();
			if (closed.get()) {
				throw new DataStoreAlreadyClosedException("remove() failed. Store " + dsc.getStoreName() + " already closed");
			}

			if (_logger.isLoggable(Level.FINE)) {
				_logger.log(Level.FINE, "DataStore.remove(" + k + ") CALLED ****");
			}

			replicaStore.remove(k);
			dscMBean.incrementRemoveCount();

			String[] targets = dsc.getKeyMapper().getCurrentMembers();

			if (targets != null) {
				for (String target : targets) {
					RemoveCommand<K, V> cmd = new RemoveCommand<K, V>(k);
					cmd.setTarget(target);
					cm.execute(cmd);
				}
			}
		} finally {
			dsc.releaseReadLock();
		}
	}

	@Override
	public String touch(K k, long version, long ts, long ttl) throws DataStoreException {
		String location = "";
		try {
			dsc.acquireReadLock();
			if (closed.get()) {
				throw new DataStoreAlreadyClosedException("touch() failed. Store " + dsc.getStoreName() + " already closed");
			}

			DataStoreEntry<K, V> entry = replicaStore.getEntry(k);
			if (entry != null) {
				synchronized (entry) {
					long now = System.currentTimeMillis();
					entry.setLastAccessedAt(now);
					String target = entry.getReplicaInstanceName();
					TouchCommand<K, V> cmd = new TouchCommand<K, V>(k, version, now, defaultIdleTimeoutInMillis);
					cm.execute(cmd);

					location = cmd.getKeyMappingInfo();
				}
			}
		} finally {
			dsc.releaseReadLock();
		}
		return location;
	}

	@Override
	public int removeIdleEntries(long idleFor) {

		int finalResult = 0;
		String[] targets = dsc.getKeyMapper().getCurrentMembers();

		ResponseMediator respMed = dsc.getResponseMediator();
		CommandResponse resp = respMed.createCommandResponse();
		long tokenId = resp.getTokenId();
		Future<Integer> future = resp.getFuture();
		resp.setTransientResult(new Integer(0));

		try {
			dsc.acquireReadLock();
			if (closed.get()) {
				throw new DataStoreAlreadyClosedException("removeIdleEntries() failed. Store " + dsc.getStoreName() + " already closed");
			}
			if (targets != null && dsc.isBroadcastRemovedExpired()) {
				resp.setExpectedUpdateCount(targets.length);
				for (String target : targets) {
					RemoveExpiredCommand<K, V> cmd = new RemoveExpiredCommand<K, V>(idleFor, tokenId);
					cmd.setTarget(target);
					try {
						cm.execute(cmd);
					} catch (DataStoreException dse) {
						_logger.log(Level.INFO, "Exception during removeIdleEntries...", dse);
					}
				}
			}

			int localResult = replicaStore.removeExpired();
			synchronized (resp) {
				Integer existingValue = (Integer) resp.getTransientResult();
				Integer newResult = new Integer(existingValue.intValue() + localResult);
				resp.setTransientResult(newResult);
			}

			finalResult = (Integer) resp.getTransientResult();

			finalResult = future.get(6, TimeUnit.SECONDS);
		} catch (Exception ex) {
			// TODO
		} finally {
			respMed.removeCommandResponse(tokenId);
			dsc.releaseReadLock();
		}

		return finalResult;

		// return replicaStore.removeExpired();
	}

	@Override
	public void close() {
		try {
			dsc.acquireWriteLock();
			closed.set(true);
			dsc.getCommandManager().close();
			if (mbs != null && mbeanObjectName != null) {
				mbs.unregisterMBean(mbeanObjectName);
			}
		} catch (InstanceNotFoundException inNoEx) {
			// TODO
		} catch (MBeanRegistrationException mbRegEx) {
			// TODO
		} finally {
			dsc.releaseWriteLock();
		}
	}

	@Override
	public void destroy() {
		close();
	}

	public int size() {

		int result = 0;
		try {
			dsc.acquireReadLock();
			if (closed.get()) {
				// Since we cannot throw an Exception and since the store is already closed
				// we return 0
				return 0;
			}

			KeyMapper km = dsc.getKeyMapper();
			String[] targets = km.getCurrentMembers();

			int targetCount = targets.length;
			SizeRequestCommand[] commands = new SizeRequestCommand[targetCount];

			for (int i = 0; i < targetCount; i++) {
				commands[i] = new SizeRequestCommand(targets[i]);
				try {
					dsc.getCommandManager().execute(commands[i]);
				} catch (DataStoreException dse) {
					// TODO:
				}
			}

			for (int i = 0; i < targetCount; i++) {
				result += commands[i].getResult();
			}
		} finally {
			dsc.releaseReadLock();
		}
		return result;
	}
}
