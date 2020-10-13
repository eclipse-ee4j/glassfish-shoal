/*
 * Copyright (c) 1997, 2018 Oracle and/or its affiliates. All rights reserved.
 * COpyright (c) 2020 Payara Services Ltd.
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

import java.text.MessageFormat;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.enterprise.ee.cms.core.Action;
import com.sun.enterprise.ee.cms.core.ActionException;
import com.sun.enterprise.ee.cms.core.FailureNotificationAction;
import com.sun.enterprise.ee.cms.core.FailureNotificationActionFactory;
import com.sun.enterprise.ee.cms.core.FailureNotificationSignal;
import com.sun.enterprise.ee.cms.core.FailureRecoveryAction;
import com.sun.enterprise.ee.cms.core.FailureRecoveryActionFactory;
import com.sun.enterprise.ee.cms.core.FailureRecoverySignal;
import com.sun.enterprise.ee.cms.core.FailureSuspectedAction;
import com.sun.enterprise.ee.cms.core.FailureSuspectedActionFactory;
import com.sun.enterprise.ee.cms.core.FailureSuspectedSignal;
import com.sun.enterprise.ee.cms.core.GroupLeadershipNotificationAction;
import com.sun.enterprise.ee.cms.core.GroupLeadershipNotificationActionFactory;
import com.sun.enterprise.ee.cms.core.GroupLeadershipNotificationSignal;
import com.sun.enterprise.ee.cms.core.GroupManagementService;
import com.sun.enterprise.ee.cms.core.JoinNotificationAction;
import com.sun.enterprise.ee.cms.core.JoinNotificationActionFactory;
import com.sun.enterprise.ee.cms.core.JoinNotificationSignal;
import com.sun.enterprise.ee.cms.core.JoinedAndReadyNotificationAction;
import com.sun.enterprise.ee.cms.core.JoinedAndReadyNotificationActionFactory;
import com.sun.enterprise.ee.cms.core.JoinedAndReadyNotificationSignal;
import com.sun.enterprise.ee.cms.core.MessageAction;
import com.sun.enterprise.ee.cms.core.MessageActionFactory;
import com.sun.enterprise.ee.cms.core.MessageSignal;
import com.sun.enterprise.ee.cms.core.PlannedShutdownAction;
import com.sun.enterprise.ee.cms.core.PlannedShutdownActionFactory;
import com.sun.enterprise.ee.cms.core.PlannedShutdownSignal;
import com.sun.enterprise.ee.cms.core.Signal;
import com.sun.enterprise.ee.cms.impl.base.GMSThreadFactory;
import com.sun.enterprise.ee.cms.logging.GMSLogDomain;

/**
 * Routes signals to appropriate destinations
 *
 * @author Shreedhar Ganapathy Date: Jan 16, 2004
 * @version $Revision$
 */
public class Router {
    private final CopyOnWriteArrayList<FailureNotificationActionFactory> failureNotificationAF = new CopyOnWriteArrayList<FailureNotificationActionFactory>();

    private final ConcurrentHashMap<String, FailureRecoveryActionFactory> failureRecoveryAF = new ConcurrentHashMap<String, FailureRecoveryActionFactory>();

    private final ConcurrentHashMap<String, MessageActionFactory> messageAF = new ConcurrentHashMap<String, MessageActionFactory>();

    private final CopyOnWriteArrayList<PlannedShutdownActionFactory> plannedShutdownAF = new CopyOnWriteArrayList<PlannedShutdownActionFactory>();

    private final CopyOnWriteArrayList<JoinNotificationActionFactory> joinNotificationAF = new CopyOnWriteArrayList<JoinNotificationActionFactory>();

    private final CopyOnWriteArrayList<JoinedAndReadyNotificationActionFactory> joinedAndReadyNotificationAF = new CopyOnWriteArrayList<JoinedAndReadyNotificationActionFactory>();

