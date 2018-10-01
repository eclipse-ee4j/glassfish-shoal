/*
 * Copyright (c) 2010, 2018 Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.glassfish.ha.store.api.BackingStoreConfiguration;
import org.shoal.ha.cache.api.DataStoreContext;

/**
 * @author Mahesh Kannan
 *
 */
public class RepliatedBackingStoreRegistry {

	private static Map<String, DataStoreContext> _contexts = new ConcurrentHashMap<String, DataStoreContext>();

	private static Map<String, BackingStoreConfiguration> _confs = new ConcurrentHashMap<String, BackingStoreConfiguration>();

	public static synchronized final void registerStore(String name, BackingStoreConfiguration conf, DataStoreContext ctx) {
		_contexts.put(name, ctx);
		_confs.put(name, conf);
	}

	public static synchronized final void unregisterStore(String name) {
		_contexts.remove(name);
		_confs.remove(name);
	}

	public static final Collection<String> getStoreNames() {
		return _contexts.keySet();
	}

	public static final Collection<BackingStoreConfiguration> getConfigurations() {
		return _confs.values();
	}

	public static final DataStoreContext getContext(String name) {
		return _contexts.get(name);
	}

}
