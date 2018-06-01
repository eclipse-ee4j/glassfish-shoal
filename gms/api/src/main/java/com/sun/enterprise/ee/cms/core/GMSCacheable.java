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

package com.sun.enterprise.ee.cms.core;

import java.io.Serializable;
import java.util.Comparator;

/**
 * Support class for DistributedStateCacheImpl. This class provides an
 * encapsulation for the details that represent a composite key for the
 * cache.
 *
 * @author Shreedhar Ganapathy
 *         Date: May 9, 2005
 * @version $Revision$
 */
public class GMSCacheable implements Serializable, Comparator {
    static final long serialVersionUID = 8510812525534342911L;

    private final String componentName;
    private final String memberTokenId;
    private final Object key;
    private int hashCode = 0;

    public GMSCacheable(final String componentName, final String memberTokenId, final Serializable key) {
        this(componentName, memberTokenId, (Object)key);
    }

    public GMSCacheable(final String componentName, final String memberTokenId, final Object key) {
        if (componentName == null) {
            throw new IllegalArgumentException("GMSCacheable componentName must be non-null");
        }
        if (memberTokenId == null) {
            throw new IllegalArgumentException("GMSCacheable memberTokenId must be non-null");
        }
        if (key == null) {
            throw new IllegalArgumentException("GMSCacheable key must be non-null");
        }
        this.componentName = componentName;
        this.memberTokenId = memberTokenId;
        this.key = key;
    }

    public int compare(final Object o, final Object o1) {
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    public int hashCode() {
        if (hashCode == 0) {
            hashCode = key.hashCode() * 17 + componentName.hashCode() + memberTokenId.hashCode();
        }
        return hashCode;
    }

    /**
     * {@inheritDoc}
     * <p/>
     * We compare the contents of the GMSCacheable argument passed in with
     * the contents of this instance and determine if they are the same.
     *
     */
    @Override
    public boolean equals(final Object o) {
        boolean retval = false;
        boolean componentNameEqual = false;
        boolean memberTokenIdEqual = false;
        boolean keyEqual = false;

        if (o instanceof GMSCacheable) {
            if (this.componentName == null) {
                if (((GMSCacheable) o).componentName == null) {
                    componentNameEqual = true;
                }
            } else if (this.componentName.equals(((GMSCacheable) o).componentName)) {
                componentNameEqual = true;
            }
            if (this.memberTokenId == null) {
                if (((GMSCacheable) o).memberTokenId == null) {
                    memberTokenIdEqual = true;
                }
            } else if (this.memberTokenId.equals(((GMSCacheable) o).memberTokenId)) {
                memberTokenIdEqual = true;
            }
            if (this.key.equals(((GMSCacheable) o).key)) {
                keyEqual = true;
            }

            if (componentNameEqual && memberTokenIdEqual && keyEqual) {
                retval = true;
            }
        }
        return retval;
    }

    public String getComponentName() {
        return componentName;
    }

    public String getMemberTokenId() {
        return memberTokenId;
    }

    public Object getKey() {
        return key;
    }

    public String toString() {
        return "GMSMember:" + memberTokenId + ":Component:" + componentName + ":key:" + key;
    }
}
