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

import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.ha.store.api.BackingStoreConfiguration;
import org.glassfish.ha.store.util.KeyTransformer;
import org.shoal.ha.cache.impl.command.CommandManager;
import org.shoal.ha.cache.impl.store.ReplicaStore;
import org.shoal.ha.cache.impl.util.ResponseMediator;
import org.shoal.ha.group.GroupService;
import org.shoal.ha.mapper.KeyMapper;

/**
 * @author Mahesh Kannan
 */
public class DataStoreContext<K, V> extends DataStoreConfigurator<K, V> {

	private static final Logger _logger = Logger.getLogger(ShoalCacheLoggerConstants.CACHE_DATA_STORE);

	private CommandManager<K, V> cm;

	private ResponseMediator responseMediator;

	private GroupService groupService;

	private ReplicaStore<K, V> replicaStore;

	private ReplicatedDataStoreStatsHolder dscMBean;

	private ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock(true);

	public DataStoreContext(String serviceName, GroupService gs, ClassLoader loader) {
		super.setStoreName(serviceName);
		super.setInstanceName(gs.getMemberName());
		this.groupService = gs;
		super.setClassLoader(loader);
	}

	public DataStoreContext() {
		super();
	}

	public void acquireReadLock() {
		rwLock.readLock().lock();
	}

	public void releaseReadLock() {
		rwLock.readLock().unlock();
	}

	public void acquireWriteLock() {
		rwLock.writeLock().lock();
	}

	public void releaseWriteLock() {
		rwLock.writeLock().unlock();
	}

