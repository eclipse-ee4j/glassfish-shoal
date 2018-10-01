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

package com.sun.enterprise.ee.cms.impl.common;

import java.lang.management.ManagementFactory;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import javax.management.StandardMBean;

import com.sun.enterprise.ee.cms.core.ServiceProviderConfigurationKeys;
import com.sun.enterprise.ee.cms.impl.base.Utility;
import com.sun.enterprise.ee.cms.logging.GMSLogDomain;

/**
 * Lightweight monitoring solution.
 *
 * One per gms group.
 *
 * Candidate to evolve into GMBAL Managaged Object in future.
 */
public class GMSMonitor {
	private static final Logger logger = GMSLogDomain.getLogger(GMSLogDomain.GMS_LOGGER);
	private static final Logger monitorLogger = GMSLogDomain.getMonitorLogger();

	// todo: fix this. hack to control this by logging.
	public final boolean ENABLED;
	public static final long ENABLED_DEFAULT = -1;

	private Timer timer = null;
	private long REPORT_DURATION = 5; // seconds
	private long sendWriteTimeout = Long.MAX_VALUE; // default to no timeout.
	final public MBeanServer mbs;
	final public String instanceName;
	final public String groupName;

	public GMSMonitor(String instanceName, String groupName, Properties props) {
		this.instanceName = instanceName;
		this.groupName = groupName;
		REPORT_DURATION = Utility.getLongProperty(ServiceProviderConfigurationKeys.MONITORING.toString(), ENABLED_DEFAULT, props);
		boolean enableMonitoring = REPORT_DURATION < 0 ? false : true;
		if (REPORT_DURATION != ENABLED_DEFAULT && logger.isLoggable(Level.CONFIG)) {
			logger.config("GMSMonitor ENABLED: " + enableMonitoring);
		}
		this.ENABLED = enableMonitoring;
		if (this.ENABLED) {
			if (REPORT_DURATION != 0) {
				timer = new Timer(true);
				timer.scheduleAtFixedRate(new Report(this), REPORT_DURATION * 1000, REPORT_DURATION * 1000);
			}
			mbs = ManagementFactory.getPlatformMBeanServer();
		} else {
			mbs = null;
		}
	}

	public void setSendWriteTimeout(long value) {
		sendWriteTimeout = value;
	}

	public long getSendWriteTimeout() {
		return sendWriteTimeout;
	}

	public void report() {
		if (ENABLED) {
			for (MessageStats stat : gmsMsgStats.values()) {
				logger.log(Level.INFO, "gmsMonitor: " + stat.toString());
			}
		}
	}

	// map from GMS Message target component to MessageStats
	private final ConcurrentHashMap<String, MessageStats> gmsMsgStats = new ConcurrentHashMap<String, MessageStats>();

	public MessageStats getGMSMessageMonitorStats(String targetComponent) {
		MessageStats result = gmsMsgStats.get(targetComponent);

		// initialization step. Ensures that one and only one entry will be added for a targetComponent.
		if (result == null) {
			MessageStats newEntry = new MessageStats(targetComponent, this);
			result = gmsMsgStats.putIfAbsent(targetComponent, newEntry);
			// returns null if targetComponent was not in hash.
			if (result == null) {
				result = newEntry;

				// register new mbean.
				result.register();
			}
		}
		return result;
	}

	public void closeGMSMessageMonitorStats(String targetComponent) {
		MessageStats closeEntry = gmsMsgStats.remove(targetComponent);
		if (closeEntry != null) {
			closeEntry.close();
		}
	}

	private AtomicLong maxIncomingMessageQueueSize = new AtomicLong(0);

	public interface GMSMessageStatsMBean {
		long getNumFailMsgSend();

		long incrementNumFailMsgSend();

		long getSendWriteTimeouts();

		long incrementSendWriteTimeout();

		long getNumMsgsSent();

		long incrementNumMsgsSent();

		long getBytesSent();

		long addBytesSent(long bytesSent);

		long getSendDuration();

		long addSendDuration(long duration);

		long getMaxSendDuration();

		void setMaxSendDuration(long duration);

		long getReceiveDuration();

		long addReceiveDuration(long duration);

		long getMaxReceiveDuration();

		void setMaxReceiveDuration(long duration);

		long getNumMsgsReceived();

		long incrementNumMsgsReceived();

		long getBytesReceived();

		long addBytesReceived(long bytesReceived);

