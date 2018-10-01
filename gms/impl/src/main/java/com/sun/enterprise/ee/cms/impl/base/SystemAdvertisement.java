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

package com.sun.enterprise.ee.cms.impl.base;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.net.URI;

/**
 * This interface provides system characteristics
 *
 * i.g. HW/SW configuration, CPU load, etc...
 * 
 * @author Bongjae Chang
 */
public interface SystemAdvertisement extends Comparable<SystemAdvertisement>, Cloneable, Serializable {

	static final long serialVersionUID = 4520670615616793233L;

	public static final String OSNameTag = "OSName";
	public static final String OSVersionTag = "OSVer";
	public static final String OSarchTag = "osarch";
	public static final String hwarchTag = "hwarch";
	public static final String hwvendorTag = "hwvendor";
	public static final String idTag = "ID";
	public static final String ipTag = "ip";
	public static final String nameTag = "name";

	/**
	 * Sets the hWArch attribute of the SystemAdvertisement object
	 *
	 * @param hwarch The new hWArch value
	 */
	public void setHWArch(final String hwarch);

	/**
	 * Sets the OSArch attribute of the SystemAdvertisement object
	 *
	 * @param osarch The new hWArch value
	 */
	public void setOSArch(final String osarch);

	/**
	 * Sets the hWVendor attribute of the SystemAdvertisement object
	 *
	 * @param hwvendor The new hWVendor value
	 */
	public void setHWVendor(final String hwvendor);

	/**
	 * sets the unique id
	 *
	 * @param id The id
	 */
	public void setID(final PeerID id);

	/**
	 * Sets the network interface's address in the form of a URI
	 *
	 * @param value new uri (tcp://host:port)
	 */
	public void addEndpointAddress(final String value);

	/**
	 * API for setting the IP addresses for all the network interfaces
	 *
	 * @param endpoints endpoint addresses
	 */
	public void setEndpointAddresses(final List<String> endpoints);

	/**
	 * Sets the name attribute of the DeviceAdvertisement object
	 *
	 * @param name The new name value
	 */
	public void setName(final String name);

	/**
	 * Sets the oSName attribute of the SystemAdvertisement object
	 *
	 * @param osname The new oSName value
	 */
	public void setOSName(final String osname);

	/**
	 * Sets the oSVersion attribute of the SystemAdvertisement object
	 *
	 * @param osversion The new oSVersion value
	 */
	public void setOSVersion(final String osversion);

	public void setCustomTag(final String tag, final String value);

	public void setCustomTags(final Map<String, String> tags);

	/**
	 * Gets the hWArch attribute of the SystemAdvertisement object
	 *
	 * @return The hWArch value
	 */
	public String getHWArch();

	/**
	 * Gets the OSArch attribute of the SystemAdvertisement object
	 *
	 * @return The OSArch value
	 */
	public String getOSArch();

	/**
	 * Gets the hWVendor attribute of the SystemAdvertisement object
	 *
	 * @return The hWVendor value
	 */
	public String getHWVendor();

	/**
	 * returns the id of the device
	 *
	 * @return ID the device id
	 */
	public PeerID getID();

	/**
	 * Gets the address of the network interface in the form of URI
	 *
	 * @return the list of URIs for all the network interfaces
	 */
	public List<String> getEndpointAddresses();

	public List<URI> getURIs();

	/**
	 * Gets the name attribute of the SystemAdvertisement object
	 *
	 * @return The name value
	 */
	public String getName();

	/**
	 * Gets the OSName attribute of the SystemAdvertisement object
	 *
	 * @return The OSName value
	 */
	public String getOSName();

	/**
	 * Gets the OSVersion attribute of the SystemAdvertisement object
	 *
	 * @return The OSVersion value
	 */
	public String getOSVersion();

	public String getCustomTagValue(final String tagName) throws NoSuchFieldException;

	public Map<String, String> getCustomTags();
}
