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

package com.sun.enterprise.ee.cms.impl.client;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.enterprise.ee.cms.core.ActionException;
import com.sun.enterprise.ee.cms.core.CallBack;
import com.sun.enterprise.ee.cms.core.FailureRecoveryAction;
import com.sun.enterprise.ee.cms.core.FailureRecoverySignal;
import com.sun.enterprise.ee.cms.core.Signal;
import com.sun.enterprise.ee.cms.core.SignalAcquireException;
import com.sun.enterprise.ee.cms.core.SignalReleaseException;
import com.sun.enterprise.ee.cms.logging.GMSLogDomain;

/**
 * Reference implementation of FailureRecoveryAction interface
 *
 * @author Shreedhar Ganapathy Date: Jan 8, 2004
 * @version $Revision$
 */
public class FailureRecoveryActionImpl implements FailureRecoveryAction {
    private Logger logger = GMSLogDomain.getLogger(GMSLogDomain.GMS_LOGGER);
    private CallBack caller;

    public FailureRecoveryActionImpl(final CallBack caller) {
        this.caller = caller;
    }

    /**
     * processes the recovery signal. typically involves getting information from the signal, acquiring the signal and after
     * processing, releasing the signal
     *
     * @param signal the signal
     */
    public void consumeSignal(final Signal signal) throws ActionException {
        boolean signalAcquired = false;
        final String component = signal instanceof FailureRecoverySignal ? ((FailureRecoverySignal) signal).getComponentName() : "";
        try {
            // This is a mandatory call.
            // Always call acquire before doing any other processing as this
            // results in Failure Fencing which protects other members from
            // doing the same recovery operation
            signal.acquire();
            signalAcquired = true;
            logger.log(Level.FINE, component + ":Failure Recovery Signal acquired");
            notifyListeners(signal);
        } catch (SignalAcquireException e) {
            logger.log(Level.SEVERE, e.getLocalizedMessage(), e);
        } finally {
            if (signalAcquired) {
                // Always Release after completing any other processing.This call is
                // also mandatory.
                try {
                    signal.release();
                    logger.log(Level.FINE, component + ":Failure Recovery Signal released");
                } catch (SignalReleaseException e) {
                    logger.log(Level.SEVERE, e.getLocalizedMessage(), e);
                }
            }
        }
    }

    private void notifyListeners(final Signal signal) {
        caller.processNotification(signal);
    }
}
