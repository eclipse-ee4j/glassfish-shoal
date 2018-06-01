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

package com.sun.enterprise.mgmt.transport.buffers;

import java.io.UnsupportedEncodingException;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

/**
 *
 * @author Alexey Stashok
 */
public class BufferUtils {

    public static final ByteBuffer EMPTY_BYTE_BUFFER = ByteBuffer.allocate(0);
    public static final ByteBuffer[] EMPTY_BYTE_BUFFER_ARRAY = new ByteBuffer[0];

    /**
     * Slice {@link ByteBuffer} of required size from big chunk.
     * Passed chunk position will be changed, after the slicing (chunk.position += size).
     *
     * @param chunk big {@link ByteBuffer} pool.
     * @param size required slice size.
     *
     * @return sliced {@link ByteBuffer} of required size.
     */
    public static ByteBuffer slice(ByteBuffer chunk, int size) {
        chunk.limit(chunk.position() + size);
        ByteBuffer view = chunk.slice();
        chunk.position(chunk.limit());
        chunk.limit(chunk.capacity());

        return view;
    }


    /**
     * Get the {@link ByteBuffer}'s slice basing on its passed position and limit.
     * Position and limit values of the passed {@link ByteBuffer} won't be changed.
     * The result {@link ByteBuffer} position will be equal to 0, and limit
     * equal to number of sliced bytes (limit - position).
     *
     * @param byteBuffer {@link ByteBuffer} to slice/
     * @param position the position in the passed byteBuffer, the slice will start from.
     * @param limit the limit in the passed byteBuffer, the slice will be ended.
     *
     * @return sliced {@link ByteBuffer} of required size.
     */
    public static ByteBuffer slice(final ByteBuffer byteBuffer,
            final int position, final int limit) {
        final int oldPos = byteBuffer.position();
        final int oldLimit = byteBuffer.limit();

        setPositionLimit(byteBuffer, position, limit);

        final ByteBuffer slice = byteBuffer.slice();

        setPositionLimit(byteBuffer, oldPos, oldLimit);

        return slice;
    }

    public static String toStringContent(ByteBuffer byteBuffer, Charset charset,
            int position, int limit) {

        if (charset == null) {
            charset = Charset.defaultCharset();
        }

        if (byteBuffer.hasArray()) {
            try {
                return new String(byteBuffer.array(),
                        position + byteBuffer.arrayOffset(),
                        limit - position, charset.name());
            } catch (UnsupportedEncodingException e) {
                throw new IllegalStateException(e);
            }
//            Uncomment, when StringDecoder will not create copy of byte[]
//            return new String(byteBuffer.array(),
//                    position + byteBuffer.arrayOffset(),
//                    limit - position, charset);
        } else {
            int oldPosition = byteBuffer.position();
            int oldLimit = byteBuffer.limit();
            setPositionLimit(byteBuffer, position, limit);

            byte[] tmpBuffer = new byte[limit - position];
            byteBuffer.get(tmpBuffer);

            setPositionLimit(byteBuffer, oldPosition, oldLimit);

            try {
                return new String(tmpBuffer, charset.name());
            } catch (UnsupportedEncodingException e) {
                throw new IllegalStateException(e);
            }

//            Uncomment, when StringDecoder will not create copy of byte[]
//            return new String(tmpBuffer, charset);
        }
    }

    public static void setPositionLimit(final ByteBuffer buffer, final int position, final int limit) {
            buffer.limit(limit);
            buffer.position(position);
        }

    public static void setPositionLimit(final Buffer buffer, final int position, final int limit) {
            buffer.limit(limit);
            buffer.position(position);
        }

    public static void put(ByteBuffer srcBuffer, int srcOffset, int length,
            ByteBuffer dstBuffer) {

        if (dstBuffer.remaining() < length) {
            throw new BufferOverflowException();
        }

        if (srcBuffer.hasArray() && dstBuffer.hasArray()) {

            System.arraycopy(srcBuffer.array(),
                    srcBuffer.arrayOffset() + srcOffset,
                    dstBuffer.array(),
                    dstBuffer.arrayOffset() + dstBuffer.position(),
                    length);
            dstBuffer.position(dstBuffer.position() + length);
        } else {
            int oldPos = srcBuffer.position();
            int oldLim = srcBuffer.limit();
            setPositionLimit(srcBuffer, srcOffset, srcOffset + length);

            dstBuffer.put(srcBuffer);

            srcBuffer.position(oldPos);
            srcBuffer.limit(oldLim);
        }
    }
   
    public static void get(ByteBuffer srcBuffer,
            byte[] dstBytes, int dstOffset, int length) {

        if (srcBuffer.hasArray()) {
            if (length > srcBuffer.remaining()) {
                throw new BufferUnderflowException();
            }

            System.arraycopy(srcBuffer.array(),
                    srcBuffer.arrayOffset() + srcBuffer.position(),
                    dstBytes, dstOffset, length);
            srcBuffer.position(srcBuffer.position() + length);
        } else {
            srcBuffer.get(dstBytes, dstOffset, length);
        }
    }

    public static void put(byte[] srcBytes, int srcOffset, int length,
            ByteBuffer dstBuffer) {
        if (dstBuffer.hasArray()) {
            if (length > dstBuffer.remaining()) {
                throw new BufferOverflowException();
            }

            System.arraycopy(srcBytes, srcOffset, dstBuffer.array(),
                    dstBuffer.arrayOffset() + dstBuffer.position(), length);
            dstBuffer.position(dstBuffer.position() + length);
        } else {
            dstBuffer.put(srcBytes, srcOffset, length);
        }
    }
}
