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

package org.glassfish.shoal.gms.mgmt.transport.grizzly;

import java.io.Serializable;

/**
 * @author Bongjae Chang
 */
public class GrizzlyPeerID implements Serializable, Comparable<GrizzlyPeerID> {

    // TODO: rework this peerID so its serialized form is only a name based java.util.UUID and the mapping of the id
    // to its network protocol is managed outside this class.
    //

    static final long serialVersionUID = 9093067296675025106L;

    // For latest release, both the ip address (host) and tcpPort are not considered part of the identity of the
    // Grizzly peerid.
    public final String host; // due to Grizzly transport hack, this host is used to send a message to a member.
                              // this value is not considered part of data structure for identity.
    public final int tcpPort; // due to Grizzly transport hack, this tcpport is used to send a message to a member.
                              // so this value must stay in the datastructure BUT is not considered part of it for
                              // comparison's sake.
    public final String multicastAddress;
    public final int multicastPort;
    transient private String toStringValue = null;

    public GrizzlyPeerID(String host, int tcpPort, String multicastAddress, int multicastPort) {
        this.host = host;
        this.multicastAddress = multicastAddress;
        this.tcpPort = tcpPort;
        this.multicastPort = multicastPort;
    }

    public String getHost() {
        return host;
    }

    public int getTcpPort() {
        return tcpPort;
    }

    public int getMulticastPort() {
        return multicastPort;
    }

    public String getMulticastAddress() {
        return multicastAddress;
    }

    // NOTE: no longer include tcpport in this calculation nor the hash calculation.
    // instance should be able to use a port within a range and still be considered same instance.
    @Override
    public boolean equals(Object other) {
        if (other instanceof GrizzlyPeerID) {
            GrizzlyPeerID otherPeerID = (GrizzlyPeerID) other;
            boolean multicastAddressCompare;
            if (multicastAddress == null) {
                multicastAddressCompare = (multicastAddress == otherPeerID.multicastAddress);
            } else {
                multicastAddressCompare = multicastAddress.equals(otherPeerID.multicastAddress);
            }
            return multicastPort == otherPeerID.multicastPort && multicastAddressCompare && host.equals(otherPeerID.getHost());
        } else {
            return false;
        }
    }

    // DO NOT INCLUDE HOST or TCP PORT in this calculation.
    //
    @Override
    public int hashCode() {
        int result = 17;
        if (multicastAddress != null) {
            result = 37 * result + multicastAddress.hashCode();
        }
        result = 37 * result + multicastPort;
        result = 37 * result + host.hashCode();
        return result;
    }

    @Override
    public String toString() {
        if (toStringValue == null) {
            toStringValue = host + ":" + tcpPort + ":" + multicastAddress + ":" + multicastPort;
        }
        return toStringValue;
    }

    @Override
    public int compareTo(GrizzlyPeerID other) {
        if (this == other) {
            return 0;
        }
        if (other == null) {
            return 1;
        }
        int result = multicastPort - other.multicastPort;
        if (result != 0) {
            return result;
        } else {
            return multicastAddress.compareTo(other.multicastAddress);
        }
    }
}
