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

package com.sun.enterprise.mgmt.transport.jxta;

import net.jxta.pipe.PipeID;
import net.jxta.peer.PeerID;
import net.jxta.protocol.PipeAdvertisement;
import net.jxta.peergroup.PeerGroupID;
import net.jxta.peergroup.PeerGroup;

/**
 * Provides a facade over the NetworkManager in order to facilitate apps that require direct access to JXTA artifacts
 * such as pipe advertisements, etc.
 */
public class JxtaNetworkManagerProxy {

    private JxtaNetworkManager manager = null;

    /**
     * Given a network manager instance return a NetworkProxy instance.
     *
     * @param manager the network manager instance
     */
    JxtaNetworkManagerProxy(JxtaNetworkManager manager) {
        this.manager = manager;
    }

    /**
     * Gets the pipeID attribute of the NetworkManager class.
     *
     * @param instanceName instance name
     * @return The pipeID value
     */
    public PipeID getPipeID(String instanceName) {
        return manager.getPipeID(instanceName);
    }

    /**
     * Gets the socketID attribute of the NetworkManager class.
     *
     * @param instanceName instance name value
     * @return The socketID value
     */
    public PipeID getSocketID(final String instanceName) {
        return manager.getSocketID(instanceName);
    }

    /**
     * Gets the peerID attribute of the NetworkManager class.
     *
     * @param instanceName instance name value
     * @return The peerID value
     */
    public PeerID getJxtaPeerID(final String instanceName) {
        return manager.getJxtaPeerID(instanceName);

    }

    /**
     * Returns the SessionQeuryPipe ID.
     *
     * @return The SessionQueryPipe Pipe ID
     */
    public PipeID getSessionQueryPipeID() {
        return manager.getSessionQueryPipeID();
    }

    /**
     * Creates a JxtaSocket pipe advertisement with a SHA1 encoded instance name pipe ID.
     *
     * @param instanceName instance name
     * @return a JxtaSocket Pipe Advertisement
     */
    public PipeAdvertisement getSocketAdvertisement(final String instanceName) {
        return manager.getSocketAdvertisement(instanceName);
    }

    /**
     * Creates a JxtaBiDiPipe pipe advertisement with a SHA1 encoded instance name pipe ID.
     *
     * @param instanceName instance name
     * @return PipeAdvertisement a JxtaBiDiPipe Pipe Advertisement
     */
    public PipeAdvertisement getPipeAdvertisement(final String instanceName) {
        return manager.getPipeAdvertisement(instanceName);
    }

    /**
     * Gets the infraPeerGroupID attribute of the NetworkManager class.
     *
     * @return The infraPeerGroupID value
     */
    public PeerGroupID getInfraPeerGroupID() {
        return manager.getInfraPeerGroupID();
    }

    /**
     * Gets the netPeerGroup instance.
     *
     * @return The netPeerGroup value
     */
    public PeerGroup getNetPeerGroup() {
        return manager.getNetPeerGroup();
    }

    /**
     * Gets the running attribute of the NetworkManager class
     *
     * @return The running value
     */
    public boolean isStarted() {
        return manager.isStarted();
    }
}
