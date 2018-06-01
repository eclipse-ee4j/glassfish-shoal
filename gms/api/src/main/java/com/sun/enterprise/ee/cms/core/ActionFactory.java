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

package com.sun.enterprise.ee.cms.core;

/**
 * Produces Action instances. This pattern enables implementors 
 * to provide Action instances only when called, along with the
 * flexibility to produce Actions from either a pool or by creating 
 * a new instance each time or by using a Singleton, etc.
 *
 * The method <code>produceAction</code> will be called before delivery
 * of Signal to the Action "produced."
 *
 * Each instance of an Action is guaranteed to be served an instance 
 * of Signal on a separate thread.
 *
 * If the ActionFactory always returns the same Action instance, that
 * instance should be written to take into account the possibility of multiple
 * threads delivering distinct signals.
 *
 * @author Shreedhar Ganapathy
 * Date: Jan 13, 2004
 * @version $Revision$
 */
public interface ActionFactory {
    /**
     * Produces an Action instance.
     * @return com.sun.enterprise.ee.cms.Action
     */
    Action produceAction();
}

