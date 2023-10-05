/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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

import java.io.IOException;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.memory.MemoryManager;

import com.sun.enterprise.mgmt.transport.Message;
import com.sun.enterprise.mgmt.transport.MessageImpl;

/**
 * Filter, responsible for {@link Buffer} to {@link Message} transformation.
 *
 * Message protocol format is:
 *
 * Message Header is MessageImpl.HEADER_LENGTH and composed of following fields. magicNumber integer
 * {@link MessageImpl#MAGIC_NUMBER} version integer {@link MessageImpl#VERSION} type integer {@link Message#getType} for
 * possible values messageLength integer {@link MessageImpl#maxTotalMessageLength}
 *
 * Message Body is composed of following fields. payload byte[messageLen]
 *
 * MessageHeader {@link Message#parseHeader(com.sun.enterprise.mgmt.transport.buffers.Buffer, int)}
 * MessageBody {@link Message#parseMessage(com.sun.enterprise.mgmt.transport.buffers.Buffer, int, int)}
 *
 * @author Bongjae Chang
 * @author Joe Fialli
 * @author Alexey Stashok
 */
public class MessageFilter extends BaseFilter {

    private final Attribute<MessageParsingState> preparsedMessageAttr = Grizzly.DEFAULT_ATTRIBUTE_BUILDER
            .createAttribute(MessageFilter.class + ".preparsedMessageAttr", MessageParsingState::new);

    @Override
    public NextAction handleRead(final FilterChainContext ctx) throws IOException {
        final Connection<?> connection = ctx.getConnection();
        final Buffer buffer = ctx.getMessage();

        final MessageParsingState parsingState = preparsedMessageAttr.get(connection);

        if (!parsingState.isHeaderParsed) {
            // Header was not parsed yet
            if (buffer.remaining() < MessageImpl.HEADER_LENGTH) {
                // not enough data to parse the header
                return ctx.getStopAction(buffer);
            }

            final MessageImpl message = new MessageImpl();

            final GMSBufferWrapper gmsBuffer = parsingState.gmsBufferWrapper.wrap(buffer);

            final int messageLength = message.parseHeader(gmsBuffer, gmsBuffer.position());

            gmsBuffer.recycle();

            if (messageLength + MessageImpl.HEADER_LENGTH > MessageImpl.getMaxMessageLength()) {
                throw new IllegalStateException("too large message."
                    + " request-size=" + (messageLength + MessageImpl.HEADER_LENGTH)
                    + " max-size=" + MessageImpl.getMaxMessageLength());
            }

            parsingState.isHeaderParsed = true;
            parsingState.message = message;
            parsingState.messageLength = messageLength;
        }

        final int totalMsgLength = MessageImpl.HEADER_LENGTH + parsingState.messageLength;

        if (buffer.remaining() < totalMsgLength) {
            // We don't have entire message
            return ctx.getStopAction(buffer);
        }

        final int pos = buffer.position();

        final GMSBufferWrapper gmsBuffer = parsingState.gmsBufferWrapper.wrap(buffer);

        parsingState.message.parseMessage(gmsBuffer, pos + MessageImpl.HEADER_LENGTH, parsingState.messageLength);

        ctx.setMessage(parsingState.message);

        gmsBuffer.recycle();

        // Go to the next message
        final Buffer remainder = buffer.split(pos + totalMsgLength);

        parsingState.reset();

        return ctx.getInvokeAction(remainder.hasRemaining() ? remainder : null);
    }

    @Override
    public NextAction handleWrite(final FilterChainContext ctx) throws IOException {
        final Message message = ctx.getMessage();

        final MemoryManager<?> mm = ctx.getConnection().getTransport().getMemoryManager();

        com.sun.enterprise.mgmt.transport.buffers.Buffer buffer = message.getPlainBuffer(Grizzly2ExpandableBufferWriter.createFactory(mm));

        ctx.setMessage(buffer.underlying());

        return ctx.getInvokeAction();
    }

    static final class MessageParsingState {
        final GMSBufferWrapper gmsBufferWrapper = new GMSBufferWrapper();
        boolean isHeaderParsed;
        int messageLength;
        MessageImpl message;

        void reset() {
            isHeaderParsed = false;
            message = null;
            messageLength = 0;
            gmsBufferWrapper.recycle();
        }
    }
}
