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

package org.glassfish.ha.store.api;

import java.io.File;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * @author Mahesh Kannan
 */
public class BackingStoreConfiguration<K extends Serializable, V extends Serializable> {

	public static final String BASE_DIRECTORY_NAME = "base.directory.name";

	public static final String NO_OP_PERSISTENCE_TYPE = "noop";

	public static final String START_GMS = "start.gms";

	private String clusterName;

	private String instanceName;

	private String storeName;

	private String shortUniqueName;

	private String storeType;

	private long maxIdleTimeInSeconds = -1;

	private String relaxVersionCheck;

	private long maxLoadWaitTimeInSeconds;

	private File baseDirectory;

	private Class<K> keyClazz;

	private Class<V> valueClazz;

	private boolean synchronousSave;

	private long typicalPayloadSizeInKiloBytes;

	private Logger logger;

	private Map<String, Object> vendorSpecificSettings = new HashMap<String, Object>();

	private ClassLoader classLoader;

	private boolean startGroupService;

	public String getClusterName() {
		return clusterName;
	}

	public BackingStoreConfiguration<K, V> setClusterName(String clusterName) {
		this.clusterName = clusterName;
		return this;
	}

	public String getInstanceName() {
		return instanceName;
	}

	public BackingStoreConfiguration<K, V> setInstanceName(String instanceName) {
		this.instanceName = instanceName;
		return this;
	}

	public String getStoreName() {
		return storeName;
	}

	public BackingStoreConfiguration<K, V> setStoreName(String storeName) {
		this.storeName = storeName;
		return this;
	}

	public String getShortUniqueName() {
		return shortUniqueName;
	}

	public BackingStoreConfiguration<K, V> setShortUniqueName(String shortUniqueName) {
		this.shortUniqueName = shortUniqueName;
		return this;
	}

	public String getStoreType() {
		return storeType;
	}

	public BackingStoreConfiguration<K, V> setStoreType(String storeType) {
		this.storeType = storeType;
		return this;
	}

	public long getMaxIdleTimeInSeconds() {
		return maxIdleTimeInSeconds;
	}

	public BackingStoreConfiguration<K, V> setMaxIdleTimeInSeconds(long maxIdleTimeInSeconds) {
		this.maxIdleTimeInSeconds = maxIdleTimeInSeconds;
		return this;
	}

	public String getRelaxVersionCheck() {
		return relaxVersionCheck;
	}

	public BackingStoreConfiguration<K, V> setRelaxVersionCheck(String relaxVersionCheck) {
		this.relaxVersionCheck = relaxVersionCheck;
		return this;
	}

	public long getMaxLoadWaitTimeInSeconds() {
		return maxLoadWaitTimeInSeconds;
	}

	public BackingStoreConfiguration<K, V> setMaxLoadWaitTimeInSeconds(long maxLoadWaitTimeInSeconds) {
		this.maxLoadWaitTimeInSeconds = maxLoadWaitTimeInSeconds;
		return this;
	}

	public File getBaseDirectory() {
		return baseDirectory;
	}

	public BackingStoreConfiguration<K, V> setBaseDirectory(File baseDirectory) {
		this.baseDirectory = baseDirectory;
		return this;
	}

	public Class<K> getKeyClazz() {
		return keyClazz;
	}

	public BackingStoreConfiguration<K, V> setKeyClazz(Class<K> kClazz) {
		this.keyClazz = kClazz;
		return this;
	}

	public Class<V> getValueClazz() {
		return valueClazz;
	}

	public BackingStoreConfiguration<K, V> setValueClazz(Class<V> vClazz) {
		this.valueClazz = vClazz;
		return this;
	}

	public boolean isSynchronousSave() {
		return synchronousSave;
	}

	public BackingStoreConfiguration<K, V> setSynchronousSave(boolean synchronousSave) {
		this.synchronousSave = synchronousSave;
		return this;
	}

	public long getTypicalPayloadSizeInKiloBytes() {
		return typicalPayloadSizeInKiloBytes;
	}

	public BackingStoreConfiguration<K, V> setTypicalPayloadSizeInKiloBytes(long typicalPayloadSizeInKiloBytes) {
		this.typicalPayloadSizeInKiloBytes = typicalPayloadSizeInKiloBytes;
		return this;
	}

	public Logger getLogger() {
		return logger;
	}

	public BackingStoreConfiguration<K, V> setLogger(Logger logger) {
		this.logger = logger;
		return this;
	}

	public Map<String, Object> getVendorSpecificSettings() {
		return vendorSpecificSettings;
	}

	public ClassLoader getClassLoader() {
		return classLoader;
	}

	public BackingStoreConfiguration<K, V> setClassLoader(ClassLoader classLoader) {
		this.classLoader = classLoader;
		return this;
	}

	public boolean getStartGroupService() {
		return startGroupService;
	}

	public BackingStoreConfiguration<K, V> setStartGroupService(boolean startGroupService) {
		this.startGroupService = startGroupService;
		return this;
	}

	@Override
	public String toString() {
		return "BackingStoreConfiguration{" + "clusterName='" + clusterName + '\'' + ", instanceName='" + instanceName + '\'' + ", storeName='" + storeName
		        + '\'' + ", shortUniqueName='" + shortUniqueName + '\'' + ", storeType='" + storeType + '\'' + ", maxIdleTimeInSeconds=" + maxIdleTimeInSeconds
		        + ", relaxVersionCheck='" + relaxVersionCheck + '\'' + ", maxLoadWaitTimeInSeconds=" + maxLoadWaitTimeInSeconds + ", baseDirectoryName='"
		        + baseDirectory + '\'' + ", keyClazz=" + keyClazz + ", valueClazz=" + valueClazz + ", synchronousSave=" + synchronousSave
		        + ", typicalPayloadSizeInKiloBytes=" + typicalPayloadSizeInKiloBytes + ", vendorSpecificSettings=" + vendorSpecificSettings + '}';
	}
}
