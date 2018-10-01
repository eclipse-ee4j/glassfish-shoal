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
import org.shoal.adapter.store.commands.SaveCommand;
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
public class ReplicationCommandTransmitterWithMap<K, V> implements Runnable, CommandCollector<K, V> {

	private static final Logger _logger = Logger.getLogger(ShoalCacheLoggerConstants.CACHE_TRANSMIT_INTERCEPTOR);

	private static final Logger _statsLogger = Logger.getLogger(ShoalCacheLoggerConstants.CACHE_STATS);

	private DataStoreContext<K, V> dsc;

	private volatile String targetName;

	private ScheduledFuture future;

	private static final String TRANSMITTER_FREQUECNCY_PROP_NAME = "org.shoal.cache.transmitter.frequency.in.millis";

	private static final String MAX_BATCH_SIZE_PROP_NAME = "org.shoal.cache.transmitter.max.batch.size";

	private static int TRANSMITTER_FREQUECNCY_IN_MILLIS = 100;

	private static int MAX_BATCH_SIZE = 30;

	private AtomicReference<BatchedCommandMapDataFrame> mapRef;

	ASyncReplicationManager asyncReplicationManager = ASyncReplicationManager._getInstance();

	private long timeStamp = System.currentTimeMillis();

	ThreadPoolExecutor executor;

	private AtomicBoolean openStatus = new AtomicBoolean(true);

	private AtomicInteger activeBatchCount = new AtomicInteger(1);

	private CountDownLatch latch = new CountDownLatch(1);

	static {
		try {
			TRANSMITTER_FREQUECNCY_IN_MILLIS = Integer.valueOf(System.getProperty(TRANSMITTER_FREQUECNCY_PROP_NAME, "" + TRANSMITTER_FREQUECNCY_IN_MILLIS));
			_statsLogger.log(Level.CONFIG, "USING " + TRANSMITTER_FREQUECNCY_PROP_NAME + " = " + TRANSMITTER_FREQUECNCY_IN_MILLIS);
		} catch (Exception ex) {
			_statsLogger.log(Level.CONFIG, "USING " + TRANSMITTER_FREQUECNCY_PROP_NAME + " = " + TRANSMITTER_FREQUECNCY_IN_MILLIS);
		}

		try {
			MAX_BATCH_SIZE = Integer.valueOf(System.getProperty(MAX_BATCH_SIZE_PROP_NAME, "" + MAX_BATCH_SIZE));
			_statsLogger.log(Level.CONFIG, "USING " + MAX_BATCH_SIZE_PROP_NAME + " = " + MAX_BATCH_SIZE);
		} catch (Exception ex) {
			_statsLogger.log(Level.CONFIG, "USING " + MAX_BATCH_SIZE_PROP_NAME + " = " + MAX_BATCH_SIZE);
		}

		_logger.log(Level.FINE, "USING ReplicationCommandTransmitterWithMap");
	}

	@Override
	public void initialize(String targetName, DataStoreContext<K, V> rsInfo) {

		this.executor = ASyncReplicationManager._getInstance().getExecutorService();
		this.targetName = targetName;
		this.dsc = rsInfo;

		BatchedCommandMapDataFrame batch = new BatchedCommandMapDataFrame(openStatus.get());
		mapRef = new AtomicReference<BatchedCommandMapDataFrame>(batch);

		future = asyncReplicationManager.getScheduledThreadPoolExecutor().scheduleAtFixedRate(this, TRANSMITTER_FREQUECNCY_IN_MILLIS,
		        TRANSMITTER_FREQUECNCY_IN_MILLIS, TimeUnit.MILLISECONDS);
	}

	@Override
	public void close() {
		// We have a write lock here.
		// So no other request threads OR background thread are active
		try {

			// Mark this as closed to prevent new valid batches
			if (openStatus.compareAndSet(true, false)) {

				// First cancel the background task
				future.cancel(false);

				// Now flush all pending batched data
				if (_logger.isLoggable(Level.FINE)) {
					_logger.log(Level.FINE, "(ReplicationCommandTransmitterWithMap) BEGIN Flushing all batched data upon shutdown..." + activeBatchCount.get()
					        + " to be flushed...");
				}

				BatchedCommandMapDataFrame closedBatch = new BatchedCommandMapDataFrame(false);
				BatchedCommandMapDataFrame batch = mapRef.getAndSet(closedBatch);
				// Note that the above batch is a valid batch
				asyncReplicationManager.getExecutorService().submit(batch);
				dsc.getDataStoreMBean().incrementBatchSentCount();

				for (int loopCount = 0; loopCount < 5; loopCount++) {
					if (activeBatchCount.get() > 0) {
						try {
							latch.await(5, TimeUnit.SECONDS);
						} catch (InterruptedException inEx) {
							// Ignore...
						}
					}
				}

				if (_logger.isLoggable(Level.FINE)) {
					_logger.log(Level.FINE, "(ReplicationCommandTransmitterWithMap) DONE Flushing all batched data upon shutdown...");
				}
			}
		} catch (Exception ex) {
			// Ignore
		}
	}

