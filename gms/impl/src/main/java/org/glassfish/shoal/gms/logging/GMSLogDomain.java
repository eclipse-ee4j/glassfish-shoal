/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
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

package org.glassfish.shoal.gms.logging;

import java.util.logging.Logger;

/**
 * GMS logger that abstracts out application specific loggers. One can plug in any logger here - even potentially set
 * custom log handlers through this abstraction.
 *
 * @author Shreedhar Ganapathy Date: Apr 1, 2004
 * @version $Revision$
 */
public class GMSLogDomain {

    public static final String GMS_LOGGER = "ShoalLogger";

    private static final String LOG_STRINGS = "org.glassfish.shoal.gms.logging.LogStrings";

    private static final String GMS_MONITOR_LOGGER = GMS_LOGGER + ".monitor";
    private static final String GMS_HANDLER_LOGGER = GMS_LOGGER + ".handler";
    private static final String MCAST_LOGGER_NAME = GMS_LOGGER + ".mcast";
    private static final String MASTER_LOGGER_NAME = GMS_LOGGER + ".MasterNode";
    private static final String GMS_SEND = GMS_LOGGER + ".send";
    private static final String GMS_DSC = GMS_LOGGER + ".dsc";
    private static final String GMS_NOMCAST = GMS_LOGGER + ".nomcast";

    private GMSLogDomain() {
        /* you can't have me */}

    public static Logger getLogger(final String loggerName) {
        return Logger.getLogger(loggerName, LOG_STRINGS);
    }

    public static Logger getMonitorLogger() {
        return Logger.getLogger(GMS_MONITOR_LOGGER, LOG_STRINGS);
    }

    public static Logger getMcastLogger() {
        return Logger.getLogger(MCAST_LOGGER_NAME, LOG_STRINGS);
    }

    public static Logger getMasterNodeLogger() {
        return Logger.getLogger(MASTER_LOGGER_NAME, LOG_STRINGS);
    }

    public static Logger getSendLogger() {
        return Logger.getLogger(GMS_SEND, LOG_STRINGS);
    }

    public static Logger getHandlerLogger() {
        return Logger.getLogger(GMS_HANDLER_LOGGER, LOG_STRINGS);
    }

    public static Logger getDSCLogger() {
        return Logger.getLogger(GMS_DSC, LOG_STRINGS);
    }

    public static Logger getNoMCastLogger() {
        return Logger.getLogger(GMS_NOMCAST, LOG_STRINGS);
    }
}
