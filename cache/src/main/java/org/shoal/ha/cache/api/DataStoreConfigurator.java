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

package org.shoal.ha.cache.api;

import java.util.ArrayList;
import java.util.List;

import org.glassfish.ha.store.util.KeyTransformer;
import org.shoal.ha.cache.impl.command.Command;
import org.shoal.ha.cache.impl.store.DataStoreEntryUpdater;
import org.shoal.ha.mapper.KeyMapper;

/**
 * @author Mahesh Kannan
 */
public class DataStoreConfigurator<K, V> {

    private String instanceName;

    private String groupName;

    private String storeName;

    private Class<K> keyClazz;

    private Class<V> valueClazz;

    private KeyMapper keyMapper;

    private boolean startGMS;

    private ClassLoader clazzLoader;

    private boolean cacheLocally;

    private boolean doSynchronousReplication;

    private List<Command<K, ? super V>> commands = new ArrayList<Command<K, ? super V>>();

    private boolean addCommands;

    private IdleEntryDetector<K, V> idleEntryDetector;

    private long defaultMaxIdleTimeInMillis = -1;

    private DataStoreEntryUpdater<K, V> dseUpdater;

    private boolean safeToDelayCaptureState = true;

    private boolean useMapToCacheCommands = true;

    private KeyTransformer<K> keyTransformer;

    private boolean broadcastRemovedExpired = true;

    protected DataStoreConfigurator() {

    }

    public String getInstanceName() {
        return instanceName;
    }

    public DataStoreConfigurator<K, V> setInstanceName(String instanceName) {
        this.instanceName = instanceName;
        return this;
    }

    public String getGroupName() {
        return groupName;
    }

    public DataStoreConfigurator<K, V> setGroupName(String groupName) {
        this.groupName = groupName;
        return this;
    }

    public String getStoreName() {
        return storeName;
    }

    public DataStoreConfigurator<K, V> setStoreName(String storeName) {
        this.storeName = storeName;
        return this;
    }

    public Class<K> getKeyClazz() {
        return keyClazz;
    }

    public DataStoreConfigurator<K, V> setKeyClazz(Class<K> kClazz) {
        this.keyClazz = kClazz;
        return this;
    }

    public Class<V> getValueClazz() {
        return valueClazz;
    }

    public DataStoreConfigurator<K, V> setValueClazz(Class<V> vClazz) {
        this.valueClazz = vClazz;
        return this;
    }

    public KeyMapper getKeyMapper() {
        return keyMapper;
    }

    public DataStoreConfigurator<K, V> setKeyMapper(KeyMapper keyMapper) {
        this.keyMapper = keyMapper;
        return this;
    }

    public boolean isStartGMS() {
        return startGMS;
    }

    public DataStoreConfigurator<K, V> setStartGMS(boolean startGMS) {
        this.startGMS = startGMS;
        return this;
    }

    public ClassLoader getClassLoader() {
        return clazzLoader;
    }

    public DataStoreConfigurator<K, V> setClassLoader(ClassLoader loader) {
        this.clazzLoader = loader == null ? ClassLoader.getSystemClassLoader() : loader;
        return this;
    }

    public boolean isCacheLocally() {
        return cacheLocally;
    }

    public DataStoreConfigurator<K, V> setCacheLocally(boolean cacheLocally) {
        this.cacheLocally = cacheLocally;
        return this;
    }

    public boolean isDoSynchronousReplication() {
        return doSynchronousReplication;
    }

    public DataStoreConfigurator<K, V> setDoSynchronousReplication(boolean val) {
        this.doSynchronousReplication = val;
        return this;
    }

    public List<Command<K, ? super V>> getCommands() {
        return commands;
    }

    public void addCommand(Command<K, V> cmd) {
        commands.add(cmd);
    }

    public DataStoreConfigurator<K, V> setDoAddCommands() {
        addCommands = true;
        return this;
    }

    public boolean isDoAddCommands() {
        return addCommands;
    }

    public IdleEntryDetector<K, V> getIdleEntryDetector() {
        return idleEntryDetector;
    }

    public DataStoreConfigurator<K, V> setIdleEntryDetector(IdleEntryDetector<K, V> idleEntryDetector) {
        this.idleEntryDetector = idleEntryDetector;
        return this;
    }

    public long getDefaultMaxIdleTimeInMillis() {
        return defaultMaxIdleTimeInMillis;
    }

    public DataStoreConfigurator<K, V> setDefaultMaxIdleTimeInMillis(long defaultMaxIdleTimeInMillis) {
        this.defaultMaxIdleTimeInMillis = defaultMaxIdleTimeInMillis;
        return this;
    }

    public DataStoreEntryUpdater<K, V> getDataStoreEntryUpdater() {
        return dseUpdater;
    }

    public DataStoreConfigurator<K, V> setDataStoreEntryUpdater(DataStoreEntryUpdater<K, V> dseUpdater) {
        this.dseUpdater = dseUpdater;
        return this;
    }

    public boolean isSafeToDelayCaptureState() {
        return safeToDelayCaptureState;
    }

    public DataStoreConfigurator<K, V> setSafeToDelayCaptureState(boolean safeToDelayCaptureState) {
        this.safeToDelayCaptureState = safeToDelayCaptureState;
        return this;
    }

    public boolean isUseMapToCacheCommands() {
        return useMapToCacheCommands;
    }

    public DataStoreConfigurator<K, V> setUseMapToCacheCommands(boolean useMapToCacheCommands) {
        this.useMapToCacheCommands = useMapToCacheCommands;
        return this;
    }

    public KeyTransformer<K> getKeyTransformer() {
        return keyTransformer;
    }

    public DataStoreConfigurator<K, V> setKeyTransformer(KeyTransformer<K> keyTransformer) {
        this.keyTransformer = keyTransformer;
        return this;
    }

    public boolean isBroadcastRemovedExpired() {
        return broadcastRemovedExpired;
    }

    public DataStoreConfigurator<K, V> setBroadcastRemovedExpired(boolean broadcastRemovedExpired) {
        this.broadcastRemovedExpired = broadcastRemovedExpired;
        return this;
    }

    @Override
    public String toString() {
        return "DataStoreConfigurator{" + "instanceName='" + instanceName + '\'' + ", groupName='" + groupName + '\'' + ", storeName='" + storeName + '\''
                + ", keyClazz=" + keyClazz + ", valueClazz=" + valueClazz + ", keyMapper=" + keyMapper + ", startGMS=" + startGMS + ", cacheLocally= "
                + cacheLocally + ", clazzLoader=" + clazzLoader + ", doSynchronousReplication=" + doSynchronousReplication + ", broadcastRemovedExpired="
                + broadcastRemovedExpired + ", keyTransformer=" + ((keyTransformer == null) ? null : keyTransformer.getClass().getName()) + '}';
    }
}
