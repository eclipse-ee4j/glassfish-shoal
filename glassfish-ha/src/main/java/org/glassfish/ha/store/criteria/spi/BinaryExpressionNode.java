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

/**
 * A class that represents a binary operation.
 *
 * @param <T> The type of the expression
 *
 * @author Mahesh.Kannan@Sun.Com
 */
public class BinaryExpressionNode<T>
    extends ExpressionNode<T> {

    private ExpressionNode<T> left;

    private ExpressionNode<T> right;

    public BinaryExpressionNode(Opcode opcode, Class<T> returnType, ExpressionNode<T> left) {
        this(opcode, returnType, left, null);
    }

    public BinaryExpressionNode(Opcode opcode, Class<T> returnType,
                                ExpressionNode<T> left, ExpressionNode<T> right) {
        super(opcode, returnType);
        this.left= left;
        this.right = right;
    }

    public ExpressionNode<T> getLeft() {
        return left;
    }

    public ExpressionNode<T> getRight() {
        return right;
    }

}