    private final CopyOnWriteArrayList<FailureSuspectedActionFactory> failureSuspectedAF = new CopyOnWriteArrayList<FailureSuspectedActionFactory>();

    private final CopyOnWriteArrayList<GroupLeadershipNotificationActionFactory> groupLeadershipNotificationAFs = new CopyOnWriteArrayList<GroupLeadershipNotificationActionFactory>();

    private final BlockingQueue<SignalPacket> queue;
    private AtomicInteger queueHighWaterMark = new AtomicInteger(0);
    private final Logger logger = GMSLogDomain.getLogger(GMSLogDomain.GMS_LOGGER);
    private final Logger handlerLogger = GMSLogDomain.getHandlerLogger();
    private final Logger monitorLogger = GMSLogDomain.getMonitorLogger();
    private final ExecutorService actionPool;
    private final ExecutorService messageActionPool;
    private long startupTime;
    private static final int GROUP_WARMUP_TIME = 30000; // join notification remains in queue for this amount of time when there is no Join handler registered
                                                        // yet.
    private final int MAX_QUEUE_SIZE; // used to be 100, now it is set relative to size of msg queue.
    final private Thread signalHandlerThread;
    private SignalHandler signalHandler;
    public final AliveAndReadyViewWindow aliveAndReadyView;
    public final String groupName;
    private final GMSMonitor gmsMonitor;
    private final boolean isSpectator;

