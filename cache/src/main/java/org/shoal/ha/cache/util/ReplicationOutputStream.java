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

package org.shoal.ha.cache.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;

/**
 * @author Mahesh Kannan
 */
public class ReplicationOutputStream extends ByteArrayOutputStream {

    public ReplicationOutputStream() {
        super();
    }

    private int maxCount;

    public int mark() {
        return size();
    }

    public void reWrite(int mark, byte[] data) {
        System.arraycopy(data, 0, buf, mark, data.length);
    }

    public void writeInt(int value) throws IOException {
        write(Utility.intToBytes(value));
    }

    public void writeLong(long value) throws IOException {
        write(Utility.longToBytes(value));
    }

    public void writeLengthPrefixedString(String str) throws IOException {
        if (str == null) {
            writeInt(0);
        } else {
            byte[] data = str.getBytes(Charset.defaultCharset());
            writeInt(data.length);
            write(data);
        }
    }

    public void writeLengthPrefixedBytes(byte[] data) throws IOException {
        if (data == null) {
            writeInt(0);
        } else {
            writeInt(data.length);
            write(data);
        }
    }

    public void writeBoolean(boolean b) throws IOException {
        write(b ? 1 : 0); // Writes one byte
    }

    public int moveTo(int pos) {
        if (count > maxCount) {
            maxCount = count;
        }

        int oldPos = count;
        if (pos >= 0 && pos <= count) {
            count = pos;
        }

        return oldPos;
    }

    /**
     * Note: This must be used only after a call to moveTo
     */
    public void backToAppendMode() {
        if (count < maxCount) {
            count = maxCount;
        }
    }

    public byte[] toByteArray() {
        backToAppendMode();
        return super.toByteArray();
    }

}
