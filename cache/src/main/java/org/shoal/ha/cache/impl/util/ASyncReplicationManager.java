/*
 * Copyright (c) 2010, 2018 Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author Mahesh Kannan
 */
public class ASyncReplicationManager {

    private static final Object _lock = new Object();

    private static volatile ASyncReplicationManager _me = new ASyncReplicationManager();

    private static volatile ASyncThreadPool _asyncPool;

    private static ScheduledThreadPoolExecutor _scheduledTP;

    public static ASyncReplicationManager _getInstance() {
        return _me;
    }

    private ASyncReplicationManager() {
        int corePoolSize = getSystemProp("org.shoal.ha.threadpool.core.pool.size", 16);
        int maxPoolSize = getSystemProp("org.shoal.ha.threadpool.max.pool.size", 16);
        int keepAliveInSeconds = getSystemProp("org.shoal.ha.threadpool.keepalive.in.seconds", 300);
        int boundedPoolSize = getSystemProp("org.shoal.ha.threadpool.max.pending.replication.limit", 32 * 1024);

        LinkedBlockingQueue queue = new LinkedBlockingQueue(boundedPoolSize);
        _asyncPool = new ASyncThreadPool(corePoolSize, maxPoolSize, keepAliveInSeconds, queue);

        // TODO Should we another system property?
        _scheduledTP = new ScheduledThreadPoolExecutor(2);

//        System.out.println("Created ExecutorService with: " +
//            "core=" + corePoolSize + "; max=" + maxPoolSize +
//            "; keepAlive=" + keepAliveInSeconds + "; maxLimit=" + boundedPoolSize);
    }

    private static final int getSystemProp(String propName, int defaultValue) {
        int value = defaultValue;
        try {
            value = Integer.parseInt(System.getProperty(propName, "" + defaultValue));
        } catch (Exception ex) {
            // Ignore
        }

        return value;
    }

    public ThreadPoolExecutor getExecutorService() {
        return _asyncPool;
    }

    public ScheduledThreadPoolExecutor getScheduledThreadPoolExecutor() {
        return _scheduledTP;
    }
}
