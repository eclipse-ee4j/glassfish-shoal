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

package org.glassfish.shoal.gms.mgmt.transport.buffers;

import java.nio.charset.Charset;

/**
 * JDK {@link java.nio.ByteBuffer} was taken as base for Grizzly <tt>Buffer</tt> interface, but <tt>Buffer</tt> has
 * several extensions: it's possible to prepend some data to a Buffer and release Buffer, when it's not required any
 * more.
 *
 * @author Alexey Stashok
 */
public interface Buffer extends Comparable<Buffer> {
    /**
     * Creates a new <code>Buffer</code> that shares this buffer's content.
     *
     * <p>
     * The content of the new buffer will be that of this buffer. Changes to this buffer's content will be visible in the
     * new buffer, and vice versa; the two buffer's position, limit, and mark values will be independent.
     *
     * <p>
     * The new buffer's capacity, limit, position, and mark values will be identical to those of this buffer. The new buffer
     * will be direct if, and only if, this buffer is direct, and it will be read-only if, and only if, this buffer is
     * read-only.
     * </p>
     *
     * @return The new <code>Buffer</code>
     */
    Buffer duplicate();

    /**
     * Disposes the buffer part, outside [position, limit] interval if possible. May return without changing capacity. After
     * shrink is called, position/limit/capacity values may have different values, than before, but still point to the same
     * <tt>Buffer</tt> elements.
     */
    void shrink();

    /**
     * Notify the allocator that the space for this <tt>Buffer</tt> is no longer needed. All calls to methods on a
     * <tt>Buffer</tt> will fail after a call to dispose().
     */
    void dispose();

    /**
     * Return the underlying buffer
     *
     * @return the underlying buffer
     */
    Object underlying();

    /**
     * Returns this buffer's capacity.
     * <p>
     *
     * @return The capacity of this buffer
     */
    int capacity();

    /**
     * Returns this buffer's position.
     * <p>
     *
     * @return The position of this buffer
     */
    int position();

    /**
     * Sets this buffer's position. If the mark is defined and larger than the new position then it is discarded.
     * <p>
     *
     * @param newPosition The new position value; must be non-negative and no larger than the current limit
     *
     * @return This buffer
     *
     * @throws IllegalArgumentException If the preconditions on <tt>newPosition</tt> do not hold
     */
    Buffer position(int newPosition);

    /**
     * Returns this buffer's limit.
     * <p>
     *
     * @return The limit of this buffer
     */
    int limit();

    /**
     * Sets this buffer's limit. If the position is larger than the new limit then it is set to the new limit. If the mark
     * is defined and larger than the new limit then it is discarded.
     * <p>
     *
     * @param newLimit The new limit value; must be non-negative and no larger than this buffer's capacity
     *
     * @return This buffer
     *
     * @throws IllegalArgumentException If the preconditions on <tt>newLimit</tt> do not hold
     */
    Buffer limit(int newLimit);

    /**
     * Sets this buffer's mark at its position.
     * <p>
     *
     * @return This buffer
     */
    Buffer mark();

    /**
     * Resets this buffer's position to the previously-marked position.
     *
     * <p>
     * Invoking this method neither changes nor discards the mark's value.
     * </p>
     *
     * @return This buffer
     *
     * @throws java.nio.InvalidMarkException If the mark has not been set
     */
    Buffer reset();

    /**
     * Clears this buffer. The position is set to zero, the limit is set to the capacity, and the mark is discarded.
     *
     * <p>
     * Invoke this method before using a sequence of channel-read or <i>put</i> operations to fill this buffer. For example:
     *
     * <blockquote>
     *
     * <pre>
     * buf.clear(); // Prepare buffer for reading
     * in.read(buf); // Read data
     * </pre>
     *
     * </blockquote>
     *
     * <p>
     * This method does not actually erase the data in the buffer, but it is named as if it did because it will most often
     * be used in situations in which that might as well be the case.
     * </p>
     *
     * @return This buffer
     */
    Buffer clear();

    /**
     * Flips this buffer. The limit is set to the current position and then the position is set to zero. If the mark is
     * defined then it is discarded.
     *
     * <p>
     * After a sequence of channel-read or <i>put</i> operations, invoke this method to prepare for a sequence of
     * channel-write or relative <i>get</i> operations. For example:
     *
     * <blockquote>
     *
     * <pre>
     * buf.put(magic); // Prepend header
     * in.read(buf); // Read data into rest of buffer
     * buf.flip(); // Flip buffer
     * out.write(buf); // Write header + data to channel
     * </pre>
     *
     * </blockquote>
     *
     * @return This buffer
     */
    Buffer flip();

