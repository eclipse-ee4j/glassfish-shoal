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

import java.util.logging.Level;
import java.util.logging.Logger;

import org.shoal.ha.cache.api.AbstractCommandInterceptor;
import org.shoal.ha.cache.api.DataStoreException;
import org.shoal.ha.cache.api.ShoalCacheLoggerConstants;
import org.shoal.ha.cache.impl.command.Command;

/**
 * @author Mahesh Kannan
 */
public final class CommandHandlerInterceptor<K, V> extends AbstractCommandInterceptor<K, V> {

    private static final Logger _logger = Logger.getLogger(ShoalCacheLoggerConstants.CACHE_COMMAND);

    @Override
    public void onTransmit(Command<K, V> cmd, String initiator) throws DataStoreException {
        try {
            cmd.prepareTransmit(dsc);
        } catch (Exception ex) {
            throw new DataStoreException("Error during writeCommandPayload", ex);
        }

        if (dsc.getInstanceName().equals(cmd.getTargetName())) {
            _logger.log(Level.WARNING, "To Me??? Cmd: " + cmd);
            cmd.execute(initiator);
        } else {
            super.onTransmit(cmd, initiator);
        }
    }

    @Override
    public void onReceive(Command<K, V> cmd, String initiator) throws DataStoreException {
        if (_logger.isLoggable(Level.FINE)) {
            _logger.log(Level.FINE, storeName + ": Received " + cmd + " from " + initiator);
        }

        try {
            cmd.execute(initiator);
        } catch (Exception ex) {
            throw new DataStoreException("Error during writeCommandPayload", ex);
        }
    }

}
