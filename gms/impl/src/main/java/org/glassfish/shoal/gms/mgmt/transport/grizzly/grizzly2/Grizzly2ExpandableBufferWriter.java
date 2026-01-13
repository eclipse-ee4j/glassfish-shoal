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

package org.glassfish.shoal.gms.mgmt.transport.grizzly.grizzly2;

import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.shoal.gms.mgmt.transport.buffers.Buffer;
import org.glassfish.shoal.gms.mgmt.transport.buffers.ExpandableBufferWriter;
import org.glassfish.shoal.gms.mgmt.transport.buffers.ExpandableBufferWriterFactory;

/**
 * Grizzly 2.0 based expandable Buffer writer.
 *
 * @author Alexey Stashok
 */
public final class Grizzly2ExpandableBufferWriter extends ExpandableBufferWriter {

    public static ExpandableBufferWriterFactory createFactory(final MemoryManager memoryManager) {
        return new ExpandableBufferWriterFactory() {

            @Override
            public ExpandableBufferWriter create() {
                return new Grizzly2ExpandableBufferWriter(memoryManager);
            }
        };
    }

    private final MemoryManager memoryManager;

    private final GMSBufferWrapper wrapper = new GMSBufferWrapper();
    private org.glassfish.grizzly.Buffer grizzlyBuffer;

    private Grizzly2ExpandableBufferWriter(final MemoryManager memoryManager) {
        this.memoryManager = memoryManager;
        grizzlyBuffer = memoryManager.allocate(4096);
        wrapper.wrap(grizzlyBuffer);
    }

    @Override
    public Buffer getBuffer() {
        return wrapper;
    }

    @Override
    public Buffer toBuffer() {
        grizzlyBuffer.trim();
        final Buffer duplicate = wrapper.duplicate();
        grizzlyBuffer.position(grizzlyBuffer.limit());

        return duplicate;
    }

    @Override
    public int position() {
        return grizzlyBuffer.position();
    }

    @Override
    public void position(final int pos) {
        grizzlyBuffer.position(pos);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void ensureCapacity(final int delta) {
        if (delta <= 0 || grizzlyBuffer.remaining() >= delta) {
            return;
        }

        grizzlyBuffer = memoryManager.reallocate(grizzlyBuffer, Math.max(grizzlyBuffer.capacity() * 2, grizzlyBuffer.capacity() + delta));
        wrapper.wrap(grizzlyBuffer);
    }
}
