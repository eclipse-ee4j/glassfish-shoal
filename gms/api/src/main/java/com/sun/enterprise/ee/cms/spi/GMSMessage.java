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

package com.sun.enterprise.ee.cms.spi;

import java.io.Serializable;
import java.nio.charset.Charset;

/**
 * This is a wrapper Serializable so that a message sent to a remote member can be further filtered to a target
 * component in that remote member.
 * 
 * @author Shreedhar Ganapathy Date: Mar 14, 2005
 * @version $Revision$
 */

public class GMSMessage implements Serializable {
	static final long serialVersionUID = -5485293884999776323L;

	private final String componentName;
	private final byte[] message;
	private final String groupName;
	private final Long startTime;

	public GMSMessage(final String componentName, final byte[] message, final String groupName, final Long startTime) {
		if (componentName == null) {
			throw new IllegalArgumentException("parameter componentName must be non-null");
		}
		if (groupName == null) {
			throw new IllegalArgumentException("parameter groupName must be non-null");
		}

		// a componentName of null acts as a wildcard and the message is delivered to all
		// target components. see GroupHandle.sendMessage javadoc for details.
		this.componentName = componentName;
		this.message = message;
		this.groupName = groupName;
		this.startTime = startTime;
	}

	public String getComponentName() {
		return componentName;
	}

	public byte[] getMessage() {
		return message;
	}

	public String getGroupName() {
		return groupName;
	}

	public long getStartTime() {
		if (startTime == null) {
			return 0;
		}
		return startTime;
	}

	public String toString() {
		StringBuffer sb = new StringBuffer(30);
		sb.append("GMSMessage to componentName:").append(componentName);
		sb.append(" message size:" + message.length);
		sb.append(" payload:");
		if (message.length < 30) {
			sb.append(new String(message, Charset.defaultCharset()));
		} else {
			sb.append(new String(message, Charset.defaultCharset()).substring(0, 15));
		}
		sb.append(" group:").append(groupName);
		return sb.toString();
	}
}