    /**
     * Rewinds this buffer. The position is set to zero and the mark is discarded.
     *
     * <p>
     * Invoke this method before a sequence of channel-write or <i>get</i> operations, assuming that the limit has already
     * been set appropriately. For example:
     *
     * <blockquote>
     *
     * <pre>
     * out.write(buf); // Write remaining data
     * buf.rewind(); // Rewind buffer
     * buf.get(array); // Copy data into array
     * </pre>
     *
     * </blockquote>
     *
     * @return This buffer
     */
    Buffer rewind();

    /**
     * Returns the number of elements between the current position and the limit.
     * <p>
     *
     * @return The number of elements remaining in this buffer
     */
    int remaining();

    /**
     * Tells whether there are any elements between the current position and the limit.
     * <p>
     *
     * @return <tt>true</tt> if, and only if, there is at least one element remaining in this buffer
     */
    boolean hasRemaining();

    // -- Singleton get/put methods --
    /**
     * Relative <i>get</i> method. Reads the byte at this buffer's current position, and then increments the position.
     * <p>
     *
     * @return The byte at the buffer's current position
     *
     * @throws java.nio.BufferUnderflowException If the buffer's current position is not smaller than its limit
     */
    byte get();

    /**
     * Relative <i>put</i> method&nbsp;&nbsp;<i>(optional operation)</i>.
     *
     * <p>
     * Writes the given byte into this buffer at the current position, and then increments the position.
     * </p>
     *
     * @param b The byte to be written
     *
     * @return This buffer
     *
     * @throws java.nio.BufferOverflowException If this buffer's current position is not smaller than its limit
     *
     * @throws java.nio.ReadOnlyBufferException If this buffer is read-only
     */
    Buffer put(byte b);

    /**
     * Absolute <i>get</i> method. Reads the byte at the given index.
     * <p>
     *
     * @param index The index from which the byte will be read
     *
     * @return The byte at the given index
     *
     * @throws IndexOutOfBoundsException If <tt>index</tt> is negative or not smaller than the buffer's limit
     */
    byte get(int index);

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
     * @return This buffer
     *
     * @throws IndexOutOfBoundsException If <tt>index</tt> is negative or not smaller than the buffer's limit
     *
     * @throws java.nio.ReadOnlyBufferException If this buffer is read-only
     */
    Buffer put(int index, byte b);

    // -- Bulk get operations --
    /**
     * Relative bulk <i>get</i> method.
     *
     * <p>
     * This method transfers bytes from this buffer into the given destination array. If there are fewer bytes remaining in
     * the buffer than are required to satisfy the request, that is, if
     * <tt>length</tt>&nbsp;<tt>&gt;</tt>&nbsp;<tt>remaining()</tt>, then no bytes are transferred and a
     * {@link java.nio.BufferUnderflowException} is thrown.
     *
     * <p>
     * Otherwise, this method copies <tt>length</tt> bytes from this buffer into the given array, starting at the current
     * position of this buffer and at the given offset in the array. The position of this buffer is then incremented by
     * <tt>length</tt>.
     *
     * <p>
     * In other words, an invocation of this method of the form <tt>src.get(dst,&nbsp;off,&nbsp;len)</tt> has exactly the
     * same effect as the loop
     *
     * <pre>
     * <code>
     *     for (int i = off; i &lt; off + len; i++)
     *         dst[i] = src.get(); </code>
     * </pre>
     *
     * except that it first checks that there are sufficient bytes in this buffer and it is potentially much more efficient.
     * <p>
     *
     * @param dst The array into which bytes are to be written
     *
     * @param offset The offset within the array of the first byte to be written; must be non-negative and no larger than
     * <tt>dst.length</tt>
     *
     * @param length The maximum number of bytes to be written to the given array; must be non-negative and no larger than
     * <tt>dst.length - offset</tt>
     *
     * @return This buffer
     *
     * @throws java.nio.BufferUnderflowException If there are fewer than <tt>length</tt> bytes remaining in this buffer
     *
     * @throws IndexOutOfBoundsException If the preconditions on the <tt>offset</tt> and <tt>length</tt> parameters do not
     * hold
     */
    Buffer get(byte[] dst, int offset, int length);

    /**
     * Relative bulk <i>get</i> method.
     *
     * <p>
     * This method transfers bytes from this buffer into the given destination array. An invocation of this method of the
     * form <tt>src.get(a)</tt> behaves in exactly the same way as the invocation
     *
     * <pre>
     * src.get(a, 0, a.length)
     * </pre>
     *
     * @param dst the destination byte array
     * @return This buffer
     *
     * @throws java.nio.BufferUnderflowException If there are fewer than <tt>length</tt> bytes remaining in this buffer
     */
    Buffer get(byte[] dst);

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
     * @return This buffer
     *
     * @throws java.nio.BufferOverflowException If there is insufficient space in this buffer
     *
     * @throws IndexOutOfBoundsException If the preconditions on the <tt>offset</tt> and <tt>length</tt> parameters do not
     * hold
     *
     * @throws java.nio.ReadOnlyBufferException If this buffer is read-only
     */
    Buffer put(byte[] src, int offset, int length);

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
     * @return This buffer
     */
    Buffer put(byte[] src);

