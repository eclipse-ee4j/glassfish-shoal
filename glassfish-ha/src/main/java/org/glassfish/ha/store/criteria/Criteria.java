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

/**
 * A class that represents a Criteria. Currently only an Expression<Boolean>
 *  can be specified using a Criteria. In future this class may be modified
 *  to support selection of Attributes from V
 *
 * @param <V> The type of
 *
 * @author Mahesh Kannan
 *
 */
public final class Criteria<V> {

    private Class<V> entryClazz;

    private Expression<Boolean> expression;


    Criteria(Class<V> entryClazz) {
        this.entryClazz = entryClazz;
    }

    public Expression<Boolean> getExpression() {
        return expression;
    }

    public void setExpression(Expression<Boolean> expression) {
        this.expression = expression;
    }

}
