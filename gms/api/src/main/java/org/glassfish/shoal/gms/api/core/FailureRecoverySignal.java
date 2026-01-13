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
 * Specific to recovery related signal. This Signal type will only be delivered to the corresponding Action (i.e. an
 * Action of the FailureRecoveryAction type) on only one of the servers.
 *
 * In other words, automatic recovery services which wish only one server to perform the recovery, should register an
 * ActionFactory which produces only FailureRecoveryAction on all participating GMS instances. The registration code is
 * identical in all servers. For any given failure, which one of the servers will be selected to receive a
 * FailureRecoverySignal is unique and depends on a function defined on the consistent membership view provided by the
 * underlying group infrastructure.
 *
 * This Signal's acquire() and release() methods (defined in the parent interface Signal) have special meaning and
 * <strong>must</strong> be called by the client before and after, respectively, performing any recovery operations.
 *
 * The <code>acquire()</code> method does the following: Enables the caller to raise a logical fence on a specified
 * target member token's component.
 * <p>
 * Failure Fencing is a group-wide protocol that, on one hand, requires members to update a shared/distributed
 * datastructure if any of their components need to perform operations on another members' corresponding component. On
 * the other hand, the group-wide protocol requires members to observe "Netiquette" during their startup so as to check
 * if any of their components are being operated upon by other group members. Typically this check is performed by the
 * respective components themselves. See the <code>GroupHandle.isFenced()</code> method for this check. When the
 * operation is completed by the remote member component, it removes the entry from the shared datastructure by calling
 * release() method.
 * <p>
 * Raising the fence, places an entry into the <code>DistributedStateCache</code> that is accessed by other members
 * during their startup to check for any fence.
 *
 * The release() method does the following : Enables the caller to lower the logical fence that was earlier raised on a
 * target member component. This is done when the recovery operation being performed on the target member component has
 * now completed.
 *
 * @author Shreedhar Ganapathy Date: Nov 10, 2003
 * @version $Revision$
 */
public interface FailureRecoverySignal extends FailureNotificationSignal {
    String getComponentName();

}