    /**
     * Relative <i>get</i> method for reading a char value.
     *
     * <p>
     * Reads the next two bytes at this buffer's current position, composing them into a char value according to the current
     * byte order, and then increments the position by two.
     * </p>
     *
     * @return The char value at the buffer's current position
     *
     * @throws java.nio.BufferUnderflowException If there are fewer than two bytes remaining in this buffer
     */
    char getChar();

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
     * @return This buffer
     *
     * @throws java.nio.BufferOverflowException If there are fewer than two bytes remaining in this buffer
     *
     * @throws java.nio.ReadOnlyBufferException If this buffer is read-only
     */
    Buffer putChar(char value);

    /**
     * Absolute <i>get</i> method for reading a char value.
     *
     * <p>
     * Reads two bytes at the given index, composing them into a char value according to the current byte order.
     * </p>
     *
     * @param index The index from which the bytes will be read
     *
     * @return The char value at the given index
     *
     * @throws IndexOutOfBoundsException If <tt>index</tt> is negative or not smaller than the buffer's limit, minus one
     */
    char getChar(int index);

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
     * @return This buffer
     *
     * @throws IndexOutOfBoundsException If <tt>index</tt> is negative or not smaller than the buffer's limit, minus one
     *
     * @throws java.nio.ReadOnlyBufferException If this buffer is read-only
     */
    Buffer putChar(int index, char value);

    /**
     * Relative <i>get</i> method for reading a short value.
     *
     * <p>
     * Reads the next two bytes at this buffer's current position, composing them into a short value according to the
     * current byte order, and then increments the position by two.
     * </p>
     *
     * @return The short value at the buffer's current position
     *
     * @throws java.nio.BufferUnderflowException If there are fewer than two bytes remaining in this buffer
     */
    short getShort();

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
     * @return This buffer
     *
     * @throws java.nio.BufferOverflowException If there are fewer than two bytes remaining in this buffer
     *
     * @throws java.nio.ReadOnlyBufferException If this buffer is read-only
     */
    Buffer putShort(short value);

    /**
     * Absolute <i>get</i> method for reading a short value.
     *
     * <p>
     * Reads two bytes at the given index, composing them into a short value according to the current byte order.
     * </p>
     *
     * @param index The index from which the bytes will be read
     *
     * @return The short value at the given index
     *
     * @throws IndexOutOfBoundsException If <tt>index</tt> is negative or not smaller than the buffer's limit, minus one
     */
    short getShort(int index);

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
     * @return This buffer
     *
     * @throws IndexOutOfBoundsException If <tt>index</tt> is negative or not smaller than the buffer's limit, minus one
     *
     * @throws java.nio.ReadOnlyBufferException If this buffer is read-only
     */
    Buffer putShort(int index, short value);

    /**
     * Relative <i>get</i> method for reading an int value.
     *
     * <p>
     * Reads the next four bytes at this buffer's current position, composing them into an int value according to the
     * current byte order, and then increments the position by four.
     * </p>
     *
     * @return The int value at the buffer's current position
     *
     * @throws java.nio.BufferUnderflowException If there are fewer than four bytes remaining in this buffer
     */
    int getInt();

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
     * @return This buffer
     *
     * @throws java.nio.BufferOverflowException If there are fewer than four bytes remaining in this buffer
     *
     * @throws java.nio.ReadOnlyBufferException If this buffer is read-only
     */
    Buffer putInt(int value);

    /**
     * Absolute <i>get</i> method for reading an int value.
     *
     * <p>
     * Reads four bytes at the given index, composing them into a int value according to the current byte order.
     * </p>
     *
     * @param index The index from which the bytes will be read
     *
     * @return The int value at the given index
     *
     * @throws IndexOutOfBoundsException If <tt>index</tt> is negative or not smaller than the buffer's limit, minus three
     */
    int getInt(int index);

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
     * @return This buffer
     *
     * @throws IndexOutOfBoundsException If <tt>index</tt> is negative or not smaller than the buffer's limit, minus three
     *
     * @throws java.nio.ReadOnlyBufferException If this buffer is read-only
     */
    Buffer putInt(int index, int value);

    /**
     * Relative <i>get</i> method for reading a long value.
     *
     * <p>
     * Reads the next eight bytes at this buffer's current position, composing them into a long value according to the
     * current byte order, and then increments the position by eight.
     * </p>
     *
     * @return The long value at the buffer's current position
     *
     * @throws java.nio.BufferUnderflowException If there are fewer than eight bytes remaining in this buffer
     */
    long getLong();

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
     * @return This buffer
     *
     * @throws java.nio.BufferOverflowException If there are fewer than eight bytes remaining in this buffer
     *
     * @throws java.nio.ReadOnlyBufferException If this buffer is read-only
     */
    Buffer putLong(long value);

