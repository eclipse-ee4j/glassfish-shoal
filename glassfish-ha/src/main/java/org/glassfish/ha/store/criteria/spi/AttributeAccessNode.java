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

package org.glassfish.ha.store.criteria.spi;

import org.glassfish.ha.store.spi.AttributeMetadata;

import java.util.Collection;

/**
 * An ExpressionNode that denotes an Attribute access. The type of the
 *  expression is same as the Attribute's type itself.
 *
 * @param <V> The enclosing StoreEntry type
 * @param <T> The Attribute's type
 *
 * @author Mahesh.Kannan@Sun.Com
 */
public final class AttributeAccessNode<V, T>
    extends ExpressionNode<T> {

    private AttributeMetadata<V, T> attr;

    public AttributeAccessNode(AttributeMetadata<V, T> attr) {
        super(Opcode.ATTR, attr.getAttributeType());
        this.attr = attr;
    }

    /**
     * Return the SessionAttributeMetadata associated with this Attribute
     *
     * @return The SessionAttributeMetadata of this Attribute
     */
    public AttributeMetadata<V, T> getAttributeMetadata() {
        return attr;
    }

    /**
     * Checks if the value of the Attribute is in the Collection.
     *
     * @param entries The Collection of data to examine
     * @return true if this attribute exists in the Collection, false if not
     */
    public LogicalExpressionNode in(Collection<? extends T> entries) {
        return new InExpressionNode(this, entries);
    }
    
}
