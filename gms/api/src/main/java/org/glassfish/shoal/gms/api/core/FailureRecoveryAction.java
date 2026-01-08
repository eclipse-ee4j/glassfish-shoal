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

package org.glassfish.shoal.gms.api.core;

/**
 * Action type corresponding to a recovery oriented action.Implementations consume a FailureRecoverySignal. The
 * acquisition of Signal corresponding to this Action ensures failure fencing, i.e. the failed server will not be
 * allowed to join the group once the Signal's <code>acquire</code> has been called and as long as this Action has not
 * called release on the Signal delivered to it. The FailureRecoverySignal is delivered to the FailureRecoveryAction
 * instance on only one server that is selected by its GMS instance in a distributed-consistent algorithm.
 *
 *
 * @author Shreedhar Ganapathy Date: ${DATE}
 * @version $Revision$
 */
public interface FailureRecoveryAction extends Action {

}
