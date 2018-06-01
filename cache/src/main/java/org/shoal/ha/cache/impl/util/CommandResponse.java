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

import org.shoal.ha.cache.impl.util.ResponseMediator;

import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicLong;


/**
 * @author Mahesh Kannan
 *
 */
public class CommandResponse
    implements Callable {

    private static final AtomicLong tokenCounter = new AtomicLong(0);

    private long tokenId;

    private String respondingInstanceName;

    protected Object result;

    protected int expectedUpdateCount;

    private FutureTask future;

    private ResponseMediator mediator;

    public CommandResponse(ResponseMediator mediator) {
        this.mediator = mediator;
        this.tokenId = tokenCounter.incrementAndGet();
        this.future = new FutureTask(this);
    }

    public void setExpectedUpdateCount(int value) {
        this.expectedUpdateCount = value;
    }

    public int getExpectedUpdateCount() {
        return expectedUpdateCount;
    }

    public int decrementAndGetExpectedUpdateCount() {
        return --expectedUpdateCount;
    }

    public long getTokenId() {
        return tokenId;
    }

    public FutureTask getFuture() {
        return future;
    }

    public void setResult(Object v) {
        this.result = v;
        mediator.removeCommandResponse(tokenId);
        future.run(); //Which calls our call()
    }

    public Object getTransientResult() {
        return result;
    }

    public void setTransientResult(Object temp) {
        result = temp;
    }

    public void setException(Exception ex) {
        setResult(ex);
    }

    public String getRespondingInstanceName() {
        return respondingInstanceName;
    }

    public void setRespondingInstanceName(String respondingInstanceName) {
        this.respondingInstanceName = respondingInstanceName;
    }

    public Object call() {
        return result;
    }
}