    /**
     * Absolute <i>get</i> method for reading a long value.
     *
     * <p>
     * Reads eight bytes at the given index, composing them into a long value according to the current byte order.
     * </p>
     *
     * @param index The index from which the bytes will be read
     *
     * @return The long value at the given index
     *
     * @throws IndexOutOfBoundsException If <tt>index</tt> is negative or not smaller than the buffer's limit, minus seven
     */
    long getLong(int index);

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
     * @return This buffer
     *
     * @throws IndexOutOfBoundsException If <tt>index</tt> is negative or not smaller than the buffer's limit, minus seven
     *
     * @throws java.nio.ReadOnlyBufferException If this buffer is read-only
     */
    Buffer putLong(int index, long value);

    /**
     * Relative <i>get</i> method for reading a float value.
     *
     * <p>
     * Reads the next four bytes at this buffer's current position, composing them into a float value according to the
     * current byte order, and then increments the position by four.
     * </p>
     *
     * @return The float value at the buffer's current position
     *
     * @throws java.nio.BufferUnderflowException If there are fewer than four bytes remaining in this buffer
     */
    float getFloat();

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
     * @return This buffer
     *
     * @throws java.nio.BufferOverflowException If there are fewer than four bytes remaining in this buffer
     *
     * @throws java.nio.ReadOnlyBufferException If this buffer is read-only
     */
    Buffer putFloat(float value);

    /**
     * Absolute <i>get</i> method for reading a float value.
     *
     * <p>
     * Reads four bytes at the given index, composing them into a float value according to the current byte order.
     * </p>
     *
     * @param index The index from which the bytes will be read
     *
     * @return The float value at the given index
     *
     * @throws IndexOutOfBoundsException If <tt>index</tt> is negative or not smaller than the buffer's limit, minus three
     */
    float getFloat(int index);

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
     * @return This buffer
     *
     * @throws IndexOutOfBoundsException If <tt>index</tt> is negative or not smaller than the buffer's limit, minus three
     *
     * @throws java.nio.ReadOnlyBufferException If this buffer is read-only
     */
    Buffer putFloat(int index, float value);

    /**
     * Relative <i>get</i> method for reading a double value.
     *
     * <p>
     * Reads the next eight bytes at this buffer's current position, composing them into a double value according to the
     * current byte order, and then increments the position by eight.
     * </p>
     *
     * @return The double value at the buffer's current position
     *
     * @throws java.nio.BufferUnderflowException If there are fewer than eight bytes remaining in this buffer
     */
    double getDouble();

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
     * @return This buffer
     *
     * @throws java.nio.BufferOverflowException If there are fewer than eight bytes remaining in this buffer
     *
     * @throws java.nio.ReadOnlyBufferException If this buffer is read-only
     */
    Buffer putDouble(double value);

    /**
     * Absolute <i>get</i> method for reading a double value.
     *
     * <p>
     * Reads eight bytes at the given index, composing them into a double value according to the current byte order.
     * </p>
     *
     * @param index The index from which the bytes will be read
     *
     * @return The double value at the given index
     *
     * @throws IndexOutOfBoundsException If <tt>index</tt> is negative or not smaller than the buffer's limit, minus seven
     */
    double getDouble(int index);

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
     * @return This buffer
     *
     * @throws IndexOutOfBoundsException If <tt>index</tt> is negative or not smaller than the buffer's limit, minus seven
     *
     * @throws java.nio.ReadOnlyBufferException If this buffer is read-only
     */
    Buffer putDouble(int index, double value);

    /**
     * Returns {@link Buffer} content as {@link String}, using default {@link Charset}
     *
     * @return {@link String} representation of this {@link Buffer} content.
     */
    String toStringContent();

    /**
     * Returns {@link Buffer} content as {@link String}
     *
     * @param charset the {@link Charset}, which will be use for byte[] to {@link String} transformation.
     *
     * @return {@link String} representation of this {@link Buffer} content.
     */
    String toStringContent(Charset charset);

    /**
     * Returns {@link Buffer}'s chunk content as {@link String}
     *
     * @param charset the {@link Charset}, which will be use for byte[] to {@link String} transformation.
     * @param position the first byte offset in the <tt>Buffer</tt> (inclusive)
     * @param limit the last byte offset in the <tt>Buffer</tt> (exclusive)
     *
     * @return {@link String} representation of part of this {@link Buffer}.
     */
    String toStringContent(Charset charset, int position, int limit);
}
