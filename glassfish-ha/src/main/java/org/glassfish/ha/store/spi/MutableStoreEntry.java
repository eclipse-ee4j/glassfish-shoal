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

package org.glassfish.ha.store.spi;

import java.util.Set;


/**
 * A Storeable is an interface that must be implemented by objects that
 *  are to be presisted in the store.
 *
 * @author Mahesh.Kannan@Sun.Com
 */
public interface MutableStoreEntry
    extends Storable {

    /**
     * Mark the entire store entry as dirty
     *
     */
    public void _markStoreEntryAsDirty();

    /**
     * The store name for which this Storable was created
     *
     * @return The store name
     */
    public void _markAsDirty(int attrIndex);

    /**
     * The String that can be used by the store implementation to hash the StoreEntry
     *
     * @return A (possibly null) key to be used for hashing purpose
     */
    public void _markAsClean(int attrIndex);

    /**
     * Get the version of this entry. A null value means that this entry
     *
     * @return The version or null if this entry has no version
     */
    public void _markStoreEntryAsClean();

    /**
     * Set the replicating ownerid
     */
    public void _setOwnerId(String ownerName);

}
