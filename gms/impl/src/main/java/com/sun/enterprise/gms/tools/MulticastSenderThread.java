/*
 * Copyright (c) 2010, 2018 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.enterprise.gms.tools;

import com.sun.enterprise.ee.cms.logging.GMSLogDomain;
import com.sun.enterprise.mgmt.transport.NetworkUtility;

import static com.sun.enterprise.ee.cms.core.GMSConstants.MINIMUM_MULTICAST_TIME_TO_LIVE;
import java.io.IOException;
import java.net.*;
import java.nio.charset.Charset;
import java.util.logging.Level;

/**
 * Used to periodically send multicast messages.
 */
public class MulticastSenderThread extends Thread {

	static final StringManager sm = StringManager.getInstance();

	volatile boolean done = false;

	int mcPort;
	String mcAddress;
	String bindInterface;
	int ttl;
	long msgPeriodInMillis;
	boolean debug;
	String dataString;

	public MulticastSenderThread(int mcPort, String mcAddress, String bindInterface, int ttl, long msgPeriodInMillis, boolean debug, String dataString) {
		super("McastSender");
		this.mcPort = mcPort;
		this.mcAddress = mcAddress;
		this.bindInterface = bindInterface;
		this.ttl = ttl;
		this.msgPeriodInMillis = msgPeriodInMillis;
		this.debug = debug;
		this.dataString = dataString;
	}

	@Override
	public void run() {
		InetAddress group = null;
		MulticastSocket socket = null;
		try {
			byte[] data = dataString.getBytes(Charset.defaultCharset());
			group = InetAddress.getByName(mcAddress);
			DatagramPacket datagramPacket = new DatagramPacket(data, data.length, group, mcPort);
			socket = new MulticastSocket(mcPort);

			if (bindInterface != null) {
				InetAddress iaddr = InetAddress.getByName(bindInterface);
				log("InetAddress.getByName returned: " + iaddr);

				// make sure the network interface is valid
				NetworkInterface ni = NetworkInterface.getByInetAddress(iaddr);
				if (ni != null && NetworkUtility.isUp(ni)) {
					socket.setInterface(iaddr);
					System.out.println(String.format(
					        sm.get("configured.bindinterface", bindInterface, ni.getName(), ni.getDisplayName(), NetworkUtility.isUp(ni), ni.isLoopback())));
				} else {
					if (ni != null) {
						System.out.println(String.format(
						        sm.get("invalid.bindinterface", bindInterface, ni.getName(), ni.getDisplayName(), NetworkUtility.isUp(ni), ni.isLoopback())));
					} else {
						System.err.println(sm.get("nonexistent.bindinterface", bindInterface));
					}
					iaddr = getFirstAddress();
					log("setting socket to: " + iaddr + " instead");
					socket.setInterface(iaddr);
				}
			} else {
				InetAddress iaddr = getFirstAddress();
				log("setting socket to: " + iaddr);
				socket.setInterface(iaddr);
			}

			if (ttl != -1) {
				try {
					socket.setTimeToLive(ttl);
				} catch (Exception e) {
					System.err.println(sm.get("could.not.set.ttl", e.getLocalizedMessage()));
				}
			} else {
				try {
					int defaultTTL = socket.getTimeToLive();
					if (defaultTTL < MINIMUM_MULTICAST_TIME_TO_LIVE) {
						log(String.format("The default TTL for the socket is %d. " + "Setting it to minimum %d instead.", defaultTTL,
						        MINIMUM_MULTICAST_TIME_TO_LIVE));
						socket.setTimeToLive(MINIMUM_MULTICAST_TIME_TO_LIVE);
					}
				} catch (IOException ioe) {
					// who cares? we'll print it again a couple lines down
				}
			}

			// 'false' means do NOT disable
			log("setting loopback mode false on mcast socket");
			socket.setLoopbackMode(false);

			try {
				log(String.format("socket time to live set to %s", socket.getTimeToLive()));
			} catch (IOException ioe) {
				log(ioe.getLocalizedMessage());
			}

			log(String.format("joining group: %s", group.toString()));
			socket.joinGroup(group);
			if (!debug) {
				dataString = MulticastTester.trimDataString(dataString);
			}
			System.out.println(sm.get("sending.message", dataString, msgPeriodInMillis));

			while (!done) {
				socket.send(datagramPacket);
				try {
					Thread.sleep(msgPeriodInMillis);
				} catch (InterruptedException ie) {
					log("interrupted");
					break;
				}
			}
		} catch (Exception e) {
			System.err.println(sm.get("whoops", e.toString()));
		} finally {
			if (socket != null) {
				if (group != null) {
					log("socket leaving group");
					try {
						socket.leaveGroup(group);
					} catch (IOException ioe) {
						System.err.println(sm.get("ignoring.exception.leaving", getName(), ioe.toString()));
					}
				}
				log("closing socket");
				socket.close();
			}
		}
	}

	// utility so we can silence the shoal logger
	private InetAddress getFirstAddress() throws IOException {
		if (!debug) {
			GMSLogDomain.getLogger(GMSLogDomain.GMS_LOGGER).setLevel(Level.SEVERE);
		}
		return NetworkUtility.getFirstInetAddress(false);
	}

	private void log(String msg) {
		if (debug) {
			System.err.println(String.format("%s: %s", getName(), msg));
		}
	}
}
