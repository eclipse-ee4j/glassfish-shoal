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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.Set;


/**
 * A Storeable is an interface that must be implemented by objects that
 * are to be presisted in the backing store.
 *
 * @author Mahesh.Kannan@Sun.Com
 */
public interface Storeable
        extends Serializable {

    /**
     * Get the version of this entry. -1 means that this entry has no version
     *
     * @return The version or null if this entry has no version
     */
    public long _storeable_getVersion();

    public void _storeable_setVersion(long version);

    public long _storeable_getLastAccessTime();

    public void _storeable_setLastAccessTime(long version);

    public long _storeable_getMaxIdleTime();

    public void _storeable_setMaxIdleTime(long version);

    /**
     * Providers can cache this
     * @return an array of attribute names
     */
    public String[] _storeable_getAttributeNames();

    /**
     * Providers can cache this
     * @return  A boolean array each representing the dirty status of the attribute whose name
     *  can be found at the same index in the array returned by _getAttributeNames()
     */
    public boolean[] _storeable_getDirtyStatus();

    public void _storeable_writeState(OutputStream os)
        throws IOException;

    public void _storeable_readState(InputStream is)
        throws IOException;

}
