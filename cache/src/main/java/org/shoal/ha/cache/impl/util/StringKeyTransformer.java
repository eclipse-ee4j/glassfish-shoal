/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package org.shoal.ha.cache.impl.util;

import org.glassfish.ha.store.util.KeyTransformer;

import java.nio.charset.Charset;

/**
 * @author Mahesh Kannan
 *
 */
public class StringKeyTransformer
    implements KeyTransformer<String> {

    public StringKeyTransformer() {}

    @Override
    public byte[] keyToByteArray(String str) {
        //System.out.println("@@@@@@@@@@@@@ StringKeyTransformer.keyTobyteArray(" + str +")");
        return str.getBytes(Charset.defaultCharset());
    }

    @Override
    public String byteArrayToKey(byte[] bytes, int index, int len) {
        //System.out.println("@@@@@@@@@@@@@ StringKeyTransformer.byteArrayToKey(@[b...) => " + new String(bytes, index, len));
        return new String(bytes, index, len, Charset.defaultCharset());
    }
}
