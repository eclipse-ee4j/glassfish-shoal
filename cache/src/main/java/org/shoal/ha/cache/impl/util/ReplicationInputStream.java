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

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;

/**
 * @author Mahesh Kannan
 */
public class ReplicationInputStream extends ByteArrayInputStream {

	private int minPos = 0;

	private int maxPos = -1;

	public ReplicationInputStream(byte[] data) {
		super(data);
		maxPos = data.length - 1;
	}

	public ReplicationInputStream(byte[] data, int offset, int len) {
		super(data, offset, len);
		minPos = offset;
		maxPos = offset + len - 1;
	}

	public int mark() {
		super.mark(0);
		return super.pos;
	}

	public void skipTo(int index) {
		if (index < minPos || index > maxPos) {
			throw new IllegalArgumentException("Illegal position (" + index + "). Valid values are from " + minPos + " to " + maxPos);
		}
		super.pos = index;
	}

	public final int readInt() {
		// TODO Check bounds
		int val = Utility.bytesToInt(buf, pos);
		pos += 4;
		return val;
	}

	public final long readLong() {
		// TODO Check bounds
		return ((long) readInt() << 32) | ((long) readInt() & 0xFFFFFFFFL);
	}

	public final String readLengthPrefixedString() {
		String str = null;
		int len = readInt();
		if (len > 0) {
			str = new String(buf, pos, len, Charset.defaultCharset());
			pos += len;
		}

		return str;
	}

	public final byte[] readLengthPrefixedBytes() {
		byte[] data = null;
		int len = readInt();
		if (len > 0) {
			data = new byte[len];
			System.arraycopy(buf, pos, data, 0, len);
			pos += len;
		}

		return data;
	}

	public boolean readBoolean() {
		return buf[pos++] == 1;
	}

	public byte[] getBuffer() {
		return buf;
	}

}
