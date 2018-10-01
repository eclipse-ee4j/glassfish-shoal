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

package com.sun.enterprise.mgmt.transport;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.enterprise.ee.cms.impl.common.GMSContext;
import com.sun.enterprise.ee.cms.impl.common.GMSContextFactory;
import com.sun.enterprise.ee.cms.impl.common.GMSMonitor;
import com.sun.enterprise.ee.cms.logging.GMSLogDomain;
import com.sun.enterprise.ee.cms.spi.GMSMessage;
import com.sun.enterprise.mgmt.transport.buffers.Buffer;
import com.sun.enterprise.mgmt.transport.buffers.BufferInputStream;
import com.sun.enterprise.mgmt.transport.buffers.BufferUtils;
import com.sun.enterprise.mgmt.transport.buffers.ExpandableBufferWriter;
import com.sun.enterprise.mgmt.transport.buffers.ExpandableBufferWriterFactory;

/**
 * This is a default {@link Message}'s implementation
 *
 * The byte array or ByteBuffer which represent this message's low level data will be cached if this message is not
 * modified Here are this message's structure ---- [packet] magic(4) + version(4) + type(4) + messages_length(4) +
 * messages(message_length) [messages] message_count(4) + message_key1 + message_value1 + message_key2 + message_value2
 * + ...(message_count) ----
 *
 * @author Bongjae Chang
 */
public class MessageImpl implements Message {

    static final long serialVersionUID = -3617083350698668655L;

    private static final Logger LOG = GMSLogDomain.getLogger(GMSLogDomain.GMS_LOGGER);

    public static final int DEFAULT_MAX_TOTAL_MESSAGE_LENGTH = 128 * 1024 + (2 * 1024);
    private static int maxTotalMessageLength = DEFAULT_MAX_TOTAL_MESSAGE_LENGTH;
    public static final int UNSPECIFIED_MESSAGE_LENGTH = -1;

    private static final int MAGIC_NUMBER = 770303;
    private static final int VERSION = 1;

    private static final int MAGIC_NUMBER_LENGTH = 4;
    private static final int VERSION_LENGTH = 4;
    private static final int TYPE_LENGTH = 4;
    private static final int MESSAGE_LENGTH = 4;
    public static final int HEADER_LENGTH = MAGIC_NUMBER_LENGTH + VERSION_LENGTH + TYPE_LENGTH + MESSAGE_LENGTH;

    private volatile int version;
    private volatile int type;

    private final Map<String, Serializable> messages = new HashMap<String, Serializable>();
    private final ReentrantLock messageLock = new ReentrantLock();
    private transient Buffer cachedBuffer;
    private transient ByteBuffer cachedByteBuffer;
    private boolean modified;

    public static int getMaxMessageLength() {
        return maxTotalMessageLength;
    }

    public static void setMaxMessageLength(int maxMsgLength) {
        maxTotalMessageLength = maxMsgLength;
    }

    public MessageImpl() {
    }

    public MessageImpl(final int type) {
        initialize(type, null);
    }

    public MessageImpl(final int type, final Map<String, Serializable> messages) {
        initialize(type, messages);
    }