	@Override
	public void addCommand(Command<K, V> cmd) throws DataStoreException {
		addCommandToBatch(cmd, true);
	}

	@Override
	public void removeCommand(Command<K, V> cmd) throws DataStoreException {
		addCommandToBatch(cmd, false);
	}

	private void addCommandToBatch(Command<K, V> cmd, boolean isAdd) throws DataStoreException {
		for (boolean done = false; !done;) {
			BatchedCommandMapDataFrame batch = mapRef.get();
			done = batch.doAddOrRemove(cmd, isAdd);
			if (!done) {
				BatchedCommandMapDataFrame frame = new BatchedCommandMapDataFrame(openStatus.get());
				frame.doAddOrRemove(cmd, isAdd);
				done = mapRef.compareAndSet(batch, frame);
				if (done && frame.isValid()) {
					activeBatchCount.incrementAndGet();
				}
			}
		}
	}

	public void run() {
		try {
			dsc.acquireReadLock();
			if (openStatus.get()) {
				BatchedCommandMapDataFrame batch = mapRef.get();

				// Since this called by a async thread
				// OR upon close, it is OK to not rethrow the exceptions
				batch.flushAndTransmit();
			}
		} catch (DataStoreAlreadyClosedException dsEx) {
			// Ignore....
		} catch (DataStoreException dsEx) {
			_logger.log(Level.WARNING, "Error during flush...");
		} finally {
			dsc.releaseReadLock();
		}
	}

	private static AtomicInteger _sendBatchCount = new AtomicInteger(0);

	private class BatchedCommandMapDataFrame implements Runnable {

		private int myBatchNumber;

		private AtomicInteger inFlightCount = new AtomicInteger(0);

		private AtomicBoolean batchThresholdReached = new AtomicBoolean(false);

		private AtomicBoolean alreadySent = new AtomicBoolean(false);

		private volatile ConcurrentHashMap<Object, ConcurrentLinkedQueue<Command>> map = new ConcurrentHashMap<Object, ConcurrentLinkedQueue<Command>>();

		private AtomicInteger removedKeysSize = new AtomicInteger(0);

		private volatile ConcurrentLinkedQueue removedKeys = new ConcurrentLinkedQueue();

		private volatile long lastTS = System.currentTimeMillis();

		private boolean validBatch;

		BatchedCommandMapDataFrame(boolean validBatch) {
			this.validBatch = validBatch;
			myBatchNumber = _sendBatchCount.incrementAndGet();
		}

		private boolean isValid() {
			return validBatch;
		}

		private boolean doAddOrRemove(Command cmd, boolean isAdd) throws DataStoreException {

			if (!validBatch) {
				throw new DataStoreAlreadyClosedException("Cannot add a command to a Batch after the DataStore has been closed");
			}
			boolean result = false;
			if (!batchThresholdReached.get()) {

				int inCount = 0;
				try {
					inFlightCount.incrementAndGet();
					if (!batchThresholdReached.get()) {
						if (isAdd) {
							ConcurrentLinkedQueue<Command> cmdList = map.get(cmd.getKey());
							if (cmdList == null) {
								cmdList = new ConcurrentLinkedQueue<Command>();
								ConcurrentLinkedQueue<Command> cmdList1 = map.putIfAbsent(cmd.getKey(), cmdList);
								cmdList = cmdList1 != null ? cmdList1 : cmdList;
							}

							cmdList.add(cmd);
							result = true;
							if (map.size() >= MAX_BATCH_SIZE) {
								batchThresholdReached.compareAndSet(false, true);
							}
						} else {
							map.remove(cmd.getKey());
							removedKeys.add(cmd.getKey());
							int removedSz = removedKeysSize.incrementAndGet();
							result = true;
							if (removedSz >= (2 * MAX_BATCH_SIZE)) {
								batchThresholdReached.compareAndSet(false, true);
							}
						}

					}
				} finally {
					inCount = inFlightCount.decrementAndGet();
				}

				if (batchThresholdReached.get() && inCount == 0 && alreadySent.compareAndSet(false, true)) {
					if (_statsLogger.isLoggable(Level.FINE)) {
						_statsLogger.log(Level.FINE,
						        "doAddOrRemove batchThresholdReached.get()=" + batchThresholdReached.get() + "; inFlightCount = " + inCount + "; ");

						_statsLogger.log(Level.FINE, "Sending batch# " + myBatchNumber + " to " + targetName + "; wasActive for ("
						        + (System.currentTimeMillis() - lastTS) + " millis");
					}
					asyncReplicationManager.getExecutorService().submit(this);
					dsc.getDataStoreMBean().incrementBatchSentCount();
				}
			}

			return result;
		}

