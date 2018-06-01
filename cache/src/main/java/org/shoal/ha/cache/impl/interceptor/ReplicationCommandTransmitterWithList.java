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

package org.shoal.ha.cache.impl.interceptor;

import org.shoal.adapter.store.commands.NoOpCommand;
import org.shoal.ha.cache.api.DataStoreAlreadyClosedException;
import org.shoal.ha.cache.api.DataStoreContext;
import org.shoal.ha.cache.api.DataStoreException;
import org.shoal.ha.cache.api.ShoalCacheLoggerConstants;
import org.shoal.ha.cache.impl.command.Command;
import org.shoal.ha.cache.impl.command.ReplicationCommandOpcode;
import org.shoal.ha.cache.impl.util.ASyncReplicationManager;

import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Mahesh Kannan
 */
public class ReplicationCommandTransmitterWithList<K, V>
        implements Runnable, CommandCollector<K, V> {


    private static final Logger _logger =
            Logger.getLogger(ShoalCacheLoggerConstants.CACHE_TRANSMIT_INTERCEPTOR);

    private DataStoreContext<K, V> dsc;

    private volatile String targetName;

    private ScheduledFuture future;

    private static final String TRANSMITTER_FREQUECNCY_PROP_NAME = "org.shoal.cache.transmitter.frequency.in.millis";

    private static final String MAX_BATCH_SIZE_PROP_NAME = "org.shoal.cache.transmitter.max.batch.size";

    private static int TRANSMITTER_FREQUECNCY_IN_MILLIS = 100;

    private int MAX_BATCH_SIZE = 20;

    private AtomicReference<BatchedCommandListDataFrame> mapRef;

    ASyncReplicationManager asyncReplicationManager = ASyncReplicationManager._getInstance();

    private volatile long timeStamp = System.currentTimeMillis();

    ThreadPoolExecutor executor;

    private AtomicBoolean openStatus = new AtomicBoolean(true);

    private AtomicInteger activeBatchCount = new AtomicInteger(1);

    private CountDownLatch latch = new CountDownLatch(1);


    public void initialize(String targetName, DataStoreContext<K, V> rsInfo) {

        this.executor = ASyncReplicationManager._getInstance().getExecutorService();
        this.targetName = targetName;
        this.dsc = rsInfo;

        try {
            TRANSMITTER_FREQUECNCY_IN_MILLIS =
                    Integer.getInteger(System.getProperty(TRANSMITTER_FREQUECNCY_PROP_NAME,
                            ""+TRANSMITTER_FREQUECNCY_IN_MILLIS));
        } catch (Exception ex) {
            //Ignore
        }

        try {
            MAX_BATCH_SIZE =
                    Integer.getInteger(System.getProperty(MAX_BATCH_SIZE_PROP_NAME,
                            ""+MAX_BATCH_SIZE));
        } catch (Exception ex) {
            //Ignore
        }

        BatchedCommandListDataFrame batch = new BatchedCommandListDataFrame(openStatus.get());
        mapRef = new AtomicReference<BatchedCommandListDataFrame>(batch);


        future = asyncReplicationManager.getScheduledThreadPoolExecutor().scheduleAtFixedRate(this, TRANSMITTER_FREQUECNCY_IN_MILLIS,
                TRANSMITTER_FREQUECNCY_IN_MILLIS, TimeUnit.MILLISECONDS);
    }

    @Override
    public void close() {
        //We have a write lock here.
        // So no other request threads OR background thread are active
        try {

            //Mark this as closed to prevent new valid batches
            if (openStatus.compareAndSet(true, false)) {

                //First cancel the background task
                future.cancel(false);

                //Now flush all pending batched data
                if (_logger.isLoggable(Level.FINE)) {
                    _logger.log(Level.FINE, "(ReplicationCommandTransmitterWithList) BEGIN Flushing all batched data upon shutdown..."
                        + activeBatchCount.get() + " to be flushed...");
                }

                BatchedCommandListDataFrame closedBatch
                        = new BatchedCommandListDataFrame(false);
                BatchedCommandListDataFrame batch = mapRef.getAndSet(closedBatch);
                //Note that the above batch is a valid batch
                asyncReplicationManager.getExecutorService().submit(batch);
                dsc.getDataStoreMBean().incrementBatchSentCount();

                for (int loopCount = 0; loopCount < 5; loopCount++) {
                    if (activeBatchCount.get() > 0) {
                        try {
                            latch.await(5, TimeUnit.SECONDS);
                        } catch (InterruptedException inEx) {
                            //Ignore...
                        }
                    }
                }

                if (_logger.isLoggable(Level.FINE)) {
                    _logger.log(Level.FINE, "(ReplicationCommandTransmitterWithList) DONE Flushing all batched data upon shutdown...");
                }
            }
        } catch (Exception ex) {
            //Ignore
        }
    }

    public void addCommand(Command<K, V> cmd)
        throws DataStoreException {

        for (boolean done = false; !done;) {
            BatchedCommandListDataFrame batch = mapRef.get();
            done = batch.addCommand(cmd);
            if (!done) {
                BatchedCommandListDataFrame frame = new BatchedCommandListDataFrame(openStatus.get());
                frame.addCommand(cmd);
                done = mapRef.compareAndSet(batch, frame);
                if (done && frame.isValidBatch()) {
                    activeBatchCount.incrementAndGet();
                }
            }
        }
    }

    @Override
    public void removeCommand(Command<K, V> cmd)
        throws DataStoreException {
        addCommand(cmd);
    }

    public void run() {
        try {
            dsc.acquireReadLock();
            BatchedCommandListDataFrame batch = mapRef.get();
            //Since this called by a async thread
            //   OR upon close, it is OK to not rethrow the exceptions
            if (batch.isTimeToFlush(timeStamp) || (! openStatus.get())) {
                NoOpCommand noop = new NoOpCommand();
                while (batch.addCommand(noop)) {
                    ;
                }
            }
            timeStamp = batch.getBatchCreationTime();
        } catch (DataStoreAlreadyClosedException dsEx) {
            //Ignore....
        } catch (DataStoreException dsEx) {
            _logger.log(Level.WARNING, "Error during flush...");
        } finally {
            dsc.releaseReadLock();
        }
    }

    private class BatchedCommandListDataFrame
            implements Runnable {

        private AtomicInteger current = new AtomicInteger(-1);

        private transient ConcurrentLinkedQueue<Command> list = new ConcurrentLinkedQueue<Command>();

        private long batchCreationTime = System.currentTimeMillis();

        private boolean validBatch;

        BatchedCommandListDataFrame(boolean valid) {
            this.validBatch = valid;
        }

        private boolean isValidBatch() {
            return validBatch;
        }

        public boolean addCommand(Command cmd)
            throws DataStoreException {
            if (! validBatch) {
                throw new DataStoreAlreadyClosedException("Cannot add a command to a Batch after the DataStore has been closed");
            }

            int value = current.incrementAndGet();
            if (value < MAX_BATCH_SIZE) {
                list.add(cmd);
                if (list.size() == MAX_BATCH_SIZE) {
                  asyncReplicationManager.getExecutorService().submit(this);
                }
            }

            return value < MAX_BATCH_SIZE;
        }

        //Called by periodic task
        boolean isTimeToFlush(long timeStamp) {
            return batchCreationTime == timeStamp && list.size() > 0;
        }

        long getBatchCreationTime() {
            return batchCreationTime;
        }

        public void run() {

            ReplicationFramePayloadCommand rfCmd = new ReplicationFramePayloadCommand();
            rfCmd.setTargetInstance(targetName);
            try {
                int size = list.size();
                for (int i = 0; i < size; i++) {
                    Command cmd = list.poll();
                    if (cmd.getOpcode() != ReplicationCommandOpcode.NOOP_COMMAND) {
                        rfCmd.addComamnd(cmd);
                    }
                }

                dsc.getCommandManager().execute(rfCmd);
            } catch (IOException ioEx) {
                _logger.log(Level.WARNING, "Batch operation (ASyncCommandList failed...", ioEx);
            } finally {
                //We want to decrement only if we transmitted a valid batch
                //   Otherwise we should not decrement the activeBatchCount.
                //  Also, we decrement even if there was an IOException
                if (validBatch && activeBatchCount.decrementAndGet() <= 0) {
                    if (! openStatus.get()) {
                        latch.countDown();
                    }
                }

                if (_logger.isLoggable(Level.FINE)) {
                    _logger.log(Level.FINE, "(ReplicationCommandTransmitterWithList) Completed one batch. Still "
                        + activeBatchCount.get() + " to be flushed...");
                }
            }
        }
    }
}
