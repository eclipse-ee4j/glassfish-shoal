/*
 * Copyright (c) 2010, 2018 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.enterprise.mgmt.transport.grizzly.grizzly1_9;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.util.Arrays;
import java.util.logging.Level;

import com.sun.grizzly.Context;
import com.sun.grizzly.Controller;
import com.sun.grizzly.DefaultProtocolChain;
import com.sun.grizzly.DefaultProtocolChainInstanceHandler;
import com.sun.grizzly.ProtocolChain;
import com.sun.grizzly.ProtocolFilter;
import com.sun.grizzly.ProtocolParser;
import com.sun.grizzly.TCPSelectorHandler;
import com.sun.grizzly.filter.ParserProtocolFilter;
import com.sun.grizzly.util.OutputWriter;
import com.sun.grizzly.util.WorkerThreadImpl;

import junit.framework.TestCase;

/**
 * Set of Grizzly tests
 *
 * @author Alexey Stashok
 */
public class GrizzlyParserTest extends TestCase {
    public static final int PORT = 8999;

    private static final int MAGIC_NUMBER = 770303;
    private static final int VERSION = 1;

    private static final int THREAD_LOCAL_BUFFER_SIZE = WorkerThreadImpl.DEFAULT_BYTE_BUFFER_SIZE;

    private Controller controller;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        enableDebug(false);

