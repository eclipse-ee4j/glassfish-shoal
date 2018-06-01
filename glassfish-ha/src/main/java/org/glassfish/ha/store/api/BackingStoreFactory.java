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

import java.io.Serializable;

/**
 * A factory for creating BackingStore(s). Every provider must provide an
 * implementation of this interface.
 *
 * <p>
 * The <code>createBackingStore(env)</code> method is called typically during
 * container creation time. A store instance is typically used to store state
 * for a single container.
 *
 * <p>
 * Any runtime exception thrown from createBackingStore and
 * createBatchBackingStore method will cause the container to use a default
 * persistence-type (typically no replication) and a log message will be logged
 * at WARNING level.
 *
 * @author Mahesh Kannan
 *
 */
public interface BackingStoreFactory {

    /**
     * This method is called to create a BackingStore. This
     * class must be thread safe.
     * <p>
     * If the factory can produce a BackingStore that can handle the factors
     *  specified in the conf, then it must return a fully initialized and operational BackingStore.
     * Else it must return null.
     *
     * @param conf The BackingStoreConfiguration
     *
     * @return a BackingStore. The returned BackingStore must be thread safe.
     *
     * @throws BackingStoreException
     *             If the store could not be created
     */
    public <K extends Serializable, V extends Serializable> BackingStore<K, V> createBackingStore(BackingStoreConfiguration<K, V> conf)
            throws BackingStoreException;

    /**
     *
     * @return
     */
    public BackingStoreTransaction createBackingStoreTransaction();

}
