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

package org.shoal.ha.cache.impl.util;

/**
 * @author V2.x
 *
 * Handy class full of static functions.
 */
public final class Utility {

    /**
     * Unmarshal a byte array to an integer. Assume the bytes are in BIGENDIAN order. i.e. array[offset] is the
     * most-significant-byte and array[offset+3] is the least-significant-byte.
     *
     * @param array The array of bytes.
     * @param offset The offset from which to start unmarshalling.
     */
    public static int bytesToInt(byte[] array, int offset) {
        int b1, b2, b3, b4;

        return (array[offset] << 24) & 0xFF000000 | (array[offset + 1] << 16) & 0x00FF0000 | (array[offset + 2] << 8) & 0x0000FF00
                | (array[offset + 3] << 0) & 0x000000FF;
    }

    /**
     * Marshal an integer to a byte array. The bytes are in BIGENDIAN order. i.e. array[offset] is the most-significant-byte
     * and array[offset+3] is the least-significant-byte.
     */
    public static byte[] intToBytes(int value) {
        byte[] data = new byte[4];
        intToBytes(value, data, 0);

        return data;
    }

    public static void intToBytes(int value, byte[] array, int offset) {
        array[offset] = (byte) ((value >>> 24) & 0xFF);
        array[offset + 1] = (byte) ((value >>> 16) & 0xFF);
        array[offset + 2] = (byte) ((value >>> 8) & 0xFF);
        array[offset + 3] = (byte) ((value >>> 0) & 0xFF);
    }

    /**
     * Unmarshal a byte array to an long. Assume the bytes are in BIGENDIAN order. i.e. array[offset] is the
     * most-significant-byte and array[offset+7] is the least-significant-byte.
     */
    public static byte[] longToBytes(long value) {
        byte[] data = new byte[8];
        longToBytes(value, data, 0);

        return data;
    }

    public static long bytesToLong(byte[] array, int offset) {
        long l1, l2;

        return ((long) bytesToInt(array, offset) << 32) | (bytesToInt(array, offset + 4) & 0xFFFFFFFFL);

    }

    /**
     * Marshal an long to a byte array. The bytes are in BIGENDIAN order. i.e. array[offset] is the most-significant-byte
     * and array[offset+7] is the least-significant-byte.
     *
     * @param array The array of bytes.
     * @param offset The offset from which to start marshalling.
     */
    public static void longToBytes(long value, byte[] array, int offset) {
        array[offset] = (byte) ((value >>> 56) & 0xFF);
        array[offset + 1] = (byte) ((value >>> 48) & 0xFF);
        array[offset + 2] = (byte) ((value >>> 40) & 0xFF);
        array[offset + 3] = (byte) ((value >>> 32) & 0xFF);
        array[offset + 4] = (byte) ((value >>> 24) & 0xFF);
        array[offset + 5] = (byte) ((value >>> 16) & 0xFF);
        array[offset + 6] = (byte) ((value >>> 8) & 0xFF);
        array[offset + 7] = (byte) ((value >>> 0) & 0xFF);
    }

}
