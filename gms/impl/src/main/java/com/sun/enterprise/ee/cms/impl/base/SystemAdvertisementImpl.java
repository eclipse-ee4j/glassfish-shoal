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

import com.sun.enterprise.ee.cms.logging.GMSLogDomain;

import java.io.Serializable;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.net.URI;
import java.text.MessageFormat;
import java.util.Date;

/**
 * This class is a default system advertisement and implements
 * {@link com.sun.enterprise.ee.cms.impl.base.SystemAdvertisement}
 * 
 * @author Bongjae Chang
 */
public class SystemAdvertisementImpl implements SystemAdvertisement {

	static final long serialVersionUID = -6635044542343387957L;
	private static final Logger LOG = GMSLogDomain.getLogger(GMSLogDomain.GMS_LOGGER);
	private static final List<String> EMPTY_LIST = new ArrayList<String>();
	private String hwarch;
	private String hwvendor;
	private PeerID id;
	private String name;
	private String osname;
	private String osversion;
	private String osarch;
	private List<String> endpointAddresses = null;
	private HashMap<String, String> customTags = null;

	public SystemAdvertisementImpl() {
	}

	/**
	 * Sets the hWArch attribute of the SystemAdvertisement object
	 *
	 * @param hwarch The new hWArch value
	 */
	public void setHWArch(final String hwarch) {
		this.hwarch = hwarch;
	}

	/**
	 * Sets the OSArch attribute of the SystemAdvertisement object
	 *
	 * @param osarch The new hWArch value
	 */
	public void setOSArch(final String osarch) {
		this.osarch = osarch;
	}

	/**
	 * Sets the hWVendor attribute of the SystemAdvertisement object
	 *
	 * @param hwvendor The new hWVendor value
	 */
	public void setHWVendor(final String hwvendor) {
		this.hwvendor = hwvendor;
	}

	/**
	 * sets the unique id
	 *
	 * @param id The id
	 */
	public void setID(final PeerID id) {
		this.id = id;
	}

	/**
	 * Sets the network interface's address in the form of a URI
	 *
	 * @param value new uri (tcp://host:port) in IPv4 or (tcp://[host]:port) in IPv6
	 */
	public void addEndpointAddress(final String value) {
		if (endpointAddresses == null) {
			endpointAddresses = new ArrayList<String>();
		}
		endpointAddresses.add(value);
	}

	/**
	 * API for setting the IP addresses for all the network interfaces
	 *
	 * @param endpoints endpoint addresses
	 */
	public void setEndpointAddresses(final List<String> endpoints) {
		for (String endpoint : endpoints) {
			addEndpointAddress(endpoint);
		}
	}

	/**
	 * Sets the name attribute of the DeviceAdvertisement object
	 *
	 * @param name The new name value
	 */
	public void setName(final String name) {
		this.name = name;
	}

	/**
	 * Sets the oSName attribute of the SystemAdvertisement object
	 *
	 * @param osname The new oSName value
	 */
	public void setOSName(final String osname) {
		this.osname = osname;
	}

	/**
	 * Sets the oSVersion attribute of the SystemAdvertisement object
	 *
	 * @param osversion The new oSVersion value
	 */
	public void setOSVersion(final String osversion) {
		this.osversion = osversion;
	}

	public void setCustomTags(final Map<String, String> tags) {
		if (tags == null) {
			return;
		}
		if (customTags == null) {
			customTags = new HashMap<String, String>();
		}
		customTags.putAll(tags);
	}

	@SuppressWarnings("unchecked")
	public Map<String, String> getCustomTags() {
		if (customTags == null) {
			return new HashMap<String, String>();
		} else {
			return (HashMap<String, String>) customTags.clone();
		}
	}

	public void setCustomTag(final String tag, final String value) {
		if (customTags == null) {
			customTags = new HashMap<String, String>();
		}
		customTags.put(tag, value);
	}

	/**
	 * Gets the hWArch attribute of the SystemAdvertisement object
	 *
	 * @return The hWArch value
	 */
	public String getHWArch() {
		return hwarch;
	}

	/**
	 * Gets the OSArch attribute of the SystemAdvertisement object
	 *
	 * @return The OSArch value
	 */
	public String getOSArch() {
		return osarch;
	}

	/**
	 * Gets the hWVendor attribute of the SystemAdvertisement object
	 *
	 * @return The hWVendor value
	 */
	public String getHWVendor() {
		return hwvendor;
	}

	/**
	 * returns the id of the device
	 *
	 * @return ID the device id
	 */
	public PeerID getID() {
		return id;
	}

