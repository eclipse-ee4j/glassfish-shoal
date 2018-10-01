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

package com.sun.enterprise.mgmt.transport.buffers;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.BufferOverflowException;
import java.nio.ReadOnlyBufferException;

/**
 * Expandable Buffer writer, which adopts its size during while getting more data.
 *
 * @author Alexey Stashok
 */
public abstract class ExpandableBufferWriter {
	private final OutputStream outputStream = new BufferOutputStream();

	public abstract Buffer getBuffer();

	public abstract Buffer toBuffer();

	public abstract int position();

	public abstract void position(int pos);

	protected abstract void ensureCapacity(final int delta);

	public void reserve(final int size) {
		ensureCapacity(size);
		position(position() + size);
	}

	/**
	 * Relative <i>put</i> method&nbsp;&nbsp;<i>(optional operation)</i>.
	 *
	 * <p>
	 * Writes the given byte into this buffer at the current position, and then increments the position.
	 * </p>
	 *
	 * @param b The byte to be written
	 *
	 * @return This buffer writer
	 *
	 * @throws java.nio.BufferOverflowException If this buffer's current position is not smaller than its limit
	 *
	 * @throws java.nio.ReadOnlyBufferException If this buffer is read-only
	 */
	public ExpandableBufferWriter put(final byte b) {
		ensureCapacity(1);
		getBuffer().put(b);
		return this;
	}

	/**
	 * Absolute <i>put</i> method&nbsp;&nbsp;<i>(optional operation)</i>.
	 *
	 * <p>
	 * Writes the given byte into this buffer at the given index.
	 * </p>
	 *
	 * @param index The index at which the byte will be written
	 *
	 * @param b The byte value to be written
	 *
	 * @return This buffer writer
	 *
	 * @throws IndexOutOfBoundsException If <tt>index</tt> is negative or not smaller than the buffer's limit
	 *
	 * @throws java.nio.ReadOnlyBufferException If this buffer is read-only
	 */
	public ExpandableBufferWriter put(final int index, final byte b) {
		ensureCapacity(index - position() + 1);
		getBuffer().put(index, b);
		return this;
	}

	/**
	 * Relative bulk <i>put</i> method&nbsp;&nbsp;<i>(optional operation)</i>.
	 *
	 * <p>
	 * This method transfers the entire content of the given source byte array into this buffer. An invocation of this
	 * method of the form <tt>dst.put(a)</tt> behaves in exactly the same way as the invocation
	 *
	 * <pre>
	 * dst.put(a, 0, a.length)
	 * </pre>
	 *
	 * @param src the source byte array
	 *
	 * @return This buffer writer
	 *
	 * @throws java.nio.BufferOverflowException If there is insufficient space in this buffer
	 *
	 * @throws java.nio.ReadOnlyBufferException If this buffer is read-only
	 */
	public ExpandableBufferWriter put(final byte[] src) {
		return put(src, 0, src.length);
	}

	/**
	 * Relative bulk <i>put</i> method&nbsp;&nbsp;<i>(optional operation)</i>.
	 *
	 * <p>
	 * This method transfers bytes into this buffer from the given source array. If there are more bytes to be copied from
	 * the array than remain in this buffer, that is, if <tt>length</tt>&nbsp;<tt>&gt;</tt>&nbsp;<tt>remaining()</tt>, then
	 * no bytes are transferred and a {@link java.nio.BufferOverflowException} is thrown.
	 *
	 * <p>
	 * Otherwise, this method copies <tt>length</tt> bytes from the given array into this buffer, starting at the given
	 * offset in the array and at the current position of this buffer. The position of this buffer is then incremented by
	 * <tt>length</tt>.
	 *
	 * <p>
	 * In other words, an invocation of this method of the form <tt>dst.put(src,&nbsp;off,&nbsp;len)</tt> has exactly the
	 * same effect as the loop
	 *
	 * <pre>
	 * for (int i = off; i &lt; off + len; i++)
	 *     dst.put(a[i]);
	 * </pre>
	 *
	 * except that it first checks that there is sufficient space in this buffer and it is potentially much more efficient.
	 * <p>
	 *
	 * @param src The array from which bytes are to be read
	 *
	 * @param offset The offset within the array of the first byte to be read; must be non-negative and no larger than
	 * <tt>array.length</tt>
	 *
	 * @param length The number of bytes to be read from the given array; must be non-negative and no larger than
	 * <tt>array.length - offset</tt>
	 *
	 * @return This buffer writer
	 *
	 * @throws java.nio.BufferOverflowException If there is insufficient space in this buffer
	 *
	 * @throws IndexOutOfBoundsException If the preconditions on the <tt>offset</tt> and <tt>length</tt> parameters do not
	 * hold
	 *
	 * @throws java.nio.ReadOnlyBufferException If this buffer is read-only
	 */
	public ExpandableBufferWriter put(final byte[] src, final int offset, final int length) {

		ensureCapacity(length);
		getBuffer().put(src, offset, length);
		return this;
	}

