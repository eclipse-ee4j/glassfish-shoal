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

package com.sun.enterprise.ee.cms.impl.common;

import com.sun.enterprise.ee.cms.core.FailureRecoverySignal;
import com.sun.enterprise.ee.cms.core.GMSException;
import com.sun.enterprise.ee.cms.core.SignalAcquireException;
import com.sun.enterprise.ee.cms.core.SignalReleaseException;

/**
 * Implements the FailureRecoverySignal Interface and provides operations corresponding to a recovery oriented Signal's
 * behavior
 *
 * @author Shreedhar Ganapathy Date: November 07, 2003
 * @version $Revision$
 */
public class FailureRecoverySignalImpl extends FailureNotificationSignalImpl implements FailureRecoverySignal {
    private String componentName;

    public FailureRecoverySignalImpl(final String componentName, final String failedMember, final String groupName, final long startTime) {
        this.failedMember = failedMember;
        this.componentName = componentName;
        this.groupName = groupName;
        this.startTime = startTime;
        this.ctx = GMSContextFactory.getGMSContext(groupName);
    }

    FailureRecoverySignalImpl(final FailureRecoverySignal signal) {
        this.failedMember = signal.getMemberToken();
        this.componentName = signal.getComponentName();
        this.groupName = signal.getGroupName();
        this.startTime = signal.getStartTime();
        this.ctx = GMSContextFactory.getGMSContext(groupName);
    }

    /**
     * Must be called by client before beginning any recovery operation in order to get support of failure fencing.
     *
     * @throws SignalAcquireException Exception when signal is not acquired
     */
    @Override
    public void acquire() throws SignalAcquireException {

        // deprecate fencing in gms proper. transaction handling fencing itself.
//        try {
//            final GroupHandle gh = ctx.getGroupHandle();
//            if(gh.isMemberAlive( failedMember ) ){
//                throw new GMSException ("Cannot raise fence on "+ failedMember + " as it is already alive");
//            }
//            gh.raiseFence(componentName, failedMember);
//            logger.log(Level.FINE, "raised fence for component "+componentName+" and member "+ failedMember);
//        }
//        catch ( GMSException e ) {
//            throw new SignalAcquireException( e );
//        }
    }

    /**
     * Must be called by client after recovery operation is complete to bring the group state up-to-date on this recovery
     * operation. Not doing so will leave a stale entry in the group's state.
     */
    @Override
    public void release() throws SignalReleaseException {
        try {
            // deprecated fencining in gms proper.
//          ctx.getGroupHandle().lowerFence(componentName, failedMember);
//          logger.log(Level.FINE, "lowered fence for component "+ componentName +" and member "+ failedMember);

            // GMS will reissue FailureRecovery if instance appointed as Recovery Agent fails before removing
            // its appointment.
            ctx.getGroupHandle().removeRecoveryAppointments(failedMember, componentName);
            failedMember = null;
        } catch (GMSException e) {
            throw new SignalReleaseException(e);
        }
    }

    public String getComponentName() {
        return componentName;
    }
}
