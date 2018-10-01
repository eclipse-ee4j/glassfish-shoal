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

import com.sun.enterprise.ee.cms.core.Signal;

/**
 * A packet containing the Signal(s) being delivered to the Router's signal queue.
 *
 * @author Shreedhar Ganapathy Date: Jan 22, 2004
 * @version $Revision$
 */
public class SignalPacket {
    private Signal[] signals = null;
    private Signal signal = null;

    public SignalPacket(final Signal[] signals) {
        this.signals = signals;
    }

    public SignalPacket(final Signal signal) {
        this.signal = signal;
    }

    Signal[] getSignals() {
        return signals;
    }

    Signal getSignal() {
        return signal;
    }

    public String toString() {
        String result = "SignalPacket contains: ";
        if (signal != null) {
            result += signal.toString();
        } else if (signals != null) {
            for (Signal s : signals) {
                result += s.toString() + " ";
            }

        }
        return result;
    }
}
