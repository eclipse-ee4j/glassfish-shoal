/*
 * Copyright (c) 2007, 2018 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.enterprise.mgmt.transport.buffers;

import java.io.IOException;
import java.io.InputStream;

/**
 * {@link InputStream} implementation over {@link Buffer}.
 *
 * @author Alexey Stashok
 */
public class BufferInputStream extends InputStream {

	private final Buffer buffer;

	public BufferInputStream(Buffer buffer) {
		this.buffer = buffer;
	}

	@Override
	public int read() throws IOException {
		return buffer.get() & 0xFF;
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		int length = Math.min(len, available());

		buffer.get(b, off, length);

		return length;
	}

	@Override
	public int available() throws IOException {
		return buffer.remaining();
	}

	@Override
	public long skip(long n) throws IOException {
		int skipped = (int) Math.min(n, available());

		buffer.position(buffer.position() + skipped);
		return skipped;
	}
}
