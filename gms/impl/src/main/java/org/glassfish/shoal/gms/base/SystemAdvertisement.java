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

package org.glassfish.shoal.gms.base;

import java.io.Serializable;
import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * This interface provides system characteristics
 *
 * i.g. HW/SW configuration, CPU load, etc...
 *
 * @author Bongjae Chang
 */
public interface SystemAdvertisement extends Comparable<SystemAdvertisement>, Cloneable, Serializable {

    long serialVersionUID = 4520670615616793233L;

    String OSNameTag = "OSName";
    String OSVersionTag = "OSVer";
    String OSarchTag = "osarch";
    String hwarchTag = "hwarch";
    String hwvendorTag = "hwvendor";
    String idTag = "ID";
    String ipTag = "ip";
    String nameTag = "name";

    /**
     * Sets the hWArch attribute of the SystemAdvertisement object
     *
     * @param hwarch The new hWArch value
     */
    void setHWArch(final String hwarch);

    /**
     * Sets the OSArch attribute of the SystemAdvertisement object
     *
     * @param osarch The new hWArch value
     */
    void setOSArch(final String osarch);

    /**
     * Sets the hWVendor attribute of the SystemAdvertisement object
     *
     * @param hwvendor The new hWVendor value
     */
    void setHWVendor(final String hwvendor);

    /**
     * sets the unique id
     *
     * @param id The id
     */
    void setID(final PeerID id);

    /**
     * Sets the network interface's address in the form of a URI
     *
     * @param value new uri (tcp://host:port)
     */
    void addEndpointAddress(final String value);

    /**
     * API for setting the IP addresses for all the network interfaces
     *
     * @param endpoints endpoint addresses
     */
    void setEndpointAddresses(final List<String> endpoints);

    /**
     * Sets the name attribute of the DeviceAdvertisement object
     *
     * @param name The new name value
     */
    void setName(final String name);

    /**
     * Sets the oSName attribute of the SystemAdvertisement object
     *
     * @param osname The new oSName value
     */
    void setOSName(final String osname);

    /**
     * Sets the oSVersion attribute of the SystemAdvertisement object
     *
     * @param osversion The new oSVersion value
     */
    void setOSVersion(final String osversion);

    void setCustomTag(final String tag, final String value);

    void setCustomTags(final Map<String, String> tags);

    /**
     * Gets the hWArch attribute of the SystemAdvertisement object
     *
     * @return The hWArch value
     */
    String getHWArch();

    /**
     * Gets the OSArch attribute of the SystemAdvertisement object
     *
     * @return The OSArch value
     */
    String getOSArch();

    /**
     * Gets the hWVendor attribute of the SystemAdvertisement object
     *
     * @return The hWVendor value
     */
    String getHWVendor();

    /**
     * returns the id of the device
     *
     * @return ID the device id
     */
    PeerID getID();

    /**
     * Gets the address of the network interface in the form of URI
     *
     * @return the list of URIs for all the network interfaces
     */
    List<String> getEndpointAddresses();

    List<URI> getURIs();

    /**
     * Gets the name attribute of the SystemAdvertisement object
     *
     * @return The name value
     */
    String getName();

    /**
     * Gets the OSName attribute of the SystemAdvertisement object
     *
     * @return The OSName value
     */
    String getOSName();

    /**
     * Gets the OSVersion attribute of the SystemAdvertisement object
     *
     * @return The OSVersion value
     */
    String getOSVersion();

    String getCustomTagValue(final String tagName) throws NoSuchFieldException;

    Map<String, String> getCustomTags();
}