		// Called by periodic task
		void flushAndTransmit() throws DataStoreException {
			dsc.getDataStoreMBean().incrementFlushThreadWakeupCount();
			if ((!alreadySent.get()) && ((map.size() > 0) || (removedKeysSize.get() > 0))) {
				if (lastTS == timeStamp) {
					if (_statsLogger.isLoggable(Level.FINE)) {
						_statsLogger.log(Level.FINE, "flushAndTransmit will flush data because lastTS = " + lastTS + "; timeStamp = " + timeStamp
						        + "; lastTS = " + lastTS + "; map.size() = " + map.size() + "; removedKeys.size() = " + removedKeysSize.get());
					}

					NoOpCommand nc = null;
					do {
						nc = new NoOpCommand();
					} while (doAddOrRemove(nc, true));
					dsc.getDataStoreMBean().incrementFlushThreadFlushedCount();
				} else {
					if (_statsLogger.isLoggable(Level.FINER)) {
						_statsLogger.log(Level.FINER, "flushAndTransmit will NOT flush data because lastTS = " + lastTS + "; timeStamp = " + timeStamp
						        + "; lastTS = " + lastTS + "; map.size() = " + map.size() + "; removedKeys.size() = " + removedKeysSize.get());
					}
					timeStamp = lastTS;
				}
			} else {
//                if (_statsLogger.isLoggable(Level.FINEST)) {
//                    _statsLogger.log(Level.FINEST, "flushAndTransmit visited a new Batch");
//                }
			}
		}

		public void run() {
			try {
				ReplicationFramePayloadCommand rfCmd = new ReplicationFramePayloadCommand();
				rfCmd.setTargetInstance(targetName);
				try {
					for (ConcurrentLinkedQueue<Command> cmdList : map.values()) {
						SaveCommand saveCmd = null;
						for (Command cmd : cmdList) {
							if (cmd.getOpcode() == ReplicationCommandOpcode.NOOP_COMMAND) {
								// No need to add the noop commands
							} else if (cmd.getOpcode() == ReplicationCommandOpcode.SAVE) {
								SaveCommand thisSaveCommand = (SaveCommand) cmd;
								if (saveCmd == null || saveCmd.getVersion() < thisSaveCommand.getVersion()) {
									saveCmd = thisSaveCommand;
								}
							} else {
								// Commands like Load{Requests|Response} Touch etc.
								rfCmd.addComamnd(cmd);
							}
						}

						if (saveCmd != null) {
							rfCmd.addComamnd(saveCmd);
						}
					}

					rfCmd.setRemovedKeys(removedKeys);
					dsc.getCommandManager().execute(rfCmd);

				} catch (IOException ioEx) {
					_logger.log(Level.WARNING, "Batch operation (ASyncCommandList failed...", ioEx);
				}
			} finally {
				// We want to decrement only if we transmitted a valid batch
				// Otherwise we should not decrement the activeBatchCount.
				// Also, we decrement even if there was an IOException
				if (validBatch && activeBatchCount.decrementAndGet() <= 0) {
					if (!openStatus.get()) {
						latch.countDown();
					}
				}

				if (_logger.isLoggable(Level.FINE)) {
					_logger.log(Level.FINE,
					        "(ReplicationCommandTransmitterWithMap) Completed one batch. Still " + activeBatchCount.get() + " to be flushed...");
				}
			}
		}

	}
}
