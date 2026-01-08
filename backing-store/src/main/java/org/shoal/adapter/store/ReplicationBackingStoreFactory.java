/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation.
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
import java.lang.System.Logger;
import java.lang.System.Logger.Level;

import org.glassfish.ha.store.api.BackingStore;
import org.glassfish.ha.store.api.BackingStoreConfiguration;
import org.glassfish.ha.store.api.BackingStoreException;
import org.glassfish.ha.store.api.BackingStoreFactory;
import org.glassfish.ha.store.api.BackingStoreTransaction;

/**
 * @author Mahesh Kannan
 */
public class ReplicationBackingStoreFactory implements BackingStoreFactory {

    private static final Logger LOG = System.getLogger(ReplicationBackingStoreFactory.class.getName());

    @Override
    public <K extends Serializable, V extends Serializable> BackingStore<K, V> createBackingStore(
        BackingStoreConfiguration<K, V> conf) throws BackingStoreException {
        InMemoryBackingStore<K, V> bStore = new InMemoryBackingStore<>();
        bStore.initialize(conf);
        LOG.log(Level.INFO, "CREATED an instance of {0}", bStore.getClass());
        return bStore;
    }


    @Override
    public BackingStoreTransaction createBackingStoreTransaction() {
        return new NoCommitBackingStoreTransaction();
    }
}
