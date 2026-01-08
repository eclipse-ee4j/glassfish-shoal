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

package org.glassfish.shoal.gms.common;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.shoal.gms.api.core.FailureNotificationSignal;
import org.glassfish.shoal.gms.api.core.FailureRecoverySignal;
import org.glassfish.shoal.gms.api.core.FailureSuspectedSignal;
import org.glassfish.shoal.gms.api.core.GroupLeadershipNotificationSignal;
import org.glassfish.shoal.gms.api.core.JoinNotificationSignal;
import org.glassfish.shoal.gms.api.core.JoinedAndReadyNotificationSignal;
import org.glassfish.shoal.gms.api.core.MessageSignal;
import org.glassfish.shoal.gms.api.core.PlannedShutdownSignal;
import org.glassfish.shoal.gms.api.core.Signal;
import org.glassfish.shoal.gms.logging.GMSLogDomain;

/**
 * On a separate thread, analyses and handles the Signals delivered to it. Picks up signals from a BlockingQueue and
 * processes them.
 *
 * @author Shreedhar Ganapathy Date: Jan 22, 2004
 * @version $Revision$
 */
public class SignalHandler implements Runnable {
    private final BlockingQueue<SignalPacket> signalQueue;
    private final Router router;
    private Logger logger = GMSLogDomain.getLogger(GMSLogDomain.GMS_LOGGER);
    private AtomicBoolean stopped = new AtomicBoolean(false);

    /**
     * Creates a SignalHandler
     *
     * @param packetQueue the packet exchange queue
     * @param router the Router
     */
    public SignalHandler(final BlockingQueue<SignalPacket> packetQueue, final Router router) {
        this.signalQueue = packetQueue;
        this.router = router;
    }

    public void run() {
        try {
            Signal[] signals;
            while (!stopped.get()) {
                SignalPacket signalPacket = null;
                try {
                    signalPacket = signalQueue.take();
                    if (signalPacket != null) {
                        if ((signals = signalPacket.getSignals()) != null) {
                            handleSignals(signals);
                        } else {
                            handleSignal(signalPacket.getSignal());
                        }
                    }
                } catch (InterruptedException e) {
                    stopped.set(true);
                } catch (Throwable e) {
                    logger.log(Level.SEVERE, "sig.handler.unhandled", new Object[] { Thread.currentThread().getName() });
                    logger.log(Level.WARNING, "stack trace", e);
                }
            }
        } finally {
            logger.log(Level.INFO, "sig.handler.thread.terminated", new Object[] { Thread.currentThread().getName() });
        }
    }

    private void handleSignal(final Signal signal) {
        analyzeSignal(signal);
    }

    private void handleSignals(final Signal[] signals) {
        for (Signal signal : signals) {
            analyzeSignal(signal);
        }
    }

    private void analyzeSignal(final Signal signal) {
        if (signal == null) {
            throw new IllegalArgumentException("Signal is null. Cannot analyze.");
        }
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "SignalHandler : processing a received signal " + signal.getClass().getName());
        }
        try {
            if (signal instanceof FailureRecoverySignal) {
                router.notifyFailureRecoveryAction((FailureRecoverySignal) signal);
            } else if (signal instanceof FailureNotificationSignal) {
                router.aliveAndReadyView.processNotification(signal);
                router.notifyFailureNotificationAction((FailureNotificationSignal) signal);
            } else if (signal instanceof MessageSignal) {
                router.notifyMessageAction((MessageSignal) signal);
            } else if (signal instanceof JoinNotificationSignal) {
                router.notifyJoinNotificationAction((JoinNotificationSignal) signal);
            } else if (signal instanceof PlannedShutdownSignal) {
                router.aliveAndReadyView.processNotification(signal);
                router.notifyPlannedShutdownAction((PlannedShutdownSignal) signal);
            } else if (signal instanceof FailureSuspectedSignal) {
                router.notifyFailureSuspectedAction((FailureSuspectedSignal) signal);
            } else if (signal instanceof JoinedAndReadyNotificationSignal) {
                router.aliveAndReadyView.processNotification(signal);
                router.notifyJoinedAndReadyNotificationAction((JoinedAndReadyNotificationSignal) signal);
            } else if (signal instanceof GroupLeadershipNotificationSignal) {
                router.notifyGroupLeadershipNotificationAction((GroupLeadershipNotificationSignal) signal);
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, "sig.handler.ignoring.exception", new Object[] { t.getLocalizedMessage() });
            logger.log(Level.WARNING, t.getLocalizedMessage(), t);
        }
    }

    public void stop(Thread t) {
        stopped.set(true);
        t.interrupt();
    }
}
