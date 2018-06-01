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

import com.sun.enterprise.ee.cms.core.*;
import com.sun.enterprise.ee.cms.logging.GMSLogDomain;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reference implementation of the FailureNotificationAction
 *
 * @author Shreedhar Ganapathy
 * @author Masood Mortazavi
 * Date: Jan 8, 2004
 * @version $Revision$
 */
public class FailureNotificationActionImpl implements FailureNotificationAction{
    private Logger logger = GMSLogDomain.getLogger(GMSLogDomain.GMS_LOGGER);
    private CallBack caller;

    public FailureNotificationActionImpl (final CallBack caller) {
        this.caller=caller;
    }
    /**
     * processes the recovery signal. typically involves getting information
     * from the signal, acquiring the signal and after processing, releasing
     * the signal
     * @param signal the signal
     */
    public void consumeSignal(final Signal signal) throws ActionException {
        //ALWAYS call Acquire before doing any other processing
        boolean signalAcquired = false;
        try {
            signal.acquire();
            signalAcquired = true;
            notifyListeners(signal);
        } catch (SignalAcquireException e) {
            logger.log(Level.SEVERE, e.getLocalizedMessage(), e);
        } finally {

            //ALWAYS call Release after completing any other processing.
            if (signalAcquired) {
                try {
                    signal.release();
                } catch (SignalReleaseException e) {
                    logger.log(Level.SEVERE, e.getLocalizedMessage(),e);
                }
            }
        }
    }

    private void notifyListeners(final Signal signal) {
        caller.processNotification(signal);
    }
}
