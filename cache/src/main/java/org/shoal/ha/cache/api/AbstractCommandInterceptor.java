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

package org.shoal.ha.cache.api;

import org.shoal.ha.cache.impl.command.Command;
import org.shoal.ha.cache.impl.command.CommandManager;


/**
 * @author Mahesh Kannan
 *
 */
public abstract class AbstractCommandInterceptor<K, V> {

    protected String storeName;

    protected DataStoreContext<K, V> dsc;
    
    private CommandManager<K, V> cm;
    
    private AbstractCommandInterceptor<K, V> next;
  
    private AbstractCommandInterceptor<K, V> prev;
    
    public void initialize(DataStoreContext<K, V> dsc) {
        this.dsc = dsc;
        this.cm = dsc.getCommandManager();

        this.storeName = dsc.getServiceName();
    }

    public final DataStoreContext<K, V> getDataStoreContext() {
        return dsc;
    }

    public CommandManager getCommandManager() {
        return cm;
    }

    public final void setNext(AbstractCommandInterceptor<K, V> next) {
        this.next = next;
    }

    public final void setPrev(AbstractCommandInterceptor<K, V> prev) {
        this.prev = prev;
    }

    public final AbstractCommandInterceptor<K, V> getNext() {
        return next;    
    }
    
    public final AbstractCommandInterceptor<K, V> getPrev() {
        return prev;
    }

    public void onTransmit(Command<K, V> cmd, String initiator)
        throws DataStoreException {
        AbstractCommandInterceptor n = getNext();
        if (n != null) {
            n.onTransmit(cmd, initiator);
        }
    }

    public void onReceive(Command<K, V> cmd, String initiator)
        throws DataStoreException {
        AbstractCommandInterceptor<K, V> p = getPrev();
        if (p != null) {
            p.onReceive(cmd, initiator);
        }
    }

    public void close() {}

}
