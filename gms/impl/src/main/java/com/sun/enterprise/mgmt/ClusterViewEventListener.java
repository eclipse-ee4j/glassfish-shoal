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

package com.sun.enterprise.mgmt;

import java.util.EventListener;

/**
 * The listener interface for receiving ClusterViewManager events.
 * <p>
 * The following example illustrates how to implement a {@link com.sun.enterprise.mgmt.ClusterViewEventListener}:
 *
 * <pre>
 * <tt>
 * ClusterViewEventListener myListener = new ClusterViewEventListener() {
 *   public void clusterViewEvent(int event, , SystemAdvertisement advertisement) {
 *        if (event == ClusterViewManager.ADD_EVENT) {
 *          .....
 *        }
 *   }
 * }
 * </tt>
 * </pre>
 */

public interface ClusterViewEventListener extends EventListener {

	/**
	 * Called when a cluster view event occurs.
	 *
	 * @param event The event that occurred.
	 * @param clusterView the current membership snapshot after the event.
	 */
	void clusterViewEvent(ClusterViewEvent event, ClusterView clusterView);
}
