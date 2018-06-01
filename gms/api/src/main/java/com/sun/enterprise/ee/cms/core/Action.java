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
 * Base interface to denote an Action.
 * An Action consumes a Signal which represents an Event.
 * An Action instance is produced by an ActionFactory.
 * All Action types inherit this base Action interface. Action
 * subtypes are defined in conjunction with a corresponding Signal
 * subtype and ActionFactory.
 * For each specific event, Users of the system implement
 * the corresponding ActionFactory type and Action type.
 * Users of the system register an ActionFactory type for a given
 * type of Signal they wish to receive.
 *
 * @author Shreedhar Ganapathy
 * Date: November 07, 2004
 * @version $Revision$
 */
public interface Action {

    /**
     * Implementations of consumeSignal should strive to return control 
     * promptly back to the thread that has delivered the Signal.
     * @param signal A Signal specifying a particular event in the group
     * @throws ActionException thrown when a exception condition occurs
     * wihle the Signal is consumed.
     */
    void consumeSignal(Signal signal) throws ActionException;
}
