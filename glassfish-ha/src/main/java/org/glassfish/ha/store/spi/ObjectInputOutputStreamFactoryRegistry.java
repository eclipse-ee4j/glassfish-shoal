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

import java.io.*;

/**
 * Temporary class till we figure out how to reuse a similar class in naming module.
 * We do not want to add a dependency on naming (because of metro)
 *  
 * @author Mahesh Kannan
 */
public class ObjectInputOutputStreamFactoryRegistry {

    private static ObjectInputOutputStreamFactory _factory = new DefaultObjectInputOutputStreamFactory();

    public static ObjectInputOutputStreamFactory getObjectInputOutputStreamFactory() {
        return _factory;
    }

    private static class DefaultObjectInputOutputStreamFactory
        implements ObjectInputOutputStreamFactory {
        
        @Override
        public ObjectOutputStream createObjectOutputStream(OutputStream os)
            throws IOException {
            return new ObjectOutputStream(os);
        }

        @Override
        public ObjectInputStream createObjectInputStream(InputStream is, ClassLoader loader)
            throws IOException {
            return new ObjectInputStreamWithLoader(is, loader);
        }
    }

}
