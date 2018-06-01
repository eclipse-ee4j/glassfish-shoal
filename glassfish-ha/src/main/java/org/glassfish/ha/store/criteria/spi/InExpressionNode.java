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
 * A node that represnt the "in" operation
 *
 * @param <T>  The type of operands involved
 *
 * @author Mahesh.Kannan@Sun.Com
 */
public class InExpressionNode<T>
    extends LogicalExpressionNode {

    Collection<? extends T> entries;

    public InExpressionNode(ExpressionNode<T> value, Collection<? extends T> entries) {
        super(Opcode.IN, value, null);
        this.entries = entries;
    }

    public Collection<? extends T> getEntries() {
        return entries;
    }

}