	/**
	 * Gets the address of the network interface in the form of URI
	 *
	 * @return the list of URIs for all the network interfaces
	 */
	public List<String> getEndpointAddresses() {
		if (endpointAddresses == null) {
			return EMPTY_LIST;
		} else {
			return endpointAddresses;
		}
	}

	public List<URI> getURIs() {
		List<String> endpoints = getEndpointAddresses();
		List<URI> uriList = new ArrayList<URI>(endpoints.size());

		for (int i = 0; i < endpoints.size(); i++) {
			try {
				uriList.add(new URI((String) endpoints.get(i)));
			} catch (java.net.URISyntaxException e) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.log(Level.FINE, "Exception occurred : ", e);
				}
			}
		}
		return uriList;
	}

	/**
	 * Gets the name attribute of the SystemAdvertisement object
	 *
	 * @return The name value
	 */
	public String getName() {
		return name;
	}

	/**
	 * Gets the OSName attribute of the SystemAdvertisement object
	 *
	 * @return The OSName value
	 */
	public String getOSName() {
		return osname;
	}

	/**
	 * Gets the OSVersion attribute of the SystemAdvertisement object
	 *
	 * @return The OSVersion value
	 */
	public String getOSVersion() {
		return osversion;
	}

	public String getCustomTagValue(final String tagName) throws NoSuchFieldException {
		if (customTags != null) {
			return customTags.get(tagName);
		} else {
			throw new NoSuchFieldException(tagName);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public SystemAdvertisement clone() throws CloneNotSupportedException {
		try {
			SystemAdvertisement likeMe = (SystemAdvertisement) super.clone();

			likeMe.setID(getID());
			likeMe.setName(getName());
			likeMe.setOSName(getName());
			likeMe.setOSVersion(getOSVersion());
			likeMe.setOSArch(getOSArch());
			if (endpointAddresses != null && !endpointAddresses.isEmpty()) {
				likeMe.setEndpointAddresses(getEndpointAddresses());
			}
			likeMe.setHWArch(getHWArch());
			likeMe.setHWVendor(getHWVendor());
			if (customTags != null && !customTags.isEmpty()) {
				likeMe.setCustomTags(getCustomTags());
			}
			return likeMe;
		} catch (CloneNotSupportedException impossible) {
			throw new Error("Object.clone() threw CloneNotSupportedException", impossible);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean equals(final Object obj) {
		boolean retVal = false;
		if (this == obj) {
			retVal = true;
		} else if (obj instanceof SystemAdvertisement) {
			final SystemAdvertisement adv = (SystemAdvertisement) obj;
			retVal = getID().equals(adv.getID());
		}
		return retVal;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int hashCode() {
		int result = 17;
		result = 37 * result + getID().hashCode();
		String name = getName();
		if (name != null) {
			result = 37 * result + name.hashCode();
		}
		return result;
	}

	/**
	 * {@inheritDoc}
	 */
	public int compareTo(final SystemAdvertisement other) {
		return getID().compareTo(other.getID());
	}

	/**
	 * {@inheritDoc}
	 */
	public String toString() {
		StringBuffer sb = new StringBuffer(120);
		sb.append("PEERID[");
		sb.append(id.toString());
		sb.append("]\n");
		sb.append("CUSTOMTAGS[");
		boolean firstTime = true;
		for (Map.Entry<String, String> entry : customTags.entrySet()) {
			if (!firstTime) {
				sb.append(", ");
			} else {
				firstTime = false;
			}
			String key = entry.getKey();
			sb.append(key.toUpperCase());
			if (key.equals(CustomTagNames.START_TIME.toString())) {
				sb.append("[");
				sb.append(MessageFormat.format("{0,time,full} on {0,date}", new Date(Long.parseLong(entry.getValue()))));
				sb.append("]");
			} else {
				sb.append(":");
				sb.append(entry.getValue());
			}
		}
		sb.append("], ");
		sb.append("ENDPONTADDRESSES[");
		sb.append(endpointAddresses.toString());
		sb.append("], ");
		sb.append("NAME:");
		sb.append(name);
		sb.append(", ");
		sb.append("OSNAME:");
		sb.append(osname);
		sb.append(", ");
		sb.append("OSVERSION:");
		sb.append(osversion);
		sb.append(", ");
		sb.append("OSARCH:");
		sb.append(osarch);
		sb.append(", ");
		sb.append("HWARCH:");
		sb.append(hwarch);
		sb.append(", ");
		sb.append("HWVENDOR:");
		sb.append(hwvendor);
		return sb.toString();
	}
}
