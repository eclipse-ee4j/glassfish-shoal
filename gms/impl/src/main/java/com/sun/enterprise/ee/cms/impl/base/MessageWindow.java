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

package com.sun.enterprise.ee.cms.impl.base;

import com.sun.enterprise.ee.cms.core.GMSConstants;
import com.sun.enterprise.ee.cms.core.GMSException;
import com.sun.enterprise.ee.cms.core.MessageSignal;
import com.sun.enterprise.ee.cms.impl.common.*;
import com.sun.enterprise.ee.cms.logging.GMSLogDomain;
import com.sun.enterprise.ee.cms.spi.GMSMessage;

import java.nio.charset.Charset;
import java.text.MessageFormat;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Iterator;

/**
 * Handles messages from the message queue and dispatches them to the interested parties. Also specially handles
 * messages sent for DistributedStateCacheImpl (the default implementation) for synchronization actions.
 *
 * @author Shreedhar Ganapathy Date: Jul 11, 2006
 * @version $Revision$
 */
public class MessageWindow implements Runnable {
	static private Logger logger = GMSLogDomain.getLogger(GMSLogDomain.GMS_LOGGER);
	private final Logger monitorLogger = GMSLogDomain.getMonitorLogger();
	private GMSContext ctx;
	private ArrayBlockingQueue<MessagePacket> messageQueue;
	private AtomicInteger messageQueueHighWaterMark = new AtomicInteger(0);
	private final String groupName;
	private final ExecutorService dscExecutor;

	public MessageWindow(final String groupName, final ArrayBlockingQueue<MessagePacket> messageQueue) {
		this.groupName = groupName;
		this.messageQueue = messageQueue;
		GMSThreadFactory gtf = new GMSThreadFactory("GMS-DistributedStateCache-Group-" + groupName + "-thread");
		this.dscExecutor = Executors.newSingleThreadExecutor(gtf);
	}

	void stop() {
		dscExecutor.shutdown();
	}

	private GMSContext getGMSContext() {
		if (ctx == null) {
			ctx = (GMSContext) GMSContextFactory.getGMSContext(groupName);
		}
		return ctx;
	}

	private void recordMessageQueueHighWaterMark() {
		if (monitorLogger.isLoggable(Level.FINE)) {
			int currentQueueSize = messageQueue.size();
			int localHighWater = messageQueueHighWaterMark.get();
			if (currentQueueSize > localHighWater) {
				messageQueueHighWaterMark.compareAndSet(localHighWater, currentQueueSize);
			}
		}
	}

	public void run() {
		while (!getGMSContext().isShuttingDown()) {
			try {
				recordMessageQueueHighWaterMark();
				final MessagePacket packet = messageQueue.take();
				if (packet != null) {
					if (logger.isLoggable(Level.FINER)) {
						logger.log(Level.FINER, "Processing received message .... " + packet.getMessage());
					}
					newMessageReceived(packet);
				}
			} catch (InterruptedException e) {
				logger.log(Level.FINE, e.getLocalizedMessage());
			} catch (Throwable t) {
				logger.log(Level.WARNING, "msg.wdw.exception.processing.msg", t);
			}
		}
		if (monitorLogger.isLoggable(Level.FINE)) {
			int msgQueueCapacity = (messageQueue == null ? 0 : messageQueue.remainingCapacity());
			monitorLogger.log(Level.FINE,
			        "message queue high water mark:" + messageQueueHighWaterMark.get() + " msg queue remaining capacity:" + msgQueueCapacity);
		}
		if (messageQueue != null && messageQueue.size() > 0) {
			int messageQueueSize = messageQueue.size();
			logger.log(Level.WARNING, "msg.wdw.thread.shutdown", new Object[] { groupName, messageQueueSize });
			if (messageQueueSize > 0 && logger.isLoggable(Level.FINER)) {
				Iterator<MessagePacket> mqIter = messageQueue.iterator();
				if (logger.isLoggable(Level.FINER)) {
					logger.finer("Dumping received but unprocessed messages for group: " + groupName);
				}
				while (mqIter.hasNext()) {
					MessagePacket mp = mqIter.next();
					Object message = mp.getMessage();
					String sender = mp.getAdvertisement().getName();
					if (message instanceof GMSMessage) {
						writeLog(sender, (GMSMessage) mp.getMessage());
					} else if (message instanceof DSCMessage && logger.isLoggable(Level.FINE)) {
						logger.log(Level.FINE, MessageFormat.format("Unprocessed DSCMessageReceived from :{0}, Operation :{1}", sender,
						        ((DSCMessage) message).getOperation()));
					}
				}
			}

		} else {
			logger.log(Level.INFO, "msg.wdw.thread.terminated", new Object[] { groupName });
		}
	}

	private void newMessageReceived(final MessagePacket packet) {
		final Object message = packet.getMessage();
		final SystemAdvertisement adv = packet.getAdvertisement();
		final String sender = adv.getName();

		if (message instanceof GMSMessage) {
			handleGMSMessage((GMSMessage) message, sender);
		} else if (message instanceof DSCMessage) {
			try {
				dscExecutor.submit(new ProcessDSCMessageTask(this, (DSCMessage) message, sender));
			} catch (RejectedExecutionException ree) {
				logger.log(Level.WARNING, "failed to schedule processDSCMessageTask for mesasge " + message);

			}
		}
	}

