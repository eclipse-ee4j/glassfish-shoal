/*
 * Copyright (c) 1997, 2018 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024 Contributors to the Eclipse Foundation. All rights reserved.
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

package org.glassfish.shoal.ha.cache.api;

import java.io.Serializable;

import org.glassfish.shoal.ha.cache.group.GroupService;
import org.glassfish.shoal.ha.cache.group.GroupServiceFactory;
import org.glassfish.shoal.ha.cache.mapper.DefaultKeyMapper;
import org.glassfish.shoal.ha.cache.store.ReplicatedDataStore;

/**
 * @author Mahesh Kannan
 */
public class DataStoreFactory {

    public static DataStore<String, Serializable> createDataStore(String storeName, String instanceName, String groupName) {
        DefaultKeyMapper keyMapper = new DefaultKeyMapper(instanceName);

        Class<Serializable> vClazz = Serializable.class;
        DataStoreContext<String, Serializable> conf = new DataStoreContext<String, Serializable>();
        conf.setStartGMS(true);
        conf.setStoreName(storeName).setInstanceName(instanceName).setGroupName(groupName).setKeyClazz(String.class).setValueClazz(vClazz)
                .setKeyMapper(keyMapper).setDoAddCommands().setDoSynchronousReplication(false);

        return createDataStore(conf);
    }

    /*
     * public static <K, V extends Serializable> DataStore<K, V> createDataStore(String storeName, String instanceName,
     * String groupName, Class<K> keyClazz, Class<V> vClazz, ClassLoader loader) { if (loader == null) { loader =
     * ClassLoader.getSystemClassLoader(); } DefaultObjectInputOutputStreamFactory factory = new
     * DefaultObjectInputOutputStreamFactory(); DataStoreKeyHelper<K> keyHelper = new ObjectKeyHelper(loader, factory);
     * DefaultKeyMapper keyMapper = new DefaultKeyMapper(instanceName, groupName);
     *
     * DataStoreConfigurator<K, V> conf = new DataStoreConfigurator<K, V>(); conf.setStartGMS(true);
     * conf.setStoreName(storeName) .setInstanceName(instanceName) .setGroupName(groupName) .setKeyClazz(keyClazz)
     * .setValueClazz(vClazz) .setClassLoader(loader) .setDataStoreKeyHelper(keyHelper) .setKeyMapper(keyMapper)
     * .setObjectInputOutputStreamFactory(factory);
     *
     * return createDataStore(conf); }
     *
     * public static <K, V extends Serializable> DataStore<K, V> createDataStore(String storeName, String instanceName,
     * String groupName, Class<K> keyClazz, Class<V> vClazz, ClassLoader loader, DataStoreEntryHelper<K, V> helper,
     * DataStoreKeyHelper<K> keyHelper, KeyMapper keyMapper) { if (loader == null) { loader =
     * ClassLoader.getSystemClassLoader(); }
     *
     * DataStoreConfigurator<K, V> conf = new DataStoreConfigurator<K, V>(); conf.setStartGMS(true);
     * conf.setStoreName(storeName) .setInstanceName(instanceName) .setGroupName(groupName) .setKeyClazz(keyClazz)
     * .setValueClazz(vClazz) .setClassLoader(loader) .setDataStoreEntryHelper(helper) .setDataStoreKeyHelper(keyHelper)
     * .setKeyMapper(keyMapper) .setObjectInputOutputStreamFactory(new DefaultObjectInputOutputStreamFactory());
     *
     * return createDataStore(conf); }
     */

    public static <K, V extends Serializable> DataStore<K, V> createDataStore(DataStoreContext<K, V> conf) {
        GroupService gs = GroupServiceFactory.getInstance().getGroupService(conf.getInstanceName(), conf.getGroupName(), conf.isStartGMS());
        return new ReplicatedDataStore<K, V>(conf, gs);
    }

}
