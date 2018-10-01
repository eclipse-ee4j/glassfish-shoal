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

package org.shoal.adapter.store;

import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.ha.store.api.BackingStore;
import org.glassfish.ha.store.api.BackingStoreConfiguration;
import org.glassfish.ha.store.api.BackingStoreException;
import org.glassfish.ha.store.api.BackingStoreFactory;
import org.shoal.ha.cache.api.DataStore;
import org.shoal.ha.cache.api.DataStoreContext;
import org.shoal.ha.cache.api.DataStoreException;
import org.shoal.ha.cache.api.DataStoreFactory;
import org.shoal.ha.cache.api.ShoalCacheLoggerConstants;
import org.shoal.ha.cache.impl.store.ReplicatedDataStore;

/**
 * @author Mahesh Kannan
 */
public class ReplicatedBackingStore<K extends Serializable, V extends Serializable> extends BackingStore<K, V> {

    private static final Logger _logger = Logger.getLogger(ShoalCacheLoggerConstants.CACHE_DATA_STORE);

    private String storeName = "";

    private DataStore<K, V> dataStore;

    private ReplicatedBackingStoreFactory factory;

    private long defaultMaxIdleTimeInMillis;

    /* package */ void setBackingStoreFactory(ReplicatedBackingStoreFactory factory) {
        this.factory = factory;
    }

    public BackingStoreFactory getBackingStoreFactory() {
        return factory;
    }

    public DataStoreContext<K, V> getDataStoreContext() {
        return dataStore == null ? null : ((ReplicatedDataStore) dataStore).getDataStoreContext();
    }

    @Override
    public void initialize(BackingStoreConfiguration<K, V> conf) throws BackingStoreException {
        super.initialize(conf);

        DataStoreContext<K, V> dsConf = new DataStoreContext<K, V>(conf);
        dataStore = DataStoreFactory.createDataStore(dsConf);

        storeName = dsConf.getStoreName();
    }

    @Override
    public V load(K key, String versionInfo) throws BackingStoreException {
        try {
            return dataStore.get(key);
        } catch (DataStoreException dsEx) {
            throw new BackingStoreException("Error during load: " + key, dsEx);
        }
    }

    @Override
    public String save(K key, V value, boolean isNew) throws BackingStoreException {
        try {
            return dataStore.put(key, value);
        } catch (DataStoreException dsEx) {
            throw new BackingStoreException("Error during save: " + key, dsEx);
        }
    }

    @Override
    public void remove(K key) throws BackingStoreException {
        try {
            if (dataStore != null) {
                dataStore.remove(key);
            }
        } catch (DataStoreException dsEx) {
            throw new BackingStoreException("Error during remove: " + key, dsEx);
        }
    }

    @Override
    public int removeExpired(long idleTime) throws BackingStoreException {
        return dataStore.removeIdleEntries(idleTime);
    }

    @Override
    public int size() throws BackingStoreException {
        return dataStore.size();
    }

    @Override
    public void close() throws BackingStoreException {
        destroy();
    }

    @Override
    public void destroy() throws BackingStoreException {
        if (dataStore != null) {
            dataStore.close();
            _logger.log(Level.FINE, "** StoreName = " + storeName + " is destroyed ");
        } else {
            _logger.log(Level.FINE, "** StoreName = " + storeName + " is already destroyed ");
        }
        dataStore = null;
        factory = null;
    }

    @Override
    public void updateTimestamp(K key, long time) throws BackingStoreException {
        try {
            dataStore.touch(key, Long.MAX_VALUE - 1, time, defaultMaxIdleTimeInMillis);
        } catch (DataStoreException dsEx) {
            throw new BackingStoreException("Error during updateTimestamp: " + key, dsEx);
        }
    }

}