	private void handleDSCMessage(final DSCMessage dMsg, final String token) {
		final Logger DSCLogger = GMSLogDomain.getDSCLogger();
		if (ctx.isWatchdog()) {
			// Distributed State Cache is disabled for WATCHDOG member.
			return;
		}

		final String ops = dMsg.getOperation();
		if (DSCLogger.isLoggable(Level.FINE)) {
			DSCLogger.log(Level.FINE, MessageFormat.format("DSCMessageReceived from :{0}, Operation :{1}", token, ops));
		}
		final DistributedStateCacheImpl dsc = (DistributedStateCacheImpl) getGMSContext().getDistributedStateCache();
		if (ops.equals(DSCMessage.OPERATION.ADD.toString())) {
			if (DSCLogger.isLoggable(Level.FINE)) {
				DSCLogger.log(Level.FINE, "Adding Message: " + dMsg.getKey() + ":" + dMsg.getValue());
			}
			dsc.addToLocalCache(dMsg.getKey(), dMsg.getValue());
		} else if (ops.equals(DSCMessage.OPERATION.REMOVE.toString())) {
			if (DSCLogger.isLoggable(Level.FINE)) {
				DSCLogger.log(Level.FINE, "Removing Values with Key: " + dMsg.getKey());
			}
			dsc.removeFromLocalCache(dMsg.getKey());
		} else if (ops.equals(DSCMessage.OPERATION.ADDALLLOCAL.toString())) {
			if (dMsg.isCoordinator()) {
				try {
					DSCLogger.log(Level.FINE, "Syncing local cache with group ...");
					dsc.addAllToRemoteCache();
					DSCLogger.log(Level.FINE, "done with local to group sync...");
				} catch (GMSException e) {
					DSCLogger.log(Level.WARNING, e.getLocalizedMessage());
				}
				if (DSCLogger.isLoggable(Level.FINE)) {
					DSCLogger.log(Level.FINE, "adding group cache state to local cache..");
				}
				dsc.addAllToLocalCache(dMsg.getCache());
			}
		} else if (ops.equals(DSCMessage.OPERATION.ADDALLREMOTE.toString())) {
			dsc.addAllToLocalCache(dMsg.getCache());
			if (DSCLogger.isLoggable(Level.FINE)) {
				DSCLogger.log(Level.FINE, "Add All Remote from member:" + token + " dsc=" + dsc);
			}
		} // TODO: determine if the following is needed.
		/*
		 * else if( ops.equals( DSCMessage.OPERATION.REMOVEALL.toString()) ) { dsc.removeAllFromCache( dMsg. ); }
		 */
	}

	private void handleGMSMessage(final GMSMessage gMsg, final String sender) {
		if (gMsg.getComponentName() != null && gMsg.getComponentName().equals(GMSConstants.shutdownType.GROUP_SHUTDOWN.toString())) {
			final ShutdownHelper sh = GMSContextFactory.getGMSContext(gMsg.getGroupName()).getShutdownHelper();
			logger.log(Level.INFO, "member.groupshutdown", new Object[] { sender, groupName });
			sh.addToGroupShutdownList(gMsg.getGroupName());
			logger.log(Level.FINE, "setting clusterStopping variable to true");
			GMSContextFactory.getGMSContext(gMsg.getGroupName()).getGroupCommunicationProvider().setGroupStoppingState();
		} else {
			if (getRouter().isMessageAFRegistered()) {
				writeLog(sender, gMsg);
				final MessageSignal ms = new MessageSignalImpl(gMsg.getMessage(), gMsg.getComponentName(), sender, gMsg.getGroupName(), gMsg.getStartTime());
				final SignalPacket signalPacket = new SignalPacket(ms);
				getRouter().queueSignal(signalPacket);
			}
		}
	}

	private Router getRouter() {
		return getGMSContext().getRouter();
	}

	private void writeLog(final String sender, final com.sun.enterprise.ee.cms.spi.GMSMessage message) {
		final String localId = getGMSContext().getServerIdentityToken();
		if (logger.isLoggable(Level.FINER)) {
			logger.log(Level.FINER, MessageFormat.format("Sender:{0}, Receiver :{1}, TargetComponent :{2}, Message :{3}", sender, localId,
			        message.getComponentName(), new String(message.getMessage(), Charset.defaultCharset())));
		}
	}

	private static class ProcessDSCMessageTask implements Runnable {
		final private MessageWindow mw;
		final private DSCMessage dMsg;
		final private String fromMember;

		public ProcessDSCMessageTask(MessageWindow mw, final DSCMessage dMsg, final String token) {
			this.mw = mw;
			this.dMsg = dMsg;
			this.fromMember = token;
		}

		public void run() {
			try {
				mw.handleDSCMessage(dMsg, fromMember);
			} catch (Throwable t) {
				mw.logger.log(Level.SEVERE, "failed to handleDSCMessage", t);
			}
		}
	}
}