	/**
	 * Relative <i>put</i> method for writing a char value&nbsp;&nbsp;<i>(optional operation)</i>.
	 *
	 * <p>
	 * Writes two bytes containing the given char value, in the current byte order, into this buffer at the current
	 * position, and then increments the position by two.
	 * </p>
	 *
	 * @param value The char value to be written
	 *
	 * @return This buffer writer
	 *
	 * @throws java.nio.BufferOverflowException If there are fewer than two bytes remaining in this buffer
	 *
	 * @throws java.nio.ReadOnlyBufferException If this buffer is read-only
	 */
	public ExpandableBufferWriter putChar(final char value) {
		ensureCapacity(2);
		getBuffer().putChar(value);

		return this;
	}

	/**
	 * Absolute <i>put</i> method for writing a char value&nbsp;&nbsp;<i>(optional operation)</i>.
	 *
	 * <p>
	 * Writes two bytes containing the given char value, in the current byte order, into this buffer at the given index.
	 * </p>
	 *
	 * @param index The index at which the bytes will be written
	 *
	 * @param value The char value to be written
	 *
	 * @return This buffer writer
	 *
	 * @throws IndexOutOfBoundsException If <tt>index</tt> is negative or not smaller than the buffer's limit, minus one
	 *
	 * @throws java.nio.ReadOnlyBufferException If this buffer is read-only
	 */
	public ExpandableBufferWriter putChar(final int index, final char value) {
		ensureCapacity(index - position() + 2);
		getBuffer().putChar(index, value);
		return this;
	}

	/**
	 * Relative <i>put</i> method for writing a short value&nbsp;&nbsp;<i>(optional operation)</i>.
	 *
	 * <p>
	 * Writes two bytes containing the given short value, in the current byte order, into this buffer at the current
	 * position, and then increments the position by two.
	 * </p>
	 *
	 * @param value The short value to be written
	 *
	 * @return This buffer writer
	 *
	 * @throws java.nio.BufferOverflowException If there are fewer than two bytes remaining in this buffer
	 *
	 * @throws java.nio.ReadOnlyBufferException If this buffer is read-only
	 */
	public ExpandableBufferWriter putShort(final short value) {
		ensureCapacity(2);
		getBuffer().putShort(value);

		return this;
	}

	/**
	 * Absolute <i>put</i> method for writing a short value&nbsp;&nbsp;<i>(optional operation)</i>.
	 *
	 * <p>
	 * Writes two bytes containing the given short value, in the current byte order, into this buffer at the given index.
	 * </p>
	 *
	 * @param index The index at which the bytes will be written
	 *
	 * @param value The short value to be written
	 *
	 * @return This buffer writer
	 *
	 * @throws IndexOutOfBoundsException If <tt>index</tt> is negative or not smaller than the buffer's limit, minus one
	 *
	 * @throws java.nio.ReadOnlyBufferException If this buffer is read-only
	 */
	public ExpandableBufferWriter putShort(final int index, final short value) {
		ensureCapacity(index - position() + 2);
		getBuffer().putShort(index, value);
		return this;
	}

	/**
	 * Relative <i>put</i> method for writing an int value&nbsp;&nbsp;<i>(optional operation)</i>.
	 *
	 * <p>
	 * Writes four bytes containing the given int value, in the current byte order, into this buffer at the current
	 * position, and then increments the position by four.
	 * </p>
	 *
	 * @param value The int value to be written
	 *
	 * @return This buffer writer
	 *
	 * @throws BufferOverflowException If there are fewer than four bytes remaining in this buffer
	 *
	 * @throws java.nio.ReadOnlyBufferException If this buffer is read-only
	 */
	public ExpandableBufferWriter putInt(final int value) {
		ensureCapacity(4);
		getBuffer().putInt(value);

		return this;
	}

	/**
	 * Absolute <i>put</i> method for writing an int value&nbsp;&nbsp;<i>(optional operation)</i>.
	 *
	 * <p>
	 * Writes four bytes containing the given int value, in the current byte order, into this buffer at the given index.
	 * </p>
	 *
	 * @param index The index at which the bytes will be written
	 *
	 * @param value The int value to be written
	 *
	 * @return This buffer writer
	 *
	 * @throws IndexOutOfBoundsException If <tt>index</tt> is negative or not smaller than the buffer's limit, minus three
	 *
	 * @throws java.nio.ReadOnlyBufferException If this buffer is read-only
	 */
	public ExpandableBufferWriter putInt(final int index, final int value) {
		ensureCapacity(position() - index + 4);
		getBuffer().putInt(index, value);

		return this;
	}

