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

package org.glassfish.shoal.gms.client;

import org.glassfish.shoal.gms.api.core.Action;
import org.glassfish.shoal.gms.api.core.CallBack;
import org.glassfish.shoal.gms.api.core.MessageActionFactory;

/**
 * Reference implementation of MessageActionFactory interface.
 *
 * @author Shreedhar Ganapathy Date: Jan 21, 2004
 * @version $Revision$
 */
public class MessageActionFactoryImpl implements MessageActionFactory {
    private final CallBack cb;

    public MessageActionFactoryImpl(final CallBack cb) {
        this.cb = cb;
    }

    /**
     * Produces an Action instance.
     *
     * @return com.sun.enterprise.ee.cms.Action
     */
    public Action produceAction() {
        return new MessageActionImpl(cb);
    }
}
