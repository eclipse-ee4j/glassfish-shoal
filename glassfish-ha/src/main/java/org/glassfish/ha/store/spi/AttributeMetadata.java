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

import java.lang.reflect.Method;

/**
 * For each attribute A of type T in a.b.X, a.b.X_ contains a (static) field whose
 *  type is AttributeMetadata<X, T>. AttributeMetadata describes the attribute
 *  by giving its (java) type, name etc.
 * 
 * @param <S> The StoreEntry that this AttributeMetadata belongs to
 * @param <T> The Java type of the Attribute that this Metadata represents
 *
 * @author Mahesh.Kannan@Sun.Com
 * @aauthor Larry.white@Sun.Com
 */
public interface AttributeMetadata<S, T> {

    public String getName();

    /**
     * Get the java type of this attribute
     *
     * @return
     */
    public Class<T> getAttributeType();

    //The getter method to access the value
    public Method getGetterMethod();

    //The setter method to set the value
    public Method getSetterMethod();

    public boolean isVersionAttribute();

    public boolean isHashKeyAttribute();
}