	/**
	 * Relative <i>put</i> method for writing a long value&nbsp;&nbsp;<i>(optional operation)</i>.
	 *
	 * <p>
	 * Writes eight bytes containing the given long value, in the current byte order, into this buffer at the current
	 * position, and then increments the position by eight.
	 * </p>
	 *
	 * @param value The long value to be written
	 *
	 * @return This buffer writer
	 *
	 * @throws BufferOverflowException If there are fewer than eight bytes remaining in this buffer
	 *
	 * @throws ReadOnlyBufferException If this buffer is read-only
	 */
	public ExpandableBufferWriter putLong(final long value) {
		ensureCapacity(8);
		getBuffer().putLong(value);

		return this;
	}

	/**
	 * Absolute <i>put</i> method for writing a long value&nbsp;&nbsp;<i>(optional operation)</i>.
	 *
	 * <p>
	 * Writes eight bytes containing the given long value, in the current byte order, into this buffer at the given index.
	 * </p>
	 *
	 * @param index The index at which the bytes will be written
	 *
	 * @param value The long value to be written
	 *
	 * @return This buffer writer
	 *
	 * @throws IndexOutOfBoundsException If <tt>index</tt> is negative or not smaller than the buffer's limit, minus seven
	 *
	 * @throws ReadOnlyBufferException If this buffer is read-only
	 */
	public ExpandableBufferWriter putLong(final int index, final long value) {
		ensureCapacity(index - position() + 8);
		getBuffer().putLong(index, value);

		return this;
	}

	/**
	 * Relative <i>put</i> method for writing a float value&nbsp;&nbsp;<i>(optional operation)</i>.
	 *
	 * <p>
	 * Writes four bytes containing the given float value, in the current byte order, into this buffer at the current
	 * position, and then increments the position by four.
	 * </p>
	 *
	 * @param value The float value to be written
	 *
	 * @return This buffer writer
	 *
	 * @throws BufferOverflowException If there are fewer than four bytes remaining in this buffer
	 *
	 * @throws ReadOnlyBufferException If this buffer is read-only
	 */
	public ExpandableBufferWriter putFloat(final float value) {
		ensureCapacity(4);
		getBuffer().putFloat(value);

		return this;
	}

	/**
	 * Absolute <i>put</i> method for writing a float value&nbsp;&nbsp;<i>(optional operation)</i>.
	 *
	 * <p>
	 * Writes four bytes containing the given float value, in the current byte order, into this buffer at the given index.
	 * </p>
	 *
	 * @param index The index at which the bytes will be written
	 *
	 * @param value The float value to be written
	 *
	 * @return This buffer writer
	 *
	 * @throws IndexOutOfBoundsException If <tt>index</tt> is negative or not smaller than the buffer's limit, minus three
	 *
	 * @throws ReadOnlyBufferException If this buffer is read-only
	 */
	public ExpandableBufferWriter putFloat(final int index, final float value) {
		ensureCapacity(index - position() + 4);
		getBuffer().putFloat(index, value);

		return this;
	}

	/**
	 * Relative <i>put</i> method for writing a double value&nbsp;&nbsp;<i>(optional operation)</i>.
	 *
	 * <p>
	 * Writes eight bytes containing the given double value, in the current byte order, into this buffer at the current
	 * position, and then increments the position by eight.
	 * </p>
	 *
	 * @param value The double value to be written
	 *
	 * @return This buffer writer
	 *
	 * @throws BufferOverflowException If there are fewer than eight bytes remaining in this buffer
	 *
	 * @throws ReadOnlyBufferException If this buffer is read-only
	 */
	public ExpandableBufferWriter putDouble(final double value) {
		ensureCapacity(8);
		getBuffer().putDouble(value);

		return this;
	}

	/**
	 * Absolute <i>put</i> method for writing a double value&nbsp;&nbsp;<i>(optional operation)</i>.
	 *
	 * <p>
	 * Writes eight bytes containing the given double value, in the current byte order, into this buffer at the given index.
	 * </p>
	 *
	 * @param index The index at which the bytes will be written
	 *
	 * @param value The double value to be written
	 *
	 * @return This buffer writer
	 *
	 * @throws IndexOutOfBoundsException If <tt>index</tt> is negative or not smaller than the buffer's limit, minus seven
	 *
	 * @throws ReadOnlyBufferException If this buffer is read-only
	 */
	public ExpandableBufferWriter putDouble(final int index, final double value) {
		ensureCapacity(index - position() + 8);
		getBuffer().putDouble(index, value);

		return this;
	}

	public OutputStream asOutputStream() {
		return outputStream;
	}

	private final class BufferOutputStream extends OutputStream {

		@Override
		public void write(final byte[] b, final int off, final int len) throws IOException {
			put(b, off, len);
		}

		@Override
		public void write(final int b) throws IOException {
			put((byte) b);
		}
	}
}
