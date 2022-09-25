/*
 * Copyright (c) 1997, 2018 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022 Contributors to the Eclipse Foundation. All rights reserved.
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

package com.sun.enterprise.ee.cms.impl.common;

import java.io.Serializable;
import java.util.concurrent.ConcurrentHashMap;

import com.sun.enterprise.ee.cms.core.GMSCacheable;

/**
 * This is a wrapper Serializable dedicated to support the DistributedStateCache such that the message ncapsulates the
 * operation for which the message is intended at the receivers end.
 *
 * @author Shreedhar Ganapathy Date: May 9, 2005
 * @version $Revision$
 */
public class DSCMessage implements Serializable {
    static final long serialVersionUID = -3594369933952520038L;

    public static enum OPERATION {
        ADD, REMOVE, ADDALLLOCAL, ADDALLREMOTE, REMOVEALL
    }

    private GMSCacheable key;
    private Object value;
    private String operation;
    private ConcurrentHashMap<GMSCacheable, Object> cache;
    private boolean isCoordinator = false;

    /**
     * This constructor expects a GMSCacheable object representing the composite key comprising component, member id, and
     * the state specific key, followed by the value. The value object should strictly be only a byte[] or a Serializable
     * Object.
     *
     * @param key the key
     * @param value the object value
     * @param operation the type of operation
     */
    public DSCMessage(final GMSCacheable key, final Object value, final String operation) {
        this.key = key;
        if (value instanceof Serializable) {
            this.value = value;
        } else {
            this.value = null;
        }
        this.operation = operation;
    }

    public DSCMessage(final ConcurrentHashMap<GMSCacheable, Object> cache, final String operation, final boolean isCoordinator) {
        this.cache = cache;
        this.operation = operation;
        this.isCoordinator = isCoordinator;
    }

    public GMSCacheable getKey() {
        return key;
    }

    public Object getValue() {
        return value;
    }

    public String getOperation() {
        return operation;
    }

    public ConcurrentHashMap<GMSCacheable, Object> getCache() {
        return cache;
    }

    public boolean isCoordinator() {
        return isCoordinator;
    }
}