    /**
     * {@inheritDoc}
     */
    public void initialize(final int type, final Map<String, Serializable> messages) throws IllegalArgumentException {
        this.version = VERSION;
        // Let's allow unknown message types
        /*
         * switch( type ) { case TYPE_CLUSTER_MANAGER_MESSAGE: case TYPE_HEALTH_MONITOR_MESSAGE: case TYPE_MASTER_NODE_MESSAGE:
         * break; default: throw new IllegalArgumentException( "type is not valid" ); }
         */
        this.type = type;
        if (messages != null) {
            messageLock.lock();
            try {
                this.messages.clear();
                this.messages.putAll(messages);
            } finally {
                modified = true;
                messageLock.unlock();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public int parseHeader(final byte[] bytes, final int offset) throws IllegalArgumentException {
        if (bytes == null) {
            throw new IllegalArgumentException("bytes must be initialized");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset is too small");
        }
        if (bytes.length < offset + HEADER_LENGTH) {
            throw new IllegalArgumentException("bytes' length is too small");
        }

        int messageLen;
        if (bytes.length - offset < HEADER_LENGTH) {
            throw new IllegalArgumentException("byte[] is too small");
        }

        int magicNumber = readInt(bytes, offset);
        if (magicNumber != MAGIC_NUMBER) {
            throw new IllegalArgumentException("magic number is not valid");
        }
        version = readInt(bytes, offset + 4);
        type = readInt(bytes, offset + 8);
        messageLen = readInt(bytes, offset + 12);
        return messageLen;
    }

    /**
     * {@inheritDoc}
     */
    public int parseHeader(final Buffer buffer, final int offset) throws IllegalArgumentException {
        if (buffer == null) {
            throw new IllegalArgumentException("byte buffer must be initialized");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset is too small");
        }
        int messageLen;
        int restorePosition = buffer.position();
        try {
            buffer.position(offset);
            if (buffer.remaining() < HEADER_LENGTH) {
                throw new IllegalArgumentException("byte buffer's remaining() is too small");
            }
            int magicNumber = buffer.getInt();
            if (magicNumber != MAGIC_NUMBER) {
                throw new IllegalArgumentException("magic number is not valid");
            }
            version = buffer.getInt();
            type = buffer.getInt();
            messageLen = buffer.getInt();
        } finally {
            buffer.position(restorePosition);
        }
        return messageLen;
    }

    /**
     * {@inheritDoc}
     */
    public void parseMessage(final byte[] bytes, final int offset, final int length) throws IllegalArgumentException, MessageIOException {
        if (bytes == null) {
            throw new IllegalArgumentException("bytes must be initialized");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset is too small");
        }
        if (length < 0) {
            throw new IllegalArgumentException("length is too small");
        }
        if (bytes.length < offset + length) {
            throw new IllegalArgumentException("bytes' length is too small");
        }

        if (length > 0) {
            int msgSize = HEADER_LENGTH + length;
            if (msgSize > maxTotalMessageLength) {
                if (LOG.isLoggable(Level.WARNING)) {
                    LOG.log(Level.WARNING, "total message size is too big: size = " + msgSize + ", max size = " + maxTotalMessageLength);
                }
            }

            if (bytes.length - offset < length) {
                throw new IllegalArgumentException("byte[] is too small");
            }

            ByteArrayInputStream bais = new ByteArrayInputStream(bytes, offset, length);
            try {
                readMessagesInputStream(bais);
            } finally {
                try {
                    bais.close();
                } catch (IOException e) {
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public void parseMessage(final Buffer buffer, final int offset, final int length) throws IllegalArgumentException, MessageIOException {
        long receiveDuration = 0L;
        long receiveStartTime = 0L;
        boolean calledMonitor = false;
        if (buffer == null) {
            throw new IllegalArgumentException("byte buffer must be initialized");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset is too small");
        }
        if (length < 0) {
            throw new IllegalArgumentException("length is too small");
        }
        if (length > 0) {
            int msgSize = HEADER_LENGTH + length;
            if (msgSize > maxTotalMessageLength) {
                if (LOG.isLoggable(Level.WARNING)) {
                    LOG.log(Level.WARNING, "total message size is too big: size = " + msgSize + ", max size = " + maxTotalMessageLength);
                }
            }
            int restorePosition = buffer.position();
            int restoreLimit = buffer.limit();

            try {
                buffer.position(offset);
                if (buffer.remaining() < length) {
                    throw new IllegalArgumentException("byte buffer's remaining() is too small");
                }

                buffer.limit(offset + length);
                receiveStartTime = System.currentTimeMillis();
                readMessagesInputStream(new BufferInputStream(buffer));
                receiveDuration = System.currentTimeMillis() - receiveStartTime;
                calledMonitor = true;
                monitorReceive(receiveDuration);
            } catch (MessageIOException mioe) {
                receiveDuration = System.currentTimeMillis() - receiveStartTime;
                calledMonitor = true;
                monitorReceive(receiveDuration, true);
            } finally {
                BufferUtils.setPositionLimit(buffer, restorePosition, restoreLimit);
                if (!calledMonitor) {
                    // an error occurred in receiving the message since receiveDuration never got set.
                    receiveDuration = System.currentTimeMillis() - receiveStartTime;
                    monitorReceive(receiveDuration, true);
                }
            }
        }
    }

    private transient GMSMonitor gmsMonitor = null;
    private transient boolean checkForGmsMonitor = true;

    private void monitorReceive(long receiveDuration) {
        monitorReceive(receiveDuration, false);
    }

    private void monitorReceive(long receiveDuration, boolean receiveError) {
        GMSMessage msg = null;

        if (gmsMonitor == null && checkForGmsMonitor) {
            Object element = messages.get("APPMESSAGE");
            if (element instanceof GMSMessage) {
                msg = (GMSMessage) element;
                GMSContext ctx = GMSContextFactory.getGMSContext(msg.getGroupName());
                if (ctx != null) {
                    gmsMonitor = ctx.getGMSMonitor();
                }
                checkForGmsMonitor = false;
            }
        }

        if (gmsMonitor != null && gmsMonitor.ENABLED) {
            if (msg == null) {
                Object element = messages.get("APPMESSAGE");
                if (element instanceof GMSMessage) {
                    msg = (GMSMessage) element;
                }
            }
            if (msg != null) {
                GMSMonitor.MessageStats stats = gmsMonitor.getGMSMessageMonitorStats(msg.getComponentName());
                stats.addBytesReceived(msg.getMessage().length);
                stats.incrementNumMsgsReceived();
                stats.addReceiveDuration(receiveDuration);
            } else if (receiveError) {
                GMSMonitor.MessageStats stats = gmsMonitor.getGMSMessageMonitorStats("unknown-component-due-to-receive-side-error");
                stats.addReceiveDuration(receiveDuration);
            }
        }
    }

    private void readMessagesInputStream(InputStream is) throws IllegalArgumentException, MessageIOException {
        try {
            int messageCount = readInt(is);
            messageLock.lock();
            try {
                NetworkUtility.deserialize(is, messageCount, messages);
            } finally {
                modified = true;
                messageLock.unlock();
            }
        } catch (IOException ie) {
            throw new MessageIOException(ie);
        }
    }

    /**
     * {@inheritDoc}
     */
    public int getVersion() {
        return version;
    }

    /**
     * {@inheritDoc}
     */
    public int getType() {
        return type;
    }

    /**
     * {@inheritDoc}
     */
    public Object addMessageElement(final String key, final Serializable value) {
        messageLock.lock();
        try {
            return messages.put(key, value);
        } finally {
            modified = true;
            messageLock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    public Object getMessageElement(final String key) {
        return messages.get(key);
    }

    /**
     * {@inheritDoc}
     */
    public Object removeMessageElement(final String key) {
        messageLock.lock();
        Serializable removed = null;
        try {
            removed = messages.remove(key);
            return removed;
        } finally {
            if (removed != null) {
                modified = true;
            }
            messageLock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    public Set<Map.Entry<String, Serializable>> getMessageElements() {
        return Collections.unmodifiableSet(messages.entrySet());
    }

    /**
     * {@inheritDoc}
     */
    public ByteBuffer getPlainByteBuffer() throws MessageIOException {
        messageLock.lock();
        try {
            if (cachedByteBuffer != null && !modified) {
                return cachedByteBuffer;
            }
            MessageByteArrayOutputStream mbaos = new MessageByteArrayOutputStream();
            DataOutputStream dos = null;
            try {
                dos = new DataOutputStream(mbaos);
                int tempInt = 0;
                dos.writeInt(tempInt);
                int messageCount = NetworkUtility.serialize(mbaos, messages);
                mbaos.writeIntWithoutCount(0, messageCount);
            } catch (IOException ie) {
                throw new MessageIOException(ie);
            } finally {
                if (dos != null) {
                    try {
                        dos.close();
                    } catch (IOException e) {
                    }
                }
            }
            int messageLen;
            byte[] messageBytes = mbaos.getPlainByteArray();
            if (messageBytes != null) {
                messageLen = Math.min(messageBytes.length, mbaos.size());
            } else {
                messageLen = 0;
            }
            int msgSize = HEADER_LENGTH + messageLen;
            if (msgSize > maxTotalMessageLength) {
                if (LOG.isLoggable(Level.WARNING)) {
                    LOG.log(Level.WARNING, "messageImpl.msg.too.big", new Object[] { msgSize, maxTotalMessageLength });
                }
                throw new MessageIOException("total message size is too big: size = " + msgSize + ", max size = " + maxTotalMessageLength + toString());
            }
            cachedByteBuffer = ByteBuffer.allocate(HEADER_LENGTH + messageLen);
            cachedByteBuffer.putInt(MAGIC_NUMBER);
            cachedByteBuffer.putInt(version);
            cachedByteBuffer.putInt(type);
            cachedByteBuffer.putInt(messageLen);
            cachedByteBuffer.put(messageBytes, 0, messageLen);
            cachedByteBuffer.flip();
            return cachedByteBuffer;
        } finally {
            modified = false;
            messageLock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    public byte[] getPlainBytes() throws MessageIOException {
        messageLock.lock();
        try {
            return getPlainByteBuffer().array();
        } finally {
            messageLock.unlock();
        }
    }

    public Buffer getPlainBuffer(final ExpandableBufferWriterFactory bufferWriterFactory) throws MessageIOException {
        messageLock.lock();
        try {
            if (cachedBuffer != null && !modified) {
                return cachedBuffer.duplicate();
            }

            final ExpandableBufferWriter bufferWriter = bufferWriterFactory.create();

            final int headerStart = bufferWriter.position();
            bufferWriter.reserve(HEADER_LENGTH);

            try {
                final int pos = bufferWriter.position();
                bufferWriter.reserve(4);

                final int messageCount = NetworkUtility.serialize(bufferWriter.asOutputStream(), messages);

                bufferWriter.putInt(pos, messageCount);
            } catch (IOException ie) {
                throw new MessageIOException(ie);
            }

            final int msgSize = bufferWriter.position();
            if (msgSize > maxTotalMessageLength) {
                if (LOG.isLoggable(Level.WARNING)) {
                    LOG.log(Level.WARNING, "messageImpl.msg.too.big", new Object[] { msgSize, maxTotalMessageLength });
                }

                throw new MessageIOException("total message size is too big: size = " + msgSize + ", max size = " + maxTotalMessageLength + toString());
            }

            bufferWriter.putInt(headerStart, MAGIC_NUMBER);
            bufferWriter.putInt(headerStart + 4, version);
            bufferWriter.putInt(headerStart + 8, type);
            bufferWriter.putInt(headerStart + 12, msgSize - HEADER_LENGTH);

            cachedBuffer = bufferWriter.toBuffer();

            return bufferWriter.toBuffer();
        } finally {
            messageLock.unlock();
        }
    }

    public static String getStringType(final int type) {
        switch (type) {
        case TYPE_CLUSTER_MANAGER_MESSAGE:
            return "CLUSTER_MANAGER_MESSAGE";
        case TYPE_HEALTH_MONITOR_MESSAGE:
            return "HEALTH_MONITOR_MESSAGE";
        case TYPE_MASTER_NODE_MESSAGE:
            return "MASTER_NODE_MESSAGE";
        case TYPE_MCAST_MESSAGE:
            return "MCAST_MESSAGE";
        case TYPE_PING_MESSAGE:
            return "PING_MESSAGE";
        case TYPE_PONG_MESSAGE:
            return "PONG_MESSAGE";
        default:
            return "UNKNOWN_MESSAGE(" + type + ")";
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(50);
        sb.append(MessageImpl.class.getSimpleName());
        sb.append("[v").append(version).append(":");
        sb.append(getStringType(type)).append(":");
        Serializable seq = messages.get("SEQ");
        if (seq != null) {
            sb.append(" MasterViewSeqID:").append(seq);
        }

        for (String elementName : messages.keySet()) {
            if (SOURCE_PEER_ID_TAG.compareTo(elementName) == 0) {
                sb.append(" Source: ").append(messages.get(SOURCE_PEER_ID_TAG)).append(", ");
            } else if (TARGET_PEER_ID_TAG.compareTo(elementName) == 0) {
                sb.append(" Target: ").append(messages.get(TARGET_PEER_ID_TAG)).append(" , ");
            } else {
                sb.append(" ").append(elementName).append(", ");
            }
        }
        return sb.toString();
    }

    private static int readInt(InputStream is) throws IOException {
        int ch1 = is.read();
        int ch2 = is.read();
        int ch3 = is.read();
        int ch4 = is.read();
        if ((ch1 | ch2 | ch3 | ch4) < 0) {
            throw new EOFException();
        }

        return ((ch1 << 24) + (ch2 << 16) + (ch3 << 8) + (ch4 << 0));
    }

    private static int readInt(byte[] bytes, int offset) {
        int ch1 = bytes[offset] & 0xFF;
        int ch2 = bytes[offset + 1] & 0xFF;
        int ch3 = bytes[offset + 2] & 0xFF;
        int ch4 = bytes[offset + 3] & 0xFF;

        return ((ch1 << 24) + (ch2 << 16) + (ch3 << 8) + (ch4 << 0));
    }

    static private class MessageByteArrayOutputStream extends ByteArrayOutputStream {

        private MessageByteArrayOutputStream() {
            super();
        }

        private synchronized byte[] getPlainByteArray() {
            return buf;
        }

        private synchronized void writeIntWithoutCount(final int pos, final int value) {
            NetworkUtility.writeIntToByteArray(buf, pos, value);
        }
    }

}