    public Router(String groupName, int queueSize, AliveAndReadyViewWindow viewWindow, int incomingMsgThreadPoolSize, GMSMonitor gmsMonitor) {
        this.groupName = groupName;
        aliveAndReadyView = viewWindow;
        MAX_QUEUE_SIZE = queueSize;
        queue = new ArrayBlockingQueue<SignalPacket>(MAX_QUEUE_SIZE);
        signalHandler = new SignalHandler(queue, this);
        signalHandlerThread = new Thread(signalHandler, "GMS SignalHandler for Group-" + groupName + " thread");
        signalHandlerThread.setDaemon(true);
        signalHandlerThread.start();
        GMSThreadFactory tf = new GMSThreadFactory("GMS-processNotify-Group-" + groupName + "-thread");
        actionPool = Executors.newFixedThreadPool(5, tf);
        tf = new GMSThreadFactory("GMS-processInboundMsg-Group-" + groupName + "-thread");

        messageActionPool = Executors.newFixedThreadPool(incomingMsgThreadPoolSize, tf);
        startupTime = System.currentTimeMillis();
        this.gmsMonitor = gmsMonitor;
        GMSContext ctx = GMSContextFactory.getGMSContext(groupName);
        if (ctx == null || ctx.getMemberType() == GroupManagementService.MemberType.CORE) {
            isSpectator = false;
        } else {
            isSpectator = true;
        }
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("Router: isSpectator:" + isSpectator + " MONITOR_ENABLED:" + gmsMonitor.ENABLED);
        }
    }

    /**
     * adds a FailureNotificationActionFactory as a destination. Collects this actionfactory in a Collection of same type.
     *
     * @param failureNotificationActionFactory the FailureNotificationActionFactory
     *
     */
    void addDestination(final FailureNotificationActionFactory failureNotificationActionFactory) {
        failureNotificationAF.add(failureNotificationActionFactory);
        logActionRegistration("FailureNotification");
    }

    /**
     * adds a FailureRecoveryActionFactory as a destination. Collects this actionfactory in a Collection of same type.
     *
     * @param componentName the component name
     * @param failureRecoveryActionFactory the FailureRecoveryActionFactory
     */
    void addDestination(final String componentName, final FailureRecoveryActionFactory failureRecoveryActionFactory) {
        failureRecoveryAF.put(componentName, failureRecoveryActionFactory);
        logActionRegistration("FailureRecovery", componentName);
    }

    /**
     * adds a JoinNotificationActionFactory as a destination. Collects this actionfactory in a Collection of same type.
     *
     * @param joinNotificationActionFactory the JoinNotificationActionFactory
     */
    void addDestination(final JoinNotificationActionFactory joinNotificationActionFactory) {
        joinNotificationAF.add(joinNotificationActionFactory);
        logActionRegistration("JoinNotification");
    }

    /**
     * adds a JoinedAndReadyNotificationActionFactory as a destination. Collects this actionfactory in a Collection of same
     * type.
     *
     * @param joinedAndReadyNotificationActionFactory the JoinedAndReadyNotificationActionFactory
     */
    void addDestination(final JoinedAndReadyNotificationActionFactory joinedAndReadyNotificationActionFactory) {
        joinedAndReadyNotificationAF.add(joinedAndReadyNotificationActionFactory);
        logActionRegistration("JoinedAndReadyNotification");
    }

    /**
     * adds a PlannedShutdownActionFactory as a destination. Collects this actionfactory in a Collection of same type.
     *
     * @param plannedShutdownActionFactory the PlannedShutdownActionFactory
     */
    void addDestination(final PlannedShutdownActionFactory plannedShutdownActionFactory) {
        plannedShutdownAF.add(plannedShutdownActionFactory);
        logActionRegistration("PlannedShutdown");
    }

    /**
     * adds a FailureSuspectedActionFactory as a destination. Collects this actionfactory in a Collection of same type.
     *
     * @param failureSuspectedActionFactory the FailureSuspectedActionFactory
     */
    void addDestination(final FailureSuspectedActionFactory failureSuspectedActionFactory) {
        failureSuspectedAF.add(failureSuspectedActionFactory);
        logActionRegistration("FailureSuspected");
    }

    /**
     * adds a MessageActionFactory as a destination for a given component name.
     *
     * @param messageActionFactory the MessageActionFactory
     * @param componentName the component name
     */
    void addDestination(final MessageActionFactory messageActionFactory, final String componentName) {
        messageAF.put(componentName, messageActionFactory);
        logActionRegistration("GMS Message", componentName);
    }

    /**
     * adds a GroupLeadershipNotificationActionFactory as a destination. Collects this actionfactory in a Collection of same
     * type.
     *
     * @param groupLeadershipNotificationActionFactory the GroupLeadershipNotificationActionFactory
     */
    void addDestination(final GroupLeadershipNotificationActionFactory groupLeadershipNotificationActionFactory) {
        groupLeadershipNotificationAFs.add(groupLeadershipNotificationActionFactory);
        logActionRegistration("GroupLeadershipNotification");
    }

    private void logActionRegistration(String notification) {
        logActionRegistration(notification, null);
    }

    private void logActionRegistration(String notification, String componentName) {
        if (handlerLogger.isLoggable(Level.FINE)) {
            final Exception ste = handlerLogger.isLoggable(Level.FINER) ? new Exception("stack trace") : null;
            StringBuilder buf = new StringBuilder(30);
            buf.append("registering a ").append(notification).append(" handler for ");
            if (componentName != null) {
                buf.append("targetComponent: ").append(componentName).append(" for ");
            }
            buf.append("group: ").append(groupName);
            handlerLogger.log(Level.FINE, buf.toString(), ste);
        }
    }

    /**
     * removes a FailureNotificationActionFactory destination.
     *
     * @param failureNotificationActionFactory the FailureNotificationActionFactory
     *
     */
    void removeDestination(final FailureNotificationActionFactory failureNotificationActionFactory) {
        failureNotificationAF.remove(failureNotificationActionFactory);
    }

    /**
     * removes a JoinNotificationActionFactory destination.
     *
     * @param joinNotificationActionFactory the JoinNotificationActionFactory
     */
    void removeDestination(final JoinNotificationActionFactory joinNotificationActionFactory) {
        joinNotificationAF.remove(joinNotificationActionFactory);
    }

    /**
     * removes a JoinedAndReadyNotificationActionFactory destination.
     *
     * @param joinedAndReadyNotificationActionFactory the JoinedAndReadyNotificationActionFactory
     */
    void removeDestination(final JoinedAndReadyNotificationActionFactory joinedAndReadyNotificationActionFactory) {
        joinedAndReadyNotificationAF.remove(joinedAndReadyNotificationActionFactory);
    }

    /**
     * removes a PlannedShutdownActionFactory destination.
     *
     * @param plannedShutdownActionFactory the PlannedShutdownActionFactory
     */
    void removeDestination(final PlannedShutdownActionFactory plannedShutdownActionFactory) {
        plannedShutdownAF.remove(plannedShutdownActionFactory);
    }

    /**
     * removes a PlannedShutdownActionFactory destination.
     *
     * @param failureSuspectedActionFactory the PlannedShutdownActionFactory
     */
    void removeDestination(final FailureSuspectedActionFactory failureSuspectedActionFactory) {
        failureSuspectedAF.remove(failureSuspectedActionFactory);
    }

    /**
     * removes a MessageActionFactory instance belonging to a specified component
     *
     * @param componentName the component name
     */
    public void removeMessageAFDestination(final String componentName) {
        messageAF.remove(componentName);
    }

    /**
     * removes a FailureRecoveryActionFactory instance belonging to a specified component
     *
     * @param componentName the component name
     */
    public void removeFailureRecoveryAFDestination(final String componentName) {
        failureRecoveryAF.remove(componentName);
    }

    /**
     * removes a GroupLeadershipNotificationActionFactory destination.
     *
     * @param groupLeadershipNotificationActionFactory the GroupLeadershipNotificationActionFactory
     */
    void removeDestination(final GroupLeadershipNotificationActionFactory groupLeadershipNotificationActionFactory) {
        groupLeadershipNotificationAFs.remove(groupLeadershipNotificationActionFactory);
    }

    /**
     * Queues signals. Expects an array of signals which are handed off to working threads that will determine their
     * corresponding actions to call their consumeSignal method.
     *
     * @param signalPacket the signal packet
     */
    public void queueSignals(final SignalPacket signalPacket) {
        queueSignal(signalPacket);
    }

    private void recordQueueHighWaterMark() {
        if (monitorLogger.isLoggable(Level.FINE)) {
            int currentQueueSize = queue.size();
            int localHighWater = queueHighWaterMark.get();
            if (currentQueueSize > localHighWater) {
                queueHighWaterMark.compareAndSet(localHighWater, currentQueueSize);
            }
        }
    }

    private long lastReported = 0L;
    static final private long NEXT_REPORT_DURATION = 1000 * 60 * 30; // 30 minutes

    /**
     * Adds a single signal to the queue.
     *
     * @param signalPacket the signal packet
     */
    public void queueSignal(final SignalPacket signalPacket) {
        try {
            boolean result = queue.offer(signalPacket);
            if (result == false) {

                // blocking queue is full. log how long we were blocked.
                int fullcapacity = queue.size();
                long starttime = System.currentTimeMillis();
                try {
                    queue.put(signalPacket);
                } finally {
                    long duration = System.currentTimeMillis() - starttime;
                    if (duration > 2000) {
                        if (lastReported + NEXT_REPORT_DURATION < System.currentTimeMillis()) {
                            monitorLogger.log(Level.WARNING, "router.signal.queue.blocking", new Object[] { duration, fullcapacity });
                            lastReported = System.currentTimeMillis();
                        }
                    } else if (duration > 20) {
                        if (lastReported + NEXT_REPORT_DURATION < System.currentTimeMillis()) {
                            if (monitorLogger.isLoggable(Level.FINE)) {
                                monitorLogger.fine("signal processing blocked due to signal queue being full for " + duration
                                        + " ms. Router signal queue capacity: " + fullcapacity);
                                lastReported = System.currentTimeMillis();
                            }
                        }
                    }
                }
            }
            recordQueueHighWaterMark();
        } catch (InterruptedException e) {
        }
    }

    void undocketAllDestinations() {
        failureRecoveryAF.clear();
        failureNotificationAF.clear();
        plannedShutdownAF.clear();
        joinNotificationAF.clear();
        messageAF.clear();
        failureSuspectedAF.clear();
        groupLeadershipNotificationAFs.clear();
    }

    void notifyFailureNotificationAction(final FailureNotificationSignal signal) {
        FailureNotificationAction a;
        logger.log(Level.INFO, "failurenotificationsignals.send.member", new Object[] { signal.getMemberToken() });
        for (FailureNotificationActionFactory fnaf : failureNotificationAF) {
            a = (FailureNotificationAction) fnaf.produceAction();
            callAction(a, new FailureNotificationSignalImpl(signal));
        }
    }

    void notifyFailureRecoveryAction(final FailureRecoverySignal signal) {
        final FailureRecoveryAction a;
        final FailureRecoverySignal frs;
        logger.log(Level.INFO, "failurerecoverysignals.send.component", new Object[] { signal.getComponentName(), signal.getMemberToken() });
        final FailureRecoveryActionFactory fraf = failureRecoveryAF.get(signal.getComponentName());
        a = (FailureRecoveryAction) fraf.produceAction();
        frs = new FailureRecoverySignalImpl(signal);
        callAction(a, frs);
    }

    void notifyFailureSuspectedAction(final FailureSuspectedSignal signal) {
        FailureSuspectedAction a;
        FailureSuspectedSignal fss;
        logger.log(Level.INFO, "failuresuspectedsignals.send.member", new Object[] { signal.getMemberToken() });
        for (FailureSuspectedActionFactory fsaf : failureSuspectedAF) {
            a = (FailureSuspectedAction) fsaf.produceAction();
            fss = new FailureSuspectedSignalImpl(signal);
            callAction(a, fss);
        }
    }

    private ConcurrentHashMap<String, AtomicInteger> undeliveredMessages = new ConcurrentHashMap<String, AtomicInteger>();

    private void notifyMessageAction(final MessageSignal signal, String targetComponent) {
        MessageActionFactory maf = null;
        maf = messageAF.get(targetComponent);
        if (maf == null) {
            if (gmsMonitor.ENABLED && !isSpectator) {
                GMSMonitor.MessageStats stats = gmsMonitor.getGMSMessageMonitorStats(targetComponent);
                stats.incrementNumMsgsNoHandler();
            }
            // Introduce a mechanism in future to
            // register a MessageActionFactory for messages to non-existent targetComponent.
            // (.i.e register a MessageActionFactory for "null" targetComponent.)
            // this action factory could do something like the following to register that message was not handled.

            // following commented out code did report messages that were not delivered to any target component.
            if (!isSpectator) {
                int missedMessagesInt = 0;
                AtomicInteger missedMessages = undeliveredMessages.get(targetComponent);
                if (missedMessages == null) {
                    missedMessages = new AtomicInteger(1);
                    missedMessagesInt = 1;
                    AtomicInteger alreadyPresent = undeliveredMessages.putIfAbsent(targetComponent, missedMessages);
                    if (alreadyPresent != null) {
                        alreadyPresent.incrementAndGet();
                    }
                } else {
                    missedMessagesInt = missedMessages.incrementAndGet();
                }
                if (missedMessagesInt == 1) {
                    logger.log(Level.INFO, "router.no.msghandler.for.targetcomponent", new Object[] { targetComponent, groupName });
                }
            }
        } else {
            MessageAction a = (MessageAction) maf.produceAction();
            callMessageAction(a, signal);
        }
    }

    void notifyMessageAction(final MessageSignal signal) {
        String targetComponent = signal.getTargetComponent();
        if (targetComponent == null) {
            // disallow this complicated functionality.
//            Set<String> keySet;
//            synchronized (messageAF) {
//                keySet = new TreeSet<String>(messageAF.keySet());
//            }
//
//            // if targetComponent was null,  treat as a wildcard and send messsage to ALL registered message action factories as
//            // described by  GroupHandle.sendMessage javadoc.
//            for (String targetComponentI : keySet) {
//                notifyMessageAction(signal, targetComponentI);
//            }
        } else {
            notifyMessageAction(signal, targetComponent);
        }
    }

    void notifyJoinNotificationAction(final JoinNotificationSignal signal) {
        JoinNotificationAction a;
        JoinNotificationSignal jns;
        // todo: NEED to be able to predetermine the number of GMS clients
        // that would register for join notifications.
        if (isJoinNotificationAFRegistered()) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE,
                        MessageFormat.format("Sending JoinNotificationSignals to " + "registered Actions, Member {0}...", signal.getMemberToken()));
            }
            for (JoinNotificationActionFactory jnaf : joinNotificationAF) {
                a = (JoinNotificationAction) jnaf.produceAction();
                jns = new JoinNotificationSignalImpl(signal);
                callAction(a, jns);
            }
        } else if (System.currentTimeMillis() - startupTime < GROUP_WARMUP_TIME) {
            // put it back to the queue if it is less than
            // 30 secs since start time. we give 30 secs for join notif
            // registrations to happen until which time, the signals are
            // available in queue.
            queueSignal(new SignalPacket(signal));
        }
    }

    void notifyJoinedAndReadyNotificationAction(final JoinedAndReadyNotificationSignal signal) {
        JoinedAndReadyNotificationAction a;
        JoinedAndReadyNotificationSignal jns;
        if (isJoinedAndReadyNotificationAFRegistered()) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE,
                        MessageFormat.format("Sending JoinedAndReadyNotificationSignals to " + "registered Actions, Member {0}...", signal.getMemberToken()));
            }
            for (JoinedAndReadyNotificationActionFactory jnaf : joinedAndReadyNotificationAF) {
                a = (JoinedAndReadyNotificationAction) jnaf.produceAction();
                jns = new JoinedAndReadyNotificationSignalImpl(signal);
                callAction(a, jns);
            }
        }
    }

    void notifyPlannedShutdownAction(final PlannedShutdownSignal signal) {
        PlannedShutdownAction a;
        PlannedShutdownSignal pss;
        logger.log(Level.INFO, "plannedshutdownsignals.send.member", new Object[] { signal.getEventSubType(), signal.getMemberToken() });
        for (PlannedShutdownActionFactory psaf : plannedShutdownAF) {
            a = (PlannedShutdownAction) psaf.produceAction();
            pss = new PlannedShutdownSignalImpl(signal);
            callAction(a, pss);
        }
    }

    void notifyGroupLeadershipNotificationAction(final GroupLeadershipNotificationSignal signal) {
        GroupLeadershipNotificationAction a;
        GroupLeadershipNotificationSignal glsns;
        if (isGroupLeadershipNotificationAFRegistered()) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE,
                        MessageFormat.format("Sending GroupLeadershipNotificationSignals to " + "registered Actions, Member {0}...", signal.getMemberToken()));
            }
            for (GroupLeadershipNotificationActionFactory glsnaf : groupLeadershipNotificationAFs) {
                a = (GroupLeadershipNotificationAction) glsnaf.produceAction();
                glsns = new GroupLeadershipNotificationSignalImpl(signal);
                callAction(a, glsns);
            }
        }
    }

    private void callAction(final Action a, final Signal signal) {
        try {
            final CallableAction task = new CallableAction(a, signal);
            actionPool.submit(task);
        } catch (RejectedExecutionException e) {
            logger.log(Level.WARNING, e.getMessage());
        }
    }

    private void callMessageAction(final Action a, final MessageSignal signal) {
        try {
            final CallableAction task = new CallableAction(a, signal);
            messageActionPool.submit(task);
        } catch (RejectedExecutionException e) {
            logger.log(Level.WARNING, e.getMessage());
        }
    }

    public boolean isFailureNotificationAFRegistered() {
        boolean retval = true;
        if (failureNotificationAF.isEmpty()) {
            retval = false;
        }
        return retval;
    }

    public boolean isFailureRecoveryAFRegistered() {
        boolean retval = true;
        if (failureRecoveryAF.isEmpty()) {
            retval = false;
        }
        return retval;
    }

    public boolean isMessageAFRegistered() {
        boolean retval = true;
        if (messageAF.isEmpty()) {
            retval = false;
        }
        return retval;
    }

    public boolean isPlannedShutdownAFRegistered() {
        boolean retval = true;
        if (plannedShutdownAF.isEmpty()) {
            retval = false;
        }
        return retval;
    }

    public boolean isJoinNotificationAFRegistered() {
        boolean retval = true;
        if (joinNotificationAF.isEmpty()) {
            retval = false;
        }
        return retval;
    }

    public boolean isJoinedAndReadyNotificationAFRegistered() {
        boolean retval = true;
        if (joinedAndReadyNotificationAF.isEmpty()) {
            retval = false;
        }
        return retval;
    }

    public boolean isFailureSuspectedAFRegistered() {
        boolean retval = true;
        if (failureSuspectedAF.isEmpty()) {
            retval = false;
        }
        return retval;
    }

    public boolean isGroupLeadershipNotificationAFRegistered() {
        boolean retval = true;
        if (groupLeadershipNotificationAFs.isEmpty()) {
            retval = false;
        }
        return retval;
    }

    Hashtable<String, FailureRecoveryActionFactory> getFailureRecoveryAFRegistrations() {
        return new Hashtable<String, FailureRecoveryActionFactory>(failureRecoveryAF);
    }

    public Set<String> getFailureRecoveryComponents() {
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, MessageFormat.format("Router Returning failure " + "recovery components={0}", failureRecoveryAF.keySet()));
        }
        return failureRecoveryAF.keySet();
    }

    public void shutdown() {
        undocketAllDestinations();
        if (signalHandlerThread != null) {
            signalHandler.stop(signalHandlerThread);
        }
        // consider WARNING to tell user to increase size of INCOMING_MSG_QUEUE_SIZE for their application's messaging load.
        if (monitorLogger.isLoggable(Level.INFO)) {
            monitorLogger.log(Level.INFO, "router.stats.monitor.msgqueue.high.water", new Object[] { queueHighWaterMark.get(), MAX_QUEUE_SIZE });
        }
        if (queue != null) {
            int unprocessedEventSize = queue.size();
            if (unprocessedEventSize > 0) {
                logger.log(Level.WARNING, "router.shutdown.unprocessed", new Object[] { queue.size() });
                // TBD. If shutdown has unprocessed events outstanding.
                try {
                    LinkedList<SignalPacket> unprocessedEvents = new LinkedList<SignalPacket>();
                    queue.drainTo(unprocessedEvents);
                    for (SignalPacket sp : unprocessedEvents) {
                        logger.log(Level.INFO, "router.shutdown.unprocessed.signal", new Object[] { sp.toString() });
                    }
                } catch (Throwable t) {
                }
            }

            queue.clear();
        }
        if (actionPool != null) {
            actionPool.shutdownNow();
        }
        if (messageActionPool != null) {
            messageActionPool.shutdown();
        }
    }

    /**
     * implements Callable. Used for handing off the job of calling the Action's consumeSignal() method to a ThreadPool.
     */
    private static class CallableAction implements Callable<Object> {
        private Action action;
        private Signal signal;

        CallableAction(final Action action, final Signal signal) {
            this.action = action;
            this.signal = signal;
        }

        public Object call() throws ActionException {
            try {
                action.consumeSignal(signal);
            } catch (ActionException ae) {
                // don't wrap an ActionException within an ActionException.
                throw ae;
            } catch (Throwable t) {
                ActionException nae = new ActionException();
                nae.initCause(t);
                throw nae;
            }
            return null;
        }
    }
}
