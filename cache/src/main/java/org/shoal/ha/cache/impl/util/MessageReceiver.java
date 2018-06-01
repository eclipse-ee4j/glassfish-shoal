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

package org.shoal.ha.cache.impl.util;

import com.sun.enterprise.ee.cms.core.*;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Mahesh Kannan
 */
public abstract class MessageReceiver
        implements CallBack {

    private final static Logger logger = Logger.getLogger("ReplicationLogger");

    @Override
    public void processNotification(Signal signal) {
        Object message = null;
        MessageSignal messageSignal = null;

//            logger.log(Level.INFO, "Source Member: " + signal.getMemberToken() + " group : " + signal.getGroupName());
        if (signal instanceof MessageSignal) {
            messageSignal = (MessageSignal) signal;
            message = ((MessageSignal) signal).getMessage();
//                logger.log(Level.INFO, "\t\t***  Message received: "
//                        + ((MessageSignal) signal).getTargetComponent() + "; "
//                        + ((MessageSignal) signal).getMemberToken());

            if (messageSignal != null) {
                handleMessage(messageSignal.getMemberToken(), messageSignal.getTargetComponent(),
                        (byte[]) message);
            }
        }
    }

    protected abstract void handleMessage(String senderName, String messageToken, byte[] data);
}
