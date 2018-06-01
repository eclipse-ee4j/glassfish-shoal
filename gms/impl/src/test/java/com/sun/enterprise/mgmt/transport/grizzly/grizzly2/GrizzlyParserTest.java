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
import com.sun.enterprise.mgmt.transport.Message;
import com.sun.enterprise.mgmt.transport.MessageImpl;
import junit.framework.TestCase;

import java.io.IOException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.impl.SafeFutureImpl;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.utils.ChunkingFilter;

/**
 * Set of Grizzly 2.0 tests
 *
 * @author Alexey Stashok
 */
public class GrizzlyParserTest extends TestCase {
    public static final String NUMBER_ELEMENT_KEY = "Number";
    public static final String RESULT_ELEMENT = "RESULT";

    public static final int PORT = 8998;

    private static final Logger LOGGER = Grizzly.logger(GrizzlyParserTest.class);
    
    private TCPNIOTransport transport;
    private ServerEchoFilter serverEchoFilter;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        transport = initializeServer();
    }

    @Override
    protected void tearDown() throws Exception {
        transport.stop();

        super.tearDown();
    }

    public void testSimpleMessage() throws Exception {
        final Message request = createMessage(1, 1);
        final Message response = sendRequest(request);

        final String result = (String) response.getMessageElement(RESULT_ELEMENT);
        assertNotNull(result);
        assertEquals("OK", result);
    }

    public void test10Messages() throws Exception {
        for (int i = 0; i < 10; i++) {
            final Message request = createMessage(i + 1, i * 512);
            final Message response = sendRequest(request);

            final String result = (String) response.getMessageElement(RESULT_ELEMENT);
            assertNotNull(result);
            assertEquals("OK", result);
        }
    }

    public Message sendRequest(final Message request) throws Exception {
        final FutureImpl<Message> future = SafeFutureImpl.<Message>create();
        
        final FilterChain clientFilterChain = FilterChainBuilder.stateless()
                .add(new TransportFilter())
                .add(new ChunkingFilter(1))
                .add(new MessageFilter())
                .add(new ClientResultFilter(future))
                .build();
        final TCPNIOTransport clientTransport =
                TCPNIOTransportBuilder.newInstance().build();
        clientTransport.setProcessor(clientFilterChain);

        Connection connection = null;
        try {
            clientTransport.start();

            final Future<Connection> connectFuture =
                    clientTransport.connect("localhost", PORT);

            connection = connectFuture.get(10, TimeUnit.SECONDS);

            connection.write(request);

            return future.get(60, TimeUnit.SECONDS);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error on msg#" + request.getMessageElement(NUMBER_ELEMENT_KEY)
                    + " server processed " + serverEchoFilter.getCount() + " requests", e);
            throw e;
        } finally {
            if (connection != null) {
                connection.close();
            }
            try {
                clientTransport.stop();
            } catch (IOException ignored) {
            }
        }
    }

    private static Message createMessage(final int num,
            final int objectsCount) throws IOException {
        final MessageImpl message = new MessageImpl(100);
        
        message.addMessageElement(NUMBER_ELEMENT_KEY, num);
        for (int i = 0; i < objectsCount; i++) {
            message.addMessageElement("Param #" + i, "Value #" + i);
        }

        return message;
    }

    private static class ClientResultFilter extends BaseFilter {
        private final FutureImpl<Message> future;

        private ClientResultFilter(FutureImpl<Message> future) {
            this.future = future;
        }

        @Override
        public NextAction handleRead(final FilterChainContext ctx) throws IOException {
            final Message message = ctx.getMessage();
            future.result(message);

            return ctx.getStopAction();
        }
    
    }

    private static class ServerEchoFilter extends BaseFilter {
        private final AtomicInteger count = new AtomicInteger();

        @Override
        public NextAction handleRead(final FilterChainContext ctx) throws IOException {
            final Message message = ctx.getMessage();

            count.incrementAndGet();
            
            final Message outputMessage = new MessageImpl(100);
            outputMessage.addMessageElement(RESULT_ELEMENT, message != null ? "OK" : "FAILED");

            ctx.write(outputMessage);
            
            return ctx.getInvokeAction();
        }

        private int getCount() {
            return count.get();
        }
    }

    private TCPNIOTransport initializeServer() throws IOException {
        serverEchoFilter = new ServerEchoFilter();

        final FilterChain filterChain = FilterChainBuilder.stateless()
                .add(new TransportFilter())
                .add(new ChunkingFilter(1))
                .add(new MessageFilter())
                .add(serverEchoFilter)
                .build();

        TCPNIOTransport newTransport = TCPNIOTransportBuilder.newInstance().build();
        newTransport.setProcessor(filterChain);
        newTransport.bind(PORT);
        newTransport.start();
        return newTransport;
    }
}
