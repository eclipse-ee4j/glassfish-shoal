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

package org.shoal.ha.cache.impl.command;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.lang.reflect.Array;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.shoal.ha.cache.api.AbstractCommandInterceptor;
import org.shoal.ha.cache.api.DataStoreContext;
import org.shoal.ha.cache.api.DataStoreException;
import org.shoal.ha.cache.api.ObjectInputStreamWithLoader;
import org.shoal.ha.cache.api.ShoalCacheLoggerConstants;
import org.shoal.ha.cache.impl.interceptor.CommandHandlerInterceptor;
import org.shoal.ha.cache.impl.interceptor.TransmitInterceptor;
import org.shoal.ha.cache.impl.util.MessageReceiver;

/**
 * @author Mahesh Kannan
 */
public class CommandManager<K, V> extends MessageReceiver {

    private String myName;

    private DataStoreContext<K, V> dsc;

    private Command<K, V>[] commands = (Command<K, V>[]) Array.newInstance(Command.class, 256);

    private volatile AbstractCommandInterceptor<K, V> head;

    private volatile AbstractCommandInterceptor<K, V> tail;

    private static Logger _logger = Logger.getLogger(ShoalCacheLoggerConstants.CACHE_COMMAND);

    private static Logger _statsLogger = Logger.getLogger(ShoalCacheLoggerConstants.CACHE_STATS);

    public CommandManager() {
    }

    public void initialize(DataStoreContext<K, V> dsc) {
        this.dsc = dsc;
        this.myName = dsc.getInstanceName();

        head = new CommandHandlerInterceptor<K, V>();
        head.initialize(dsc);

        tail = new TransmitInterceptor<K, V>();
        tail.initialize(dsc);

        head.setNext(tail);
        tail.setPrev(head);
    }

    public void registerCommand(Command command) {
        commands[command.getOpcode()] = command;
        command.initialize(dsc);
    }

    public synchronized void registerExecutionInterceptor(AbstractCommandInterceptor<K, V> interceptor) {
        interceptor.initialize(dsc);

        interceptor.setPrev(tail.getPrev());
        tail.getPrev().setNext(interceptor);

        interceptor.setNext(tail);
        tail.setPrev(interceptor);
    }

    public void execute(Command<K, V> cmd) throws DataStoreException {
        executeCommand(cmd, true, myName);
    }

    public final void executeCommand(Command<K, V> cmd, boolean forward, String initiator) throws DataStoreException {
        cmd.initialize(dsc);
        if (forward) {
            try {
                head.onTransmit(cmd, initiator);
                cmd.onSuccess();
            } catch (DataStoreException dseEx) {
                cmd.onFailure();
            }
        } else {
            tail.onReceive(cmd, initiator);
        }
    }

    @Override
    protected void handleMessage(String sourceMemberName, String token, byte[] messageData) {

        ObjectInputStream ois = null;
        ByteArrayInputStream bis = null;
        try {
            bis = new ByteArrayInputStream(messageData);
            ois = (dsc.getKeyTransformer() == null) ? new ObjectInputStreamWithLoader(bis, dsc.getClassLoader()) : new ObjectInputStream(bis);
            Command<K, V> cmd = (Command<K, V>) ois.readObject();
            if (_logger.isLoggable(Level.FINER)) {
                _logger.log(Level.FINER, dsc.getServiceName() + " RECEIVED " + cmd);
            }
            cmd.initialize(dsc);

            int receivedCount = dsc.getDataStoreMBean().incrementBatchReceivedCount();
            if (_statsLogger.isLoggable(Level.FINE)) {
                _statsLogger.log(Level.FINE, "Received message#  " + receivedCount + "  from " + sourceMemberName);
            }

            this.executeCommand(cmd, false, sourceMemberName);
        } catch (IOException dse) {
            _logger.log(Level.WARNING, "Error during parsing command: opcode: " + messageData[0], dse);
        } catch (Throwable th) {
            _logger.log(Level.WARNING, "Error[2] during parsing command: opcode: " + messageData[0], th);
        } finally {
            try {
                bis.close();
            } catch (Exception ex) {
                _logger.log(Level.FINEST, "Ignorable error while closing ByteArrayInputStream");
            }
            try {
                ois.close();
            } catch (Exception ex) {
                _logger.log(Level.FINEST, "Ignorable error while closing ObjectInputStream");
            }
        }
    }

    public void close() {
        for (AbstractCommandInterceptor<K, V> h = head; h != null; h = h.getNext()) {
            h.close();
        }
    }

}
