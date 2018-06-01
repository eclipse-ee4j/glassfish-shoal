/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package org.shoal.ha.cache.api;

/**
 * @author Mahesh KannanR
 */
public interface DataStoreMBean {

    public String getStoreName();

    public String getKeyClassName();

    public String getValueClassName();

    public String getEntryUpdaterClassName();

    public String getKeyTransformerClassName();

    public int getSize();

    public int getSentSaveCount();

    public int getExecutedSaveCount();

    public int getBatchSentCount();

    public int getLoadCount();

    public int getLoadSuccessCount();

    public int getLocalLoadSuccessCount();

    public int getSimpleLoadSuccessCount();

    public int getBroadcastLoadSuccessCount();

    public int getSaveOnLoadCount();

    public int getLoadFailureCount();

    public int getBatchReceivedCount();

    public int getSentRemoveCount();

    public int getExecutedRemoveCount();
    
    public int getFlushThreadFlushedCount();

    public int getFlushThreadWakeupCount();

    public int getRemoveExpiredCallCount();

    public int getExpiredEntriesCount();

    public int getGmsSendCount();

    public long getGmsSendBytesCount();
}
