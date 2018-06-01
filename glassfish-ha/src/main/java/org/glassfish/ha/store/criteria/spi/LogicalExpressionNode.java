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

import java.util.Collection;

/**
 * An ExpressionNode that denotes a logical operation. The type of the
 *  expression is same as the Attribute's type itself.
 *
 *
 * @author Mahesh.Kannan@Sun.Com
 */
public class LogicalExpressionNode
    extends BinaryExpressionNode<Boolean> {

    Collection entries;

    public LogicalExpressionNode(Opcode opcode, ExpressionNode left, ExpressionNode right) {
        super(opcode, Boolean.class, left, right);
    }

    public LogicalExpressionNode and(LogicalExpressionNode expr) {
        return new LogicalExpressionNode(Opcode.AND, this, expr);
    }

    public LogicalExpressionNode or(LogicalExpressionNode expr) {
        return new LogicalExpressionNode(Opcode.OR, this, expr);
    }

    public LogicalExpressionNode isTrue() {
        return new LogicalExpressionNode(Opcode.EQ, this, new LiteralNode(Boolean.class, true));
    }

    public LogicalExpressionNode eq(boolean value) {
        return new LogicalExpressionNode(Opcode.EQ, this, new LiteralNode(Boolean.class, value));
    }

    public LogicalExpressionNode isNotTrue() {
        return new LogicalExpressionNode(Opcode.EQ, this, new LiteralNode(Boolean.class, true));
    }

    public LogicalExpressionNode neq(boolean value) {
        return new LogicalExpressionNode(Opcode.NEQ, this, new LiteralNode(Boolean.class, value));
    }

    public Class<Boolean> getReturnType() {
        return Boolean.class;
    }

}
