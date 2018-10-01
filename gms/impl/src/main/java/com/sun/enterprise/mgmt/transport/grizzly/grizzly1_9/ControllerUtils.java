/*
 * Copyright (c) 2007, 2018 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.enterprise.mgmt.transport.grizzly.grizzly1_9;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;

import com.sun.grizzly.Controller;
import com.sun.grizzly.ControllerStateListenerAdapter;
import com.sun.grizzly.util.WorkerThreadImpl;

/**
 * @author Alexey Stashok
 */
public class ControllerUtils {

	/**
	 * Start controller in seperate thread
	 *
	 * @param controller the controller
	 */
	public static void startController(final Controller controller) {
		final CountDownLatch latch = new CountDownLatch(1);
		controller.addStateListener(new ControllerStateListenerAdapter() {
			@Override
			public void onReady() {
				latch.countDown();
			}

			@Override
			public void onException(Throwable e) {
				if (latch.getCount() > 0) {
					Controller.logger().log(Level.SEVERE, "Exception during " + "starting the controller", e);
					latch.countDown();
				} else {
					Controller.logger().log(Level.SEVERE, "Exception during " + "controller processing", e);
				}
			}
		});

		new WorkerThreadImpl("ControllerWorker", controller).start();

		try {
			latch.await();
		} catch (InterruptedException ex) {
		}

		if (!controller.isStarted()) {
			throw new IllegalStateException("Controller is not started!");
		}
	}

	/**
	 * Stop controller in seperate thread
	 *
	 * @param controller the controller
	 */
	public static void stopController(Controller controller) {
		try {
			controller.stop();
		} catch (IOException e) {
		}
	}

	public static void startControllers(Controller[] controllers) {
		startControllers(Arrays.asList(controllers));
	}

	public static void startControllers(Collection<Controller> controllers) {
		for (Controller controller : controllers) {
			startController(controller);
		}
	}

	public static void stopControllers(Controller[] controllers) {
		stopControllers(Arrays.asList(controllers));
	}

	public static void stopControllers(Collection<Controller> controllers) {
		for (Controller controller : controllers) {
			stopController(controller);
		}
	}
}
