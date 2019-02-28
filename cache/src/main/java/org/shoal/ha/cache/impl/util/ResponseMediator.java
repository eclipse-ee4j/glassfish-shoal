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

import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by IntelliJ IDEA. User: mk Date: Jan 9, 2010 Time: 2:44:09 PM To change this template use File | Settings |
 * File Templates.
 */
public class ResponseMediator {

    private ConcurrentHashMap<Long, CommandResponse> responses = new ConcurrentHashMap<Long, CommandResponse>();

    public CommandResponse createCommandResponse() {
        CommandResponse resp = new CommandResponse(this);
        responses.put(resp.getTokenId(), resp);

        return resp;
    }

    public CumulativeCommandResponse createCumulativeCommandResponse(int maxResponse, Object initialValue) {
        CumulativeCommandResponse resp = new CumulativeCommandResponse(this, maxResponse, initialValue);
        responses.put(resp.getTokenId(), resp);

        return resp;
    }

    public CommandResponse getCommandResponse(long tokenId) {
        return responses.get(tokenId);
    }

    public void removeCommandResponse(long id) {
        responses.remove(id);
    }
}