	public DataStoreContext(BackingStoreConfiguration conf) {
		setInstanceName(conf.getInstanceName()).setGroupName(conf.getClusterName()).setStoreName(conf.getStoreName()).setKeyClazz(conf.getKeyClazz())
		        .setValueClazz(conf.getValueClazz());

		if (conf.getClassLoader() != null) {
			_logger.log(Level.FINE, "**DSC[" + conf.getStoreName() + "] Client supplied ClassLoader : " + conf.getClassLoader());
			setClassLoader(conf.getClassLoader());
		}

		Map<String, Object> vendorSpecificMap = conf.getVendorSpecificSettings();

		Object stGMS = vendorSpecificMap.get("start.gms");
		boolean startGMS = false;
		if (stGMS != null) {
			if (stGMS instanceof String) {
				try {
					startGMS = Boolean.valueOf((String) stGMS);
				} catch (Throwable th) {
					// Ignore
				}
			} else if (stGMS instanceof Boolean) {
				startGMS = (Boolean) stGMS;
			}
		}

		Object cacheLocally = vendorSpecificMap.get("local.caching");
		boolean enableLocalCaching = false;
		if (cacheLocally != null) {
			if (cacheLocally instanceof String) {
				try {
					enableLocalCaching = Boolean.valueOf((String) cacheLocally);
				} catch (Throwable th) {
					// Ignore
				}
			} else if (cacheLocally instanceof Boolean) {
				enableLocalCaching = (Boolean) stGMS;
			}
		}

		if (getClassLoader() == null) {
			ClassLoader cl = (ClassLoader) vendorSpecificMap.get("class.loader");
			_logger.log(Level.FINE, "**DSC[" + conf.getStoreName() + "] vendorMap.classLoader CLASS LOADER: " + cl);
			if (cl == null) {
				cl = conf.getValueClazz().getClassLoader();
				_logger.log(Level.FINE, "**DSC[" + conf.getStoreName() + "] USING VALUE CLASS CLASS LOADER: " + conf.getValueClazz().getName());
			}
			if (cl == null) {
				cl = ClassLoader.getSystemClassLoader();
				_logger.log(Level.FINE, "**DSC[" + conf.getStoreName() + "] USING system CLASS CLASS LOADER: " + cl);
			}

			_logger.log(Level.FINE, "**DSC[" + conf.getStoreName() + "] FINALLY USING CLASS CLASS LOADER: " + cl);
			setClassLoader(cl);
		}

		setStartGMS(startGMS).setCacheLocally(enableLocalCaching);

		boolean asyncReplication = vendorSpecificMap.get("async.replication") == null ? true : (Boolean) vendorSpecificMap.get("async.replication");
		setDoSynchronousReplication(!asyncReplication);

		KeyMapper keyMapper = (KeyMapper) vendorSpecificMap.get("key.mapper");
		if (keyMapper != null) {
			setKeyMapper(keyMapper);
		}

		KeyTransformer<K> kt = (KeyTransformer<K>) vendorSpecificMap.get("key.transformer");
		if (kt != null) {
			super.setKeyTransformer(kt);
			_logger.log(Level.FINE, "** USING CLIENT DEFINED KeyTransfomer: " + super.getKeyTransformer().getClass().getName());
		}

		/*
		 * dsConf.addCommand(new SaveCommand<K, V>()); dsConf.addCommand(new SimpleAckCommand<K, V>()); dsConf.addCommand(new
		 * RemoveCommand<K, V>(null)); dsConf.addCommand(new LoadRequestCommand<K, V>()); dsConf.addCommand(new
		 * LoadResponseCommand<K, V>()); dsConf.addCommand(new StaleCopyRemoveCommand<K, V>()); dsConf.addCommand(new
		 * TouchCommand<K, V>()); dsConf.addCommand(new SizeRequestCommand<K, V>()); dsConf.addCommand(new
		 * SizeResponseCommand<K, V>()); dsConf.addCommand(new NoOpCommand<K, V>());
		 */

		Object idleTimeInSeconds = vendorSpecificMap.get("max.idle.timeout.in.seconds");
		if (idleTimeInSeconds != null) {
			long defaultMaxIdleTimeInMillis = -1;
			if (idleTimeInSeconds instanceof Long) {
				defaultMaxIdleTimeInMillis = (Long) idleTimeInSeconds;
			} else if (idleTimeInSeconds instanceof String) {
				try {
					defaultMaxIdleTimeInMillis = Long.valueOf((String) idleTimeInSeconds);
				} catch (Exception ex) {
					// Ignore
				}
			}

			setDefaultMaxIdleTimeInMillis(defaultMaxIdleTimeInMillis * 1000);
		}

		Object safeToDelayCaptureStateObj = vendorSpecificMap.get("value.class.is.thread.safe");
		if (safeToDelayCaptureStateObj != null) {
			boolean safeToDelayCaptureState = true;
			if (safeToDelayCaptureStateObj instanceof Boolean) {
				safeToDelayCaptureState = (Boolean) safeToDelayCaptureStateObj;
			} else if (safeToDelayCaptureStateObj instanceof String) {
				try {
					safeToDelayCaptureState = Boolean.valueOf((String) safeToDelayCaptureStateObj);
				} catch (Exception ex) {
					// Ignore
				}
			}

			setSafeToDelayCaptureState(safeToDelayCaptureState);
		}

		Object bcastRemExpObj = vendorSpecificMap.get("broadcast.remove.expired");
		if (bcastRemExpObj != null) {
			boolean bcastRemExp = true;
			if (bcastRemExpObj instanceof Boolean) {
				bcastRemExp = (Boolean) bcastRemExpObj;
			} else if (bcastRemExpObj instanceof String) {
				try {
					bcastRemExp = Boolean.valueOf((String) bcastRemExpObj);
				} catch (Exception ex) {
					// Ignore
				}
			}

			setBroadcastRemovedExpired(bcastRemExp);
		}
	}

	public void setDataStoreMBean(ReplicatedDataStoreStatsHolder<K, V> dscMBean) {
		this.dscMBean = dscMBean;
	}

	public ReplicatedDataStoreStatsHolder<K, V> getDataStoreMBean() {
		return dscMBean;
	}

	public String getServiceName() {
		return super.getStoreName();
	}

	public CommandManager<K, V> getCommandManager() {
		return cm;
	}

	public ResponseMediator getResponseMediator() {
		return responseMediator;
	}

	public void setResponseMediator(ResponseMediator responseMediator) {
		this.responseMediator = responseMediator;
	}

	public GroupService getGroupService() {
		return groupService;
	}

	public void setCommandManager(CommandManager<K, V> cm) {
		this.cm = cm;
	}

	public void setGroupService(GroupService groupService) {
		this.groupService = groupService;
	}

	public void setReplicaStore(ReplicaStore<K, V> replicaStore) {
		this.replicaStore = replicaStore;
	}

	public ReplicaStore<K, V> getReplicaStore() {
		return replicaStore;
	}

}
