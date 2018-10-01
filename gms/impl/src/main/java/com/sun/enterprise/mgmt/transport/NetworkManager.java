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

package com.sun.enterprise.mgmt.transport;

import java.io.IOException;
import java.util.Map;

import com.sun.enterprise.ee.cms.impl.base.PeerID;

/**
 * This interface has common APIs for network managements
 *
 * According to a kind of transport layers, this interface will be implemented adequately. Currently,
 * {@link com.sun.enterprise.mgmt.ClusterManager} initializes this with calling
 * {@link NetworkManager#initialize(String, String, java.util.Map)}. After initialization,
 * {@link com.sun.enterprise.mgmt.transport.NetworkManager#start()} ) will be called.
 *
 * @author Bongjae Chang
 */
public interface NetworkManager extends MulticastMessageSender, MessageSender {

	/**
	 * Initializes this network manager with given params and properties
	 *
	 * @param groupName group name
	 * @param instanceName instance name
	 * @param properties specific properties
	 * @throws IOException if an unexpected error occurs
	 */
	void initialize(final String groupName, final String instanceName, final Map properties) throws IOException;

	/**
	 * Starts this network manager
	 *
	 * This method will be called after
	 * {@link com.sun.enterprise.mgmt.transport.NetworkManager#initialize(String, String, java.util.Map)} internally
	 *
	 * @throws IOException if an I/O error occurs
	 */
	void start() throws IOException;

	/**
	 * Stops this network manager
	 *
	 * For cleaning up remaining values and finishing I/O operation, this method could be used
	 *
	 * @throws IOException if an I/O error occurs
	 */
	void stop() throws IOException;

	/**
	 * Adds the {@link com.sun.enterprise.mgmt.transport.MessageListener}
	 *
	 * @param messageListener a message listener which should be registered on this network manager
	 */
	void addMessageListener(final MessageListener messageListener);

	/**
	 * Removes the {@link com.sun.enterprise.mgmt.transport.MessageListener}
	 *
	 * @param messageListener a message listener which should be removed
	 */
	void removeMessageListener(final MessageListener messageListener);

	/**
	 * Processes a received {@link Message}
	 *
	 * In this process, inbound {@link Message} will be wrapped into {@link MessageEvent} and be delivered to registered
	 * {@link MessageListener} with corresponding to the message type
	 *
	 * @param message inbound message
	 * @param piggyback piggyback
	 */
	void receiveMessage(Message message, Map piggyback);

	/**
	 * Returns local {@link PeerID}
	 *
	 * @return peer id
	 */
	PeerID getLocalPeerID();

	/**
	 * Returns the proper {@link PeerID} corresponding with a given instance name
	 *
	 * @param instanceName instance name
	 * @return peer id
	 */
	PeerID getPeerID(final String instanceName);

	/**
	 * Add the <code>peerID</code> to this network manager
	 *
	 * @param peerID the peer Id
	 */
	void addRemotePeer(final PeerID peerID);

	/**
	 * Removes the <code>peerID</code> from this network manager
	 *
	 * @param peerID the peer Id
	 */
	void removePeerID(final PeerID peerID);

	/**
	 * Check whether the suspicious peer is alive or not
	 *
	 * This API is mainly used in {@link com.sun.enterprise.mgmt.HealthMonitor} in order to determine the failure member
	 *
	 * @param peerID peer id
	 * @return true if the peer is still alive, otherwise false
	 */
	boolean isConnected(final PeerID peerID);

	/**
	 * Returns a {@link MessageSender} corresponding with transport type
	 *
	 * @param transport transport type. {@link ShoalMessageSender#TCP_TRANSPORT} or
	 * {@link ShoalMessageSender#UDP_TRANSPORT}'s integer value
	 * @return a {@link MessageSender}'s instance which this network manager contains
	 */
	MessageSender getMessageSender(int transport);

	/**
	 * Returns a {@link MulticastMessageSender}
	 *
	 * @return a {@link MulticastMessageSender}'s instance which this network manager contains
	 */
	MulticastMessageSender getMulticastMessageSender();
}