		long getNumMsgsNoListener();

		long incrementNumMsgsNoHandler();
	}

	static public class MessageStats implements GMSMessageStatsMBean {
		final private String targetComponent;
		final private GMSMonitor gmsMonitor;
		private MBeanServer mbs = null;
		private ObjectName mbeanObjectName = null;

		private AtomicLong numMsgsSent = new AtomicLong(0);
		private AtomicLong bytesSent = new AtomicLong(0);
		private AtomicLong sendTime = new AtomicLong(0);
		private AtomicLong maxSendTime = new AtomicLong(0);
		private AtomicLong failMsgSend = new AtomicLong(0);
		private AtomicLong writeTimeoutMsgSend = new AtomicLong(0);

		private AtomicLong numMsgsReceived = new AtomicLong(0);
		private AtomicLong bytesReceived = new AtomicLong(0);
		private AtomicLong numMsgsNoListener = new AtomicLong(0);
		private AtomicLong receiveTime = new AtomicLong(0);
		private AtomicLong maxReceiveTime = new AtomicLong(0);

		private static final Logger _logger = GMSLogDomain.getMonitorLogger();

		public MessageStats(String component, GMSMonitor gmsMonitor) {
			targetComponent = component;
			this.gmsMonitor = gmsMonitor;
			this.mbs = gmsMonitor.mbs;
		}

		public void register() {
			if (mbs != null) {

				try {
					mbeanObjectName = new ObjectName("com.sun.enterprise.ee.cms.impl.common.GMSMonitor.MessageStats" + ":name=" + gmsMonitor.groupName + "_"
					        + gmsMonitor.instanceName + "_" + targetComponent);

					gmsMonitor.mbs.registerMBean(new StandardMBean(this, GMSMessageStatsMBean.class), mbeanObjectName);
				} catch (MalformedObjectNameException malEx) {
					_logger.log(Level.INFO, "Couldn't register MBean for " + targetComponent + " : " + malEx);
				} catch (InstanceAlreadyExistsException malEx) {
					_logger.log(Level.INFO, "Couldn't register MBean for " + targetComponent + " : " + malEx);
				} catch (MBeanRegistrationException malEx) {
					_logger.log(Level.INFO, "Couldn't register MBean for " + targetComponent + " : " + malEx);
				} catch (NotCompliantMBeanException malEx) {
					_logger.log(Level.INFO, "Couldn't register MBean for " + targetComponent + " : " + malEx);
				}
			}
		}

		public void close() {
			if (mbs != null && mbeanObjectName != null) {
				try {
					mbs.unregisterMBean(mbeanObjectName);
					mbeanObjectName = null;
					mbs = null;
				} catch (Exception e) {
				}
			}
		}

		public String toString() {
			StringBuffer sb = new StringBuffer();
			sb.append("targetComponent:").append(targetComponent);
			sb.append(" Send:[numMsgs:").append(numMsgsSent.get());
			if (numMsgsSent.get() != 0) {
				sb.append(" totalBytes:").append(bytesSent.get());
				sb.append(" avgMsgSize:").append(bytesSent.get() / numMsgsSent.get());
				sb.append(" totalTime:").append(sendTime.get());
				sb.append(" avg time:").append(sendTime.get() / numMsgsSent.get());
				sb.append(" max time:").append(maxSendTime.get());
			}
			if (writeTimeoutMsgSend.get() != 0) {
				sb.append(" write timeout:").append(writeTimeoutMsgSend.get());
			}
			if (failMsgSend.get() != 0) {
				sb.append(" write fail:").append(failMsgSend.get());
			}
			sb.append("]");
			sb.append(" Receive:[numMsgs:").append(numMsgsReceived);
			if (numMsgsReceived.get() != 0) {
				sb.append(" totalBytes:").append(bytesReceived);
				sb.append(" avgMsgSize:").append(bytesReceived.get() / numMsgsReceived.get());
				sb.append(" totalTime:").append(receiveTime);
				sb.append(" avg time:").append(receiveTime.get() / numMsgsReceived.get());
				sb.append(" max time:").append(maxReceiveTime);
			}
			if (numMsgsNoListener.get() != 0) {
				sb.append(" numMsgNoListener:").append(numMsgsNoListener);
			}
			sb.append("]");
			return sb.toString();
		}

		public long getNumFailMsgSend() {
			return failMsgSend.get();
		}

