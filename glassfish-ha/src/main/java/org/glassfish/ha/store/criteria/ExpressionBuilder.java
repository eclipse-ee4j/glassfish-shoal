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

package org.glassfish.ha.store.criteria;

import org.glassfish.ha.store.criteria.spi.*;
import org.glassfish.ha.store.spi.AttributeMetadata;

/**
 * A Class to construct portable Criteria objects
 *
 * @author Mahesh.Kannan@Sun.Com
 *
 */
public class ExpressionBuilder<V> {

    Class<V> entryClazz;

    public ExpressionBuilder(Class<V> entryClazz) {
        this.entryClazz = entryClazz;
    }

    public Criteria<V> setCriteria(Expression<Boolean> expr) {
        Criteria<V> c = new Criteria<V>(entryClazz);
        c.setExpression(expr);

        return c;
    }

    public <T> AttributeAccessNode<V, T> attr(AttributeMetadata<V, T> meta) {
        return new AttributeAccessNode<V, T>(meta);
    }

    public <T> LiteralNode<T> literal(Class<T> type, T value) {
        return new LiteralNode<T>(type, value);
    }

    public <T> LogicalExpressionNode eq(T value, AttributeMetadata<V, T> meta) {
        return new LogicalExpressionNode(Opcode.EQ,
                new LiteralNode<T>(meta.getAttributeType(), value),
                new AttributeAccessNode<V, T>(meta));
    }

    public <T> LogicalExpressionNode eq(AttributeMetadata<V, T> meta, T value) {
        return new LogicalExpressionNode(Opcode.EQ,
                new AttributeAccessNode<V, T>(meta),
                new LiteralNode<T>(meta.getAttributeType(), value));
    }

    public <T> LogicalExpressionNode eq(AttributeMetadata<V, T> meta1,
                                           AttributeMetadata<V, T> meta2) {
        return new LogicalExpressionNode(Opcode.EQ,
                new AttributeAccessNode<V, T>(meta1),
                new AttributeAccessNode<V, T>(meta2));
    }

    public <T> LogicalExpressionNode eq(ExpressionNode<T> expr1, ExpressionNode<T> expr2) {
        return new LogicalExpressionNode(Opcode.EQ, expr1, expr2);
    }

    public <T extends Number> LogicalExpressionNode eq(LiteralNode<T> value, AttributeMetadata<V, T> meta) {
        return new LogicalExpressionNode(Opcode.EQ,
                value, new AttributeAccessNode<V, T>(meta));
    }

    public <T extends Number> LogicalExpressionNode eq(AttributeMetadata<V, T> meta, LiteralNode<T> value) {
        return new LogicalExpressionNode(Opcode.EQ,
                new AttributeAccessNode<V, T>(meta), value);
    }

}
