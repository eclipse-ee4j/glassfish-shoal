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

package com.sun.enterprise.ee.cms.impl.base;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Enable thread configuration of GMS threads in thread pools.
 */
public class GMSThreadFactory implements ThreadFactory {

    private final String threadPrefixName;
    private final AtomicInteger threadNum = new AtomicInteger(1);
    private final boolean isDaemon;

    public GMSThreadFactory(String threadPrefixName) {
        this(threadPrefixName, true);
    }

    public GMSThreadFactory(String threadPrefixName, boolean isDaemon) {
        this.threadPrefixName = threadPrefixName;
        this.isDaemon = isDaemon;
    }

    public Thread newThread(Runnable run) {
        StringBuffer threadName = new StringBuffer(30);
        threadName.append(threadPrefixName).append("-").append(threadNum.getAndIncrement());
        Thread result = new Thread(run);
        result.setName(threadName.toString());
        result.setDaemon(isDaemon);
        return result;
    }
}