		public long incrementNumFailMsgSend() {
			long result = this.failMsgSend.incrementAndGet();
			if (result < 0) {
				// wrapped. reset to zero again.
				this.failMsgSend.set(1);
				result = 1;
			}
			return result;
		}

		public long getSendWriteTimeouts() {
			return writeTimeoutMsgSend.get();
		}

		public long incrementSendWriteTimeout() {
			long result = this.writeTimeoutMsgSend.incrementAndGet();
			if (result < 0) {
				// wrapped. reset to zero again.
				this.writeTimeoutMsgSend.set(1);
				result = 1;
			}
			return result;
		}

		public long getNumMsgsSent() {
			return numMsgsSent.get();
		}

		public long incrementNumMsgsSent() {
			long result = this.numMsgsSent.incrementAndGet();
			if (result < 0) {
				// wrapped. reset to zero again.
				this.numMsgsSent.set(1);
				result = 1;
			}
			return result;
		}

		public long getBytesSent() {
			return bytesSent.get();
		}

		public long addBytesSent(long bytesSent) {
			long result = this.bytesSent.addAndGet(bytesSent);
			if (result < 0) {
				// wrapped. reset to zero and start over.
				this.bytesSent.set(bytesSent);
				result = bytesSent;
			}
			return result;
		}

		public long getSendDuration() {
			return sendTime.get();
		}

		public long addSendDuration(long duration) {
			long result = this.sendTime.addAndGet(duration);
			if (result < 0) {
				// wrapped. reset to zero and start over.
				this.sendTime.set(duration);
				result = duration;
			}
			setMaxSendDuration(duration);
			return result;
		}

		public long getMaxSendDuration() {
			return maxSendTime.get();
		}

		public void setMaxSendDuration(long duration) {
			long localMaxSendTime = maxSendTime.get();
			if (duration > localMaxSendTime) {
				boolean result = maxSendTime.compareAndSet(localMaxSendTime, duration);
				if (!result) {
					// try again. another thread already set to a different value.
					setMaxSendDuration(duration);
				}
			}
		}

		public long getReceiveDuration() {
			return receiveTime.get();
		}

		public long addReceiveDuration(long duration) {
			long result = this.receiveTime.addAndGet(duration);
			if (result < 0) {
				// wrapped. reset to zero and start over.
				this.receiveTime.set(duration);
				result = duration;
			}
			setMaxReceiveDuration(duration);
			return result;
		}

		public long getMaxReceiveDuration() {
			return maxReceiveTime.get();
		}

		public void setMaxReceiveDuration(long duration) {
			long localMaxReceiveTime = maxReceiveTime.get();
			if (duration > localMaxReceiveTime) {
				boolean result = maxReceiveTime.compareAndSet(localMaxReceiveTime, duration);
				if (!result) {
					// try again. another thread already set to a different value.
					setMaxReceiveDuration(duration);
				}
			}
		}

		public long getNumMsgsReceived() {
			return numMsgsReceived.get();
		}

		public long incrementNumMsgsReceived() {
			long result = this.numMsgsReceived.incrementAndGet();
			if (result < 0) {
				// wrapped. reset to zero again.
				this.numMsgsReceived.set(1);
				result = 1;
			}
			return result;
		}

		public long getBytesReceived() {
			return bytesReceived.get();
		}

		public long addBytesReceived(long bytesReceived) {
			long result = this.bytesReceived.addAndGet(bytesReceived);
			if (result < 0) {
				// wrapped. reset to zero and start over.
				this.bytesReceived.set(bytesReceived);
				result = bytesReceived;
			}
			return result;
		}

		public long getNumMsgsNoListener() {
			return numMsgsNoListener.get();
		}

		public long incrementNumMsgsNoHandler() {
			long result = this.numMsgsNoListener.incrementAndGet();
			if (result < 0) {
				// wrapped. reset to zero again.
				this.numMsgsNoListener.set(1);
				result = 1;
			}
			return result;
		}
	}

	public void stop() {
		if (timer != null) {
			timer.cancel();
		}
		if (ENABLED) {
			report();
		}
		for (MessageStats componentMsgStats : gmsMsgStats.values()) {
			componentMsgStats.close();
		}
	}

	public static class Report extends TimerTask {
		private final GMSMonitor monitor;

		public Report(GMSMonitor gmsMonitor) {
			monitor = gmsMonitor;
		}

		public void run() {
			if (monitor.ENABLED) {
				monitor.report();
			}
		}
	}
}
