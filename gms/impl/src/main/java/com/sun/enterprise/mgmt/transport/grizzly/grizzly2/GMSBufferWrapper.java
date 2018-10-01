/*
 * Copyright (c) 2011, 2018 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.enterprise.mgmt.transport.grizzly.grizzly2;

import java.nio.charset.Charset;

import com.sun.enterprise.mgmt.transport.buffers.Buffer;

/**
 * Shoal GMS {@link Buffer} wrapper over Grizzly 2.0 {@link org.glassfish.grizzly.Buffer}.
 *
 * @author Alexey Stashok
 */
final class GMSBufferWrapper implements Buffer {

	private org.glassfish.grizzly.Buffer grizzlyBuffer;

	void recycle() {
		grizzlyBuffer = null;
	}

	GMSBufferWrapper wrap(org.glassfish.grizzly.Buffer grizzlyBuffer) {
		this.grizzlyBuffer = grizzlyBuffer;
		return this;
	}

	@Override
	public void shrink() {
		grizzlyBuffer.shrink();
	}

	@Override
	public void dispose() {
		grizzlyBuffer.tryDispose();
	}

	@Override
	public Object underlying() {
		return grizzlyBuffer;
	}

	@Override
	public int capacity() {
		return grizzlyBuffer.capacity();
	}

	@Override
	public int position() {
		return grizzlyBuffer.position();
	}

	@Override
	public Buffer position(final int newPosition) {
		grizzlyBuffer.position(newPosition);
		return this;
	}

	@Override
	public int limit() {
		return grizzlyBuffer.limit();
	}

	@Override
	public Buffer limit(final int newLimit) {
		grizzlyBuffer.limit(newLimit);
		return this;
	}

	@Override
	public Buffer mark() {
		grizzlyBuffer.mark();
		return this;
	}

	@Override
	public Buffer reset() {
		grizzlyBuffer.reset();
		return this;
	}

	@Override
	public Buffer clear() {
		grizzlyBuffer.clear();
		return this;
	}

	@Override
	public Buffer flip() {
		grizzlyBuffer.flip();
		return this;
	}

	@Override
	public Buffer rewind() {
		grizzlyBuffer.rewind();
		return this;
	}

	@Override
	public int remaining() {
		return grizzlyBuffer.remaining();
	}

	@Override
	public boolean hasRemaining() {
		return grizzlyBuffer.hasRemaining();
	}

	@Override
	public byte get() {
		return grizzlyBuffer.get();
	}

	@Override
	public Buffer put(final byte b) {
		grizzlyBuffer.put(b);
		return this;
	}

	@Override
	public byte get(final int index) {
		return grizzlyBuffer.get(index);
	}

	@Override
	public Buffer put(final int index, final byte b) {
		grizzlyBuffer.put(index, b);
		return this;
	}

	@Override
	public Buffer get(final byte[] dst) {
		grizzlyBuffer.get(dst);
		return this;
	}

	@Override
	public Buffer get(final byte[] dst, final int offset, final int length) {
		grizzlyBuffer.get(dst, offset, length);
		return this;
	}

	@Override
	public Buffer put(final byte[] src) {
		grizzlyBuffer.put(src);
		return this;
	}

	@Override
	public Buffer put(final byte[] src, final int offset, final int length) {
		grizzlyBuffer.put(src, offset, length);
		return this;
	}

	@Override
	public char getChar() {
		return grizzlyBuffer.getChar();
	}

	@Override
	public Buffer putChar(final char value) {
		grizzlyBuffer.putChar(value);
		return this;
	}

	@Override
	public char getChar(final int index) {
		return grizzlyBuffer.getChar(index);
	}

	@Override
	public Buffer putChar(final int index, final char value) {
		grizzlyBuffer.putChar(index, value);
		return this;
	}

	@Override
	public short getShort() {
		return grizzlyBuffer.getShort();
	}

	@Override
	public Buffer putShort(final short value) {
		grizzlyBuffer.putShort(value);
		return this;
	}

	@Override
	public short getShort(final int index) {
		return grizzlyBuffer.getShort(index);
	}

	@Override
	public Buffer putShort(final int index, final short value) {
		grizzlyBuffer.putShort(index, value);
		return this;
	}

	@Override
	public int getInt() {
		return grizzlyBuffer.getInt();
	}

	@Override
	public Buffer putInt(final int value) {
		grizzlyBuffer.putInt(value);
		return this;
	}

	@Override
	public int getInt(final int index) {
		return grizzlyBuffer.getInt(index);
	}

	@Override
	public Buffer putInt(final int index, final int value) {
		grizzlyBuffer.putInt(index, value);
		return this;
	}

	@Override
	public long getLong() {
		return grizzlyBuffer.getLong();
	}

	@Override
	public Buffer putLong(final long value) {
		grizzlyBuffer.putLong(value);
		return this;
	}

	@Override
	public long getLong(final int index) {
		return grizzlyBuffer.getLong(index);
	}

	@Override
	public Buffer putLong(final int index, final long value) {
		grizzlyBuffer.putLong(index, value);
		return this;
	}

	@Override
	public float getFloat() {
		return grizzlyBuffer.getFloat();
	}

	@Override
	public Buffer putFloat(final float value) {
		grizzlyBuffer.putFloat(value);
		return this;
	}

	@Override
	public float getFloat(final int index) {
		return grizzlyBuffer.getFloat(index);
	}

	@Override
	public Buffer putFloat(final int index, final float value) {
		grizzlyBuffer.putFloat(index, value);
		return this;
	}

	@Override
	public double getDouble() {
		return grizzlyBuffer.getDouble();
	}

	@Override
	public Buffer putDouble(final double value) {
		grizzlyBuffer.putDouble(value);
		return this;
	}

	@Override
	public double getDouble(final int index) {
		return grizzlyBuffer.getDouble(index);
	}

	@Override
	public Buffer putDouble(final int index, final double value) {
		grizzlyBuffer.putDouble(index, value);
		return this;
	}

	@Override
	public String toStringContent() {
		return grizzlyBuffer.toStringContent();
	}

	@Override
	public String toStringContent(final Charset charset) {
		return grizzlyBuffer.toStringContent(charset);
	}

	@Override
	public String toStringContent(final Charset charset, final int position, final int limit) {
		return grizzlyBuffer.toStringContent(charset, position, limit);
	}

	@Override
	public int compareTo(final Buffer that) {
		int n = this.position() + Math.min(this.remaining(), that.remaining());
		for (int i = this.position(), j = that.position(); i < n; i++, j++) {
			byte v1 = this.get(i);
			byte v2 = that.get(j);
			if (v1 == v2) {
				continue;
			}
			if (v1 < v2) {
				return -1;
			}
			return +1;
		}
		return this.remaining() - that.remaining();
	}

	@Override
	public Buffer duplicate() {
		final GMSBufferWrapper wrapper = new GMSBufferWrapper();
		if (grizzlyBuffer != null) {
			wrapper.wrap(grizzlyBuffer.duplicate());
		}

		return wrapper;
	}

}