        controller = initializeServer();
    }

    @Override
    protected void tearDown() throws Exception {
        controller.stop();

        super.tearDown();
    }

    public void testSimpleMessage() throws IOException {
        Socket s = new Socket("localhost", PORT);
        s.setSoTimeout(5000);
        OutputStream os = s.getOutputStream();

        byte[] message = createMessage(1);
        os.write(message);
        os.flush();

        InputStream is = s.getInputStream();
        int result = is.read();

        s.close();
        assertEquals(1, result);
    }

    public void testChunkedMessage() throws IOException {
        Socket s = new Socket("localhost", PORT);
        s.setSoTimeout(5000);
        OutputStream os = s.getOutputStream();

        byte[] message = createMessage(3);

        os.write(message, 0, 4); // MAGIC
        sleep(os, 1000);

        os.write(message, 4, 4); // VERSION
        sleep(os, 1000);

        os.write(message, 8, 4); // TYPE
        sleep(os, 1000);

        os.write(message, 12, 4); // BODY-SIZE
        sleep(os, 2000);

        os.write(message, 16, 4); // PARAMS-COUNT
        sleep(os, 1000);

        int messageHalf = (message.length - 20) / 2;
        os.write(message, 20, messageHalf); // BODY#1
        sleep(os, 2000);

        os.write(message, 20 + messageHalf, message.length - messageHalf - 20); // BODY #2
        os.flush();

        InputStream is = s.getInputStream();
        int result = is.read();

        s.close();
        assertEquals(1, result);
    }

    public void testOneAndHalfMessage() throws IOException {
        Socket s = new Socket("localhost", PORT);
        s.setSoTimeout(5000);
        OutputStream os = s.getOutputStream();

        byte[] message1 = createMessage(3);
        byte[] message2 = createMessage(5);

        byte[] totalMessage = Arrays.copyOf(message1, message1.length + message2.length);
        System.arraycopy(message2, 0, totalMessage, message1.length, message2.length);

        int oneAndHalf = message1.length + message2.length / 2;

        os.write(totalMessage, 0, oneAndHalf); // 2/3 MESSAGE
        sleep(os, 4000);

        os.write(totalMessage, oneAndHalf, totalMessage.length - oneAndHalf); // 1/3 MESSAGE

        os.flush();

        InputStream is = s.getInputStream();
        int result1 = is.read();
        int result2 = is.read();

        s.close();

        assertEquals(1, result1);
        assertEquals(1, result2);
    }

    public void testBigMessage() throws IOException {
        Socket s = new Socket("localhost", PORT);
        s.setSoTimeout(5000);
        OutputStream os = s.getOutputStream();

        byte[] message = createMessage(2048);
        System.out.println("[DEBUG] messageSize=" + message.length);
        os.write(message);
        os.flush();

        InputStream is = s.getInputStream();
        int result = is.read();

        s.close();

        assertEquals(1, result);
    }

    public void testOneAndHalfBigMessage() throws IOException {
        Socket s = new Socket("localhost", PORT);
        s.setSoTimeout(5000);
        OutputStream os = s.getOutputStream();

        byte[] message1 = createMessage(2048);
        byte[] message2 = createMessage(2048);

        byte[] totalMessage = Arrays.copyOf(message1, message1.length + message2.length);
        System.arraycopy(message2, 0, totalMessage, message1.length, message2.length);

        int oneAndHalf = message1.length + message2.length / 2;

        os.write(totalMessage, 0, oneAndHalf); // 2/3 MESSAGE
        sleep(os, 4000);

        os.write(totalMessage, oneAndHalf, totalMessage.length - oneAndHalf); // 1/3 MESSAGE

        os.flush();

        InputStream is = s.getInputStream();
        int result1 = is.read();
        int result2 = is.read();

        s.close();

        assertEquals(1, result1);
        assertEquals(1, result2);
    }

    public void testTinyRemainder() throws IOException {
        Socket s = new Socket("localhost", PORT);
        s.setSoTimeout(5000);
        OutputStream os = s.getOutputStream();

        byte[] message1 = createMessage(2048);
        byte[] message2 = createMessage(2048);

        byte[] totalMessage = Arrays.copyOf(message1, message1.length + message2.length);
        System.arraycopy(message2, 0, totalMessage, message1.length, message2.length);

        int offset = 0;

        while (offset < totalMessage.length) {
            int sendSize = Math.min(THREAD_LOCAL_BUFFER_SIZE - 100, totalMessage.length - offset);
            os.write(totalMessage, offset, sendSize); // send chunk
            sleep(os, 2000);

            offset += sendSize;
        }

        InputStream is = s.getInputStream();
        int result1 = is.read();
        int result2 = is.read();

        s.close();

        assertEquals(1, result1);
        assertEquals(1, result2);
    }

    public void test100KMessages() throws IOException {
        Socket s = new Socket("localhost", PORT);
        s.setSoTimeout(5000);
        OutputStream os = s.getOutputStream();

        byte[] message1 = createMessage(4096);
        byte[] message2 = createMessage(4096);

        byte[] totalMessage = Arrays.copyOf(message1, message1.length + message2.length);
        System.arraycopy(message2, 0, totalMessage, message1.length, message2.length);

        int oneAndHalf = message1.length + message2.length / 2;

        os.write(totalMessage, 0, oneAndHalf); // 2/3 MESSAGE
        sleep(os, 4000);

        os.write(totalMessage, oneAndHalf, totalMessage.length - oneAndHalf); // 1/3 MESSAGE

        os.flush();

        InputStream is = s.getInputStream();
        int result1 = is.read();
        int result2 = is.read();

        s.close();

        assertEquals(1, result1);
        assertEquals(1, result2);
    }

    private static byte[] createMessage(int objectsCount) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        byte[] body = createBody(objectsCount);

        dos.writeInt(MAGIC_NUMBER);
        dos.writeInt(VERSION);
        dos.writeInt(3);
        dos.writeInt(body.length + 4);

        dos.writeInt(objectsCount);
        dos.write(body);
        dos.flush();

        byte[] message = baos.toByteArray();
        dos.close();
        return message;
    }

    private static byte[] createBody(int objectsCount) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);

        for (int i = 0; i < objectsCount; i++) {
            oos.writeObject("Param #" + i);
            oos.writeObject("Value #" + i);
        }

        oos.flush();

        byte[] body = baos.toByteArray();
        oos.close();

        return body;
    }

    private static ParserFilter createParserProtocolFilter() {
        return new ParserFilter() {

            @Override
            public ProtocolParser newProtocolParser() {
                return new GrizzlyMessageProtocolParser();
            }

        };
    }

    private static class ResultFilter implements ProtocolFilter {

        @Override
        public boolean execute(Context ctx) throws IOException {
            Object message = ctx.getAttribute(ProtocolParser.MESSAGE);
            byte[] b = new byte[1];
            b[0] = (byte) (message != null ? 1 : 0);
            ByteBuffer bb = ByteBuffer.wrap(b);
            SelectableChannel channel = ctx.getSelectionKey().channel();
            OutputWriter.flushChannel(channel, bb);
            return false;
        }

        @Override
        public boolean postExecute(Context ctx) throws IOException {
            return true;
        }
    }

    private static void sleep(OutputStream dos, int i) throws IOException {
        dos.flush();
        System.out.println("[DEBUG] Sleeping for " + i + " millis");
        try {
            Thread.sleep(i);
        } catch (InterruptedException e) {
        }
    }

    private static void enableDebug(boolean b) {
        if (b) {
            GrizzlyMessageProtocolParser.DEBUG_ENABLED = true;
            GrizzlyMessageProtocolParser.DEBUG_LEVEL = Level.INFO;
        }
    }

    private static Controller initializeServer() {
        final ProtocolFilter resultFilter = new ResultFilter();
        final ParserProtocolFilter parserProtocolFilter = createParserProtocolFilter();
        TCPSelectorHandler selectorHandler = new TCPSelectorHandler();
        selectorHandler.setPort(PORT);

        final Controller controller = new Controller();

        controller.setSelectorHandler(selectorHandler);

        controller.setProtocolChainInstanceHandler(new DefaultProtocolChainInstanceHandler() {

            @Override
            public ProtocolChain poll() {
                ProtocolChain protocolChain = protocolChains.poll();
                if (protocolChain == null) {
                    protocolChain = new DefaultProtocolChain();
                    protocolChain.addFilter(parserProtocolFilter);
                    protocolChain.addFilter(resultFilter);
                }
                return protocolChain;
            }
        });

        ControllerUtils.startController(controller);

        return controller;
    }
}
