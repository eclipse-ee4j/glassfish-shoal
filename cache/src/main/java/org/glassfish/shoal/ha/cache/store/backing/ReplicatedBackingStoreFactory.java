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

package org.glassfish.shoal.ha.cache.store.backing;

import java.io.Serializable;
import java.util.Properties;

import org.glassfish.ha.store.api.BackingStore;
import org.glassfish.ha.store.api.BackingStoreConfiguration;
import org.glassfish.ha.store.api.BackingStoreException;
import org.glassfish.ha.store.api.BackingStoreFactory;
import org.glassfish.ha.store.api.BackingStoreTransaction;

/**
 * @author Mahesh Kannan
 */
public class ReplicatedBackingStoreFactory implements BackingStoreFactory {

    private Properties props;

    public ReplicatedBackingStoreFactory() {
    }

    public ReplicatedBackingStoreFactory(Properties p) {
        this.props = p;
    }

    @Override
    public <K extends Serializable, V extends Serializable> BackingStore<K, V> createBackingStore(BackingStoreConfiguration<K, V> conf)
            throws BackingStoreException {

        ReplicatedBackingStore<K, V> store = new ReplicatedBackingStore<K, V>();
        store.setBackingStoreFactory(this);
        store.initialize(conf);

        return store;
    }

    @Override
    public BackingStoreTransaction createBackingStoreTransaction() {
        return new BackingStoreTransaction() {
            @Override
            public void commit() throws BackingStoreException {
            }
        };
    }
}
