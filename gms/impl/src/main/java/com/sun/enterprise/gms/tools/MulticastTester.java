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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.StringTokenizer;
import java.util.UUID;

/**
 * Collects user parameters and starts/stops the sender and receiver threads. This can be run directly with the main()
 * method or can be wrapped in another tool using the run() method.
 */
public class MulticastTester {

	static final StringManager sm = StringManager.getInstance();

	static final String DASH = "--";

	static final String SEP = "|";

	// these are public so they can be used in external programs
	public static final String HELP_OPTION = DASH + sm.get("help.option");
	public static final String PORT_OPTION = DASH + sm.get("port.option");
	public static final String ADDRESS_OPTION = DASH + sm.get("address.option");
	public static final String BIND_OPTION = DASH + sm.get("bind.int.option");
	public static final String TTL_OPTION = DASH + sm.get("ttl.option");
	public static final String WAIT_PERIOD_OPTION = DASH + sm.get("period.option");
	public static final String TIMEOUT_OPTION = DASH + sm.get("timeout.option");
	public static final String DEBUG_OPTION = DASH + sm.get("debug.option");

	int mcPort = 2048;
	String mcAddress = "228.9.3.1";
	String bindInterface = null;
	int ttl = -1; // will only set if specified as command line param
	long msgPeriodInMillis = 2000;
	boolean debug = false;

	// this is more useful for development, but there is a param for it
	long testerTimeoutInSeconds = 20;

	/*
	 * Called by main or external tool wrapper (such as asadmin in GlassFish). Returns the exit value.
	 */
	public int run(String[] args) {
		if (!parseArgs(args)) {
			return 1;
		}

		StringBuilder out = new StringBuilder();
		out.append(sm.get("port.set", Integer.toString(mcPort))).append("\n");
		out.append(sm.get("address.set", mcAddress)).append("\n");
		out.append(sm.get("bind.int.set", bindInterface)).append("\n");
		out.append(sm.get("period.set", msgPeriodInMillis)).append("\n");
		if (ttl != -1) {
			out.append(sm.get("ttl.set", ttl)).append("\n");
		}
		System.out.println(out.toString());

		String dataString;

		try {
			InetAddress localHost = InetAddress.getLocalHost();
			dataString = mcAddress + SEP + localHost.getHostName() + SEP + UUID.randomUUID().toString();
		} catch (UnknownHostException uhe) {
			System.err.println(sm.get("whoops", uhe.getMessage()));
			return 1;
		}

		/*
		 * The receiver thread doesn't take a bind interface because the interface only impacts multicast sending.
		 */
		MultiCastReceiverThread receiver = new MultiCastReceiverThread(mcPort, mcAddress, debug, dataString);
		MulticastSenderThread sender = new MulticastSenderThread(mcPort, mcAddress, bindInterface, ttl, msgPeriodInMillis, debug, dataString);
		receiver.start();
		sender.start();

		try {

			Thread.sleep(1000 * testerTimeoutInSeconds);

			receiver.done = true;
			sender.done = true;

			receiver.interrupt();
			receiver.join(500);
			if (receiver.isAlive()) {
				log("could not join receiver thread (expected)");
			} else {
				log("joined receiver thread");
			}

			sender.interrupt();
			sender.join(500);
			if (sender.isAlive()) {
				log("could not join sender thread");
			} else {
				log("joined sender thread");
			}

		} catch (InterruptedException ie) {
			System.err.println(sm.get("whoops", ie.getMessage()));
			ie.printStackTrace();
		}

		System.out.println(sm.get("timeout.exit", testerTimeoutInSeconds, TIMEOUT_OPTION));

		if (!receiver.receivedAnything.get()) {
			System.out.println(sm.get("no.data.for.you"));
			return 1;
		}

		return 0;
	}

	/*
	 * Can't catch every random input the user can throw at us, but let's at least make an attempt to correct honest
	 * mistakes.
	 *
	 * Return true if we should keep processing.
	 */
	private boolean parseArgs(String[] args) {
		String arg;
		try {
			for (int i = 0; i < args.length; i++) {
				arg = args[i];
				if (HELP_OPTION.equals(arg)) {
					// yes, this will return a non-zero exit code
					printHelp();
					return false;
				} else if (PORT_OPTION.equals(arg)) {
					try {
						arg = args[++i];
						mcPort = Integer.parseInt(arg);
					} catch (NumberFormatException nfe) {
						System.err.println(sm.get("bad.num.param", arg, PORT_OPTION));
						return false;
					}
				} else if (ADDRESS_OPTION.equals(arg)) {
					mcAddress = args[++i];
				} else if (BIND_OPTION.equals(arg)) {
					bindInterface = args[++i];
				} else if (TTL_OPTION.equals(arg)) {
					try {
						arg = args[++i];
						ttl = Integer.parseInt(arg);
					} catch (NumberFormatException nfe) {
						System.err.println(sm.get("bad.num.param", arg, TTL_OPTION));
						return false;
					}
				} else if (WAIT_PERIOD_OPTION.equals(arg)) {
					try {
						arg = args[++i];
						msgPeriodInMillis = Long.parseLong(arg);
					} catch (NumberFormatException nfe) {
						System.err.println(sm.get("bad.num.param", arg, WAIT_PERIOD_OPTION));
						return false;
					}
				} else if (TIMEOUT_OPTION.equals(arg)) {
					try {
						arg = args[++i];
						testerTimeoutInSeconds = Long.parseLong(arg);
					} catch (NumberFormatException nfe) {
						System.err.println(sm.get("bad.num.param", arg, TIMEOUT_OPTION));
						return false;
					}
					System.out.println(sm.get("timeout.set", testerTimeoutInSeconds));
				} else if (DEBUG_OPTION.equals(arg)) {
					System.err.println(sm.get("debug.set"));
					debug = true;
				} else {
					System.err.println(sm.get("unknown.option", arg, HELP_OPTION));
					return false;
				}
			}
		} catch (ArrayIndexOutOfBoundsException badUser) {
			System.err.println(sm.get("bad.user.param"));
			printHelp();
			return false;
		}
		return true;
	}

	private void printHelp() {
		StringBuilder sb = new StringBuilder();
		sb.append(sm.get("help.message")).append("\n");
		sb.append(HELP_OPTION).append("\n");
		sb.append(PORT_OPTION).append("\n");
		sb.append(ADDRESS_OPTION).append("\n");
		sb.append(BIND_OPTION).append("\n");
		sb.append(TTL_OPTION).append("\n");
		sb.append(WAIT_PERIOD_OPTION).append("\n");
		sb.append(TIMEOUT_OPTION).append("\n");
		sb.append(DEBUG_OPTION).append("\n");
		System.out.println(sb.toString());
	}

	private void log(String msg) {
		if (debug) {
			System.err.println("MainThread: " + msg);
		}
	}

	public static void main(String[] args) {
		MulticastTester tester = new MulticastTester();
		System.exit(tester.run(args));
	}

	/*
	 * Make the output a little more readable. The expected format is prefix|host|uuid. If a message received does not start
	 * with the prefix, it is ignored and this method isn't called.
	 */
	static String trimDataString(String s) {
		StringTokenizer st = new StringTokenizer(s, SEP);
		st.nextToken();
		return st.nextToken();
	}
}
