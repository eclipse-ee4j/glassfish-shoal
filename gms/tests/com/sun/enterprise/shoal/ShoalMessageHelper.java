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

package com.sun.enterprise.shoal;


import java.io.*;
import java.util.zip.GZIPOutputStream;

public class ShoalMessageHelper {

    public static final byte[] serializeObject(Object obj)
            throws java.io.NotSerializableException, java.io.IOException {
        byte[] data = null;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = null;
        try {
            oos = new ObjectOutputStream(bos);
            oos.writeObject(obj);
            oos.flush();
            data = bos.toByteArray();
        } catch (java.io.NotSerializableException notSerEx) {
            throw notSerEx;
        } catch (Exception th) {
            IOException ioEx = new IOException(th.toString());
            ioEx.initCause(th);
            throw ioEx;
        } finally {
            if (oos != null) {
                try {
                    oos.close();
                } catch (Exception ex) {
                }
            }
            try {
                if (bos != null) {
                    bos.close();
                }
            } catch (Exception ex) {
            }
        }

        return data;
    }

    public static final Object deserializeObject(byte[] data)
            throws Exception {
        Object obj = null;
        ByteArrayInputStream bis = null;
        ObjectInputStream ois = null;
        try {
            bis = new ByteArrayInputStream(data);
            ois = new ObjectInputStream(bis);
            obj = ois.readObject();
        } catch (Exception ex) {
            throw ex;
        } finally {
            try {
                if (ois != null) {
                    ois.close();
                }
            } catch (Exception ex) {
            }
            try {
                if (bis != null) {
                    bis.close();
                }
            } catch (Exception ex) {
            }
        }
        return obj;
    }

    /**
     * Create serialized byte[] for <code>obj</code>.
     *
     * @param obj - serialize obj
     * @return byte[] containing serialized data stream for obj
     */
    /*    Keep around for when we may want to try out compressing messages.
    static public byte[] getByteArray(Serializable obj)
            throws IOException {
        return getByteArray(obj, false);
    }

    static public byte[] getByteArray(Serializable obj, boolean compress)
            throws IOException {
        ByteArrayOutputStream bos = null;
        ObjectOutputStream oos = null;
        byte[] obs;
        try {
            bos = new ByteArrayOutputStream();
            if (compress) {
                oos = new ObjectOutputStream(new GZIPOutputStream(bos));
            } else {
                oos = new ObjectOutputStream(bos);
            }
            oos.writeObject(obj);
            oos.flush();
            oos.close();
            oos = null;
            obs = bos.toByteArray();
        } finally {
            if (oos != null) {
                try {
                    oos.close();
                } catch (Exception e) {}
            }
            if (bos != null) {
                try {
                    bos.close();
                } catch (Exception e) {}
            }
        }

        return obs;
    }
    */
}
