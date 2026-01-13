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

package org.glassfish.shoal.ha.cache.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.charset.Charset;

/**
 * @author Mahesh Kannan
 */
public class SimpleSerializer {

    public static void serializeString(ReplicationOutputStream ros, String str) throws IOException {
        int len = str == null ? 0 : str.length();
        ros.write(Utility.intToBytes(len));
        if (len > 0) {
            ros.write(str.getBytes(Charset.defaultCharset()));
        }
    }

    public static void serialize(ReplicationOutputStream ros, Object obj) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        try {
            oos.writeObject(obj);
            oos.flush();
            byte[] data = bos.toByteArray();
            ros.write(Utility.intToBytes(data.length));
            ros.write(data);
        } finally {
            try {
                oos.close();
            } catch (IOException ioEx) {
            }
            try {
                bos.close();
            } catch (IOException ioEx) {
            }
        }
    }

    public static String deserializeString(byte[] data, int offset) {
        int len = Utility.bytesToInt(data, offset);
        return new String(data, offset + 4, len, Charset.defaultCharset());
    }

    public static Object deserialize(ClassLoader loader, byte[] data, int offset) throws ClassNotFoundException, IOException {
        int len = Utility.bytesToInt(data, offset);
        ByteArrayInputStream bis = new ByteArrayInputStream(data, offset + 4, len);
        ObjectInputStreamWithLoader ois = new ObjectInputStreamWithLoader(bis, loader);
        try {
            return ois.readObject();
        } finally {
            try {
                ois.close();
            } catch (IOException ioEx) {
            }
            try {
                bis.close();
            } catch (IOException ioEx) {
            }
        }
    }

}
