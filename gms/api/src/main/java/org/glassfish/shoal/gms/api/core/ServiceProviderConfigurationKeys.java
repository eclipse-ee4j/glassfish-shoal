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
 * Provides the keys that correspond to properties within group communication providers that can be configured. Note
 * that this is not exhaustive enough to cover all possible configurations in different group communication libraries.
 *
 * @author Shreedhar Ganapathy
 */
public enum ServiceProviderConfigurationKeys {
    /**
     * unreserved valid multicast address in the range 224.0.0.0 through 239.255.255.255 See
     * http://www.iana.org/assignments/multicast-addresses for more details on valid addresses. If not using multicast, do
     * not specify this property
     */
    MULTICASTADDRESS,
    /**
     * A valid port. If not using multicast, do not specify this property
     */
    MULTICASTPORT,
    /**
     * The timeout in milliseconds which will be used to send out periodic heartbeats. This is also the period that will be
     * used to check for update to the health state of each member process.
     */
    FAILURE_DETECTION_TIMEOUT,
    /**
     * Number of periodic heartbeats than can be missed in order to be determined a suspected failure. Once the retries have
     * been exhausted, a FailureSuspectedNotificationSignal is sent out to all GMS clients who have registered for this
     * event.
     */
    FAILURE_DETECTION_RETRIES,
    /**
     * The timeout in milliseconds which will be used to wait and verify that the suspected member has indeed failed. Once
     * confirmed failed, a FailureNotificationSignal is sent out to all GMS clients who have registered for this event.
     */
    FAILURE_VERIFICATION_TIMEOUT,
    /**
     * The timeout in milliseconds that each member would wait to discover a group leader. If no group leader was found
     * within this timeout, the member announces itself as the assumed and assigned group leader.
     */
    DISCOVERY_TIMEOUT,
    /**
     * Setting the value of this key to true, would make all application level messages sent by this member to also be
     * received by this member in addition to the target members to whom the message was sent.
     */
    LOOPBACK,
    /**
     * Represents a key whose value is set to true if this node will be a bootstrapping host for other members to use for
     * discovery purposes. This is particularly useful when multicast traffic is not supported in the network or cluster
     * members are located outside a multicast supported network area. Setting the value of this to true requires specifying
     * a URI corresponding to this member process's IP address and port with tcp protocol, as a value in the
     * VIRTUAL_MULTICAST_URI_LIST property. See below for the VIRTUAL_MULTICAST_URI_LIST property
     */
    IS_BOOTSTRAPPING_NODE,

    /**
     * This enum represents a key the value of which is a comma separated list of initial bootstrapping tcp addresses. This
     * address list must be specified on all members of the cluster through this property.
     * <p>
     * Typically an address uri would be specified as tcp://ipaddress:port
     * </p>
     * The port here could be any available unoccupied port.<br>
     * Specifying this list is helpful particularly when cluster members are located beyond one subnet or multicast traffic
     * is disabled.
     *
     * Deprecated and used only by Shoal GMS over JXTA transport.
     */
    VIRTUAL_MULTICAST_URI_LIST,

    /**
     * Enables a starting GMS member to discover and join its group without requiring UDP multicast.
     *
     * This enum represents a key with value that is a comma separated list of initial bootstrapping uri addresses. There
     * must be a GMS member running at one of these bootstrapping addresses to enable the current member to join the group.
     * This list must be specified on all members of the cluster through this property.
     *
     * <p>
     * The bootstrap address uri is specified as protocol://ipaddress:port. The protocol and/or the port are optional. Their
     * values will be defaulted when they are left out. Thus, allowing the list to be a comma separated list of ip addresses
     * or of protcol://ipaddress. An example address uri is tcp://someDnsHostName:port or tcp://ipAddress:port.
     *
     * <p>
     * The default port is the GMS property TCPSTARTPORT. * When the scheme is ommitted, the default scheme is tcp. Initial
     * implementation will only support tcp.
     *
     * <p>
     * If this value is set to an empty list, it indicates that this is the first instance starting in the cluster. Setting
     * this value to empty string indicates that UDP multicast is disabled.
     *
     * <p>
     * When the list is not empty, at least one of the instances refernenced by the uri must be able to be contacted to
     * enable joining an active GMS group.
     *
     * <p>
     * TODO: multi-home machine considerations. On multi-home machines, it is quite probable that one will be required to
     * set GMS property BIND_INTERFACE_ADDRESS. It is network stack specific which network interface address is selected
     * when there are multiple enabled network interfaces.
     */
    DISCOVERY_URI_LIST,

    /**
     * If you wish to specify a particular network interface that should be used for all group communication messages, use
     * this key and specify an interface address. This is the address which Shoal would pass down to a service provider such
     * as Jxta to bind to for communication.
     */
    BIND_INTERFACE_ADDRESS,
    /**
     * Maximum time that the health monitoring protocol would wait for a reachability query to block for a response. After
     * this time expires, the health monitoring protocol would report a failure based on the fact that an endpoint was
     * unreachable for this length of time. <br>
     * Specifying this property is typically helpful in determining hardware and network failures within a shorter time
     * period than the OS/System configured TCP retransmission timeout. On many OSs, the TCP retransmission timeout is about
     * 10 minutes.
     * <p>
     * The default timeout for this property is set to 30 seconds.
     * </p>
     * <p>
     * As an example, let's take the case of 2 machines A and B hosting instances X and Y, respectively. Machine B goes down
     * due to a power outage or a hardware failure.
     * </p>
     * <p>
     * Under normal circumstances, Instance X on machine A would not know of the unavailability of Instance X on Machine B
     * until the TCP retransmission timeout (typically 10 minutes) has passed. By setting this property's value to some
     * lower time threshold, instance X would determine Y's failure due to Machine B's failure, a lot earlier.
     * </p>
     * <p>
     * Related to this key is the FAILURE_DETECTION_TCP_RETRANSMIT_PORT. See below
     * </p>
     */
    FAILURE_DETECTION_TCP_RETRANSMIT_TIMEOUT,
    /**
     * <p>
     * This value of this key is a port common to all cluster members where a socket will be attempted to be created when a
     * particular instance's configured periodic heartbeats have been missed for the max retry times. The port number
     * specified should be an available unoccupied port on all machines involved in the cluster. If the socket creation
     * attempt blocks for the above-mentioned FAILURE_DETECTION_TCP_RETRANSMIT_TIMEOUT, then the health monitoring protocol
     * would return a failure event.
     * </p>
     */
    FAILURE_DETECTION_TCP_RETRANSMIT_PORT,
    /**
     * <p>
     * OPTIONAL: not a meaningful option for all implementations. Specify the max number of threads allocated to run
     * handlers for incoming multicast messages.
     */
    MULTICAST_POOLSIZE,

    /**
     * <p>
     * Enable setting how large fixed incoming message queue is.
     */
    INCOMING_MESSAGE_QUEUE_SIZE,

    /**
     * Max message length. This length is not just application payload but includes message overhead (such as headers) that
     * is implementation and transport dependent.
     */
    MAX_MESSAGE_LENGTH,

    /**
     * Configure number of threads for incoming message processing.
     */
    INCOMING_MESSAGE_THREAD_POOL_SIZE,

    /**
     * Set MONITORING frequency in seconds.
     */
    MONITORING
}
