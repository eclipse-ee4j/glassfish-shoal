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

import org.glassfish.ha.store.api.BackingStoreException;
import org.glassfish.ha.store.api.BackingStoreFactory;
import org.glassfish.ha.store.impl.NoOpBackingStoreFactory;

import java.util.Properties;
import java.util.HashMap;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author bhavanishankar@dev.java.net
 */

/**
 * A class for storing BackingStore implementation. This is a
 * singleton and contains a mapping between persistence-type and
 * <code>BackingStoreFactory</code>.
 *
 * @author Mahesh.Kannan@Sun.Com
 * @author Larry.White@Sun.Com
 *
 */
public final class BackingStoreFactoryRegistry {

    private static final HashMap<String, BackingStoreFactory> factories =
            new HashMap<String, BackingStoreFactory>();

    static {
        factories.put("noop", new NoOpBackingStoreFactory());
    }

    public static synchronized void register(String type, BackingStoreFactory factory)
            throws DuplicateFactoryRegistrationException {
        if (factories.get(type) != null) {
            throw new DuplicateFactoryRegistrationException("BackingStoreFactory " +
                    "for persistene-type " + type + " already exists");
        }
        factories.put(type, factory);
        Logger.getLogger(BackingStoreFactoryRegistry.class.getName()).log(Level.INFO, "Registered "
            + factory.getClass().getName() + " for persistence-type = " + type
            + " in BackingStoreFactoryRegistry");
    }

    /**
     * Return an instance of BackingStoreFactory for the
     * specified type. If a factory instance for this persistence
     * type has not yet been instantiated then an instance is
     * created using the public no-arg constructor.
     */
    public static synchronized BackingStoreFactory getFactoryInstance(String type)
            throws BackingStoreException {
        BackingStoreFactory factory = factories.get(type);
        if (factory == null) {
            throw new BackingStoreException("Backing store for " +
                        "persistence-type " + type + " is not registered.");
        }
        
        return factory;
    }

    /**
     * Will be called by Store's Lifecycle module to unregister
     * the factory class name.
     */
    public static synchronized void unregister(String type) {
        factories.remove(type);
    }
    
}

