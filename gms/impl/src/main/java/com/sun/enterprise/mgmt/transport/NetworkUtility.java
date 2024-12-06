/*
 * Copyright (c) 2023, 2024 Contributors to the Eclipse Foundation
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

import com.sun.enterprise.ee.cms.logging.GMSLogDomain;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class that can be used by any calling code to do common routines about Network I/O
 *
 * @author Bongjae Chang
 */
public class NetworkUtility {

    private static final Logger LOG = GMSLogDomain.getLogger(GMSLogDomain.GMS_LOGGER);

    public static final String IPV4ANYADDRESS = "0.0.0.0";
    public static final String IPV6ANYADDRESS = "::";
    public static final String IPV4LOOPBACK = "127.0.0.1";
    public static final String IPV6LOOPBACK = "::1";

    /**
     * Constant which works as the IP "Any Address" value
     */
    public static final InetAddress ANYADDRESS;
    public static final InetAddress ANYADDRESSV4;
    public static final InetAddress ANYADDRESSV6;

    /**
     * Constant which works as the IP "Local Loopback" value;
     */
    public static final InetAddress LOOPBACK;
    public static final InetAddress LOOPBACKV4;
    public static final InetAddress LOOPBACKV6;

    public volatile static List<InetAddress> allLocalAddresses;
    public volatile static InetAddress firstInetAddressV4;
    public volatile static InetAddress firstInetAddressV6;
    static AtomicBoolean preferIPv6Addresses = null;

    private static final boolean IS_AIX_JDK;

    static {
        boolean preferIPv6Addresses = getPreferIpv6Addresses();

        InetAddress GET_ADDRESS = null;
        try {
            GET_ADDRESS = InetAddress.getByName(IPV4ANYADDRESS);
        } catch (Exception ignored) {
            if (LOG.isLoggable(Level.FINE)) {
                LOG.log(Level.FINE, "failed to intialize ANYADDRESSV4. Not fatal", ignored);
            }
        }
        ANYADDRESSV4 = GET_ADDRESS;

        GET_ADDRESS = null;
        try {
            GET_ADDRESS = InetAddress.getByName(IPV6ANYADDRESS);
        } catch (Exception ignored) {
            if (LOG.isLoggable(Level.FINE)) {
                LOG.log(Level.FINE, "failed to intialize IPV6ANYADDRESS. Not fatal", ignored);
            }
        }
        ANYADDRESSV6 = GET_ADDRESS;

        ANYADDRESS = (ANYADDRESSV4 == null || preferIPv6Addresses) ? ANYADDRESSV6 : ANYADDRESSV4;

        GET_ADDRESS = null;
        try {
            GET_ADDRESS = InetAddress.getByName(IPV4LOOPBACK);
        } catch (Exception ignored) {
            if (LOG.isLoggable(Level.FINE)) {
                LOG.log(Level.FINE, "failed to intialize IPV4LOOPBACK. Not fatal", ignored);
            }
        }
        LOOPBACKV4 = GET_ADDRESS;

        GET_ADDRESS = null;
        try {
            GET_ADDRESS = InetAddress.getByName(IPV6LOOPBACK);
        } catch (Exception ignored) {
            if (LOG.isLoggable(Level.FINE)) {
                LOG.log(Level.FINE, "failed to intialize ANYADDRESSV4. Not fatal", ignored);
            }
        }
        LOOPBACKV6 = GET_ADDRESS;

        LOOPBACK = (LOOPBACKV4 == null || preferIPv6Addresses) ? LOOPBACKV6 : LOOPBACKV4;

        if (LOOPBACK == null || ANYADDRESS == null) {
            throw new IllegalStateException("failure initializing statics. Neither IPV4 nor IPV6 seem to work");
        }
    }

    static {
        String vendor = System.getProperty("java.vendor");
        IS_AIX_JDK = vendor == null ? false : vendor.startsWith("IBM");
    }

    /**
     * Returns all local addresses except for lookback and any local address But, if any addresses were not found locally,
     * the lookback is added to the list.
     *
     * @return List which contains available addresses locally
     */
    public static List<InetAddress> getAllLocalAddresses() {
        if (allLocalAddresses != null) {
            return allLocalAddresses;
        }
        List<InetAddress> allAddr = new ArrayList<>();
        Enumeration<NetworkInterface> allInterfaces = null;
        try {
            allInterfaces = NetworkInterface.getNetworkInterfaces();
        } catch (SocketException t) {
            if (LOG.isLoggable(Level.INFO)) {
                LOG.log(Level.INFO, "Could not get local interfaces' list", t);
            }
        }

        if (allInterfaces == null) {
            allInterfaces = Collections.enumeration(Collections.<NetworkInterface>emptyList());
        }

        while (allInterfaces.hasMoreElements()) {
            NetworkInterface anInterface = allInterfaces.nextElement();
            try {
                if (!isUp(anInterface)) {
                    continue;
                }
                Enumeration<InetAddress> allIntfAddr = anInterface.getInetAddresses();
                while (allIntfAddr.hasMoreElements()) {
                    InetAddress anAddr = allIntfAddr.nextElement();
                    if (anAddr.isLoopbackAddress() || anAddr.isAnyLocalAddress()) {
                        continue;
                    }
                    if (!allAddr.contains(anAddr)) {
                        allAddr.add(anAddr);
                    }
                }
            } catch (Throwable t) {
                if (LOG.isLoggable(Level.INFO)) {
                    LOG.log(Level.INFO, "Could not get addresses for " + anInterface, t);
                }
            }
        }

        if (allAddr.isEmpty()) {
            if (LOOPBACKV4 != null) {
                allAddr.add(LOOPBACKV4);
            }
            if (LOOPBACKV6 != null) {
                allAddr.add(LOOPBACKV6);
            }
        }
        allLocalAddresses = allAddr;
        return allLocalAddresses;
    }

    public static InetAddress getAnyAddress() {
        if (getPreferIpv6Addresses() && ANYADDRESSV6 != null) {
            return ANYADDRESSV6;
        } else {
            return ANYADDRESSV4;
        }
    }

    public static InetAddress getLoopbackAddress() {
        if (getPreferIpv6Addresses() && LOOPBACKV6 != null) {
            return LOOPBACKV6;
        } else {
            return LOOPBACKV4;
        }
    }

    /**
     * Return a first network interface except for the lookback But, if any network interfaces were not found locally, the
     * lookback interface is returned.
     *
     * @param preferIPv6 flag to indicate if IPV6 is preferred
     *
     * @return a first network interface
     * @throws IOException if an I/O error occurs or a network interface was not found
     */
    public static NetworkInterface getFirstNetworkInterface(boolean preferIPv6) throws IOException {
        NetworkInterface loopback = null;
        NetworkInterface firstInterface = null;
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();

        // only consider network interface that supports preferred ipv address format. (either IPv4 or IPv6. default is IPv4)
        while (interfaces != null && interfaces.hasMoreElements()) {
            NetworkInterface anInterface = interfaces.nextElement();
            if (isLoopbackNetworkInterface(anInterface)) {
                loopback = anInterface;
                continue;
            }

            // favor supports multicast the first time through.
            if (isUp(anInterface) && supportsMulticast(anInterface)) {
                if (getNetworkInetAddress(anInterface, preferIPv6) != null) {
                    firstInterface = anInterface;
                    break;
                }
            }
        }

        if (firstInterface == null) {
            // only consider network interface that supports preferred ipv address format. (either IPv4 or IPv6. default is IPv4)
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface anInterface = interfaces.nextElement();
                if (isLoopbackNetworkInterface(anInterface)) {
                    loopback = anInterface;
                    continue;
                }

                // do not require multicast this pass through.
                if (isUp(anInterface)) {
                    if (getNetworkInetAddress(anInterface, preferIPv6) != null) {
                        firstInterface = anInterface;
                        break;
                    }
                }
            }
        }

        // loop through network interfaces one last time, just look for network interfaces with !preferIPv6 this time.
        if (firstInterface == null) {
            interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface anInterface = interfaces.nextElement();
                if (isLoopbackNetworkInterface(anInterface)) {
                    loopback = anInterface;
                    continue;
                }

                // replaced isMulticast() check with just an isUp() check.
                // Definitely not correct for non-multicast mode to not allow non-multicast network interfaces.
                if (isUp(anInterface)) {
                    if (getNetworkInetAddress(anInterface, !preferIPv6) != null) {
                        firstInterface = anInterface;
                        break;
                    }
                }
            }
        }
        if (firstInterface == null) {
            firstInterface = loopback;
        }
        if (firstInterface == null) {
            throw new IOException("failed to find a network interface");
        } else {
            if (LOG.isLoggable(Level.FINE)) {
                InetAddress firstAddress = getNetworkInetAddress(firstInterface, preferIPv6);
                if (firstAddress == null) {
                    firstAddress = getNetworkInetAddress(firstInterface, !preferIPv6);
                }
                LOG.fine("getFirstNetworkInterface  result: interface name:" + firstInterface.getName() + " address:" + firstAddress);
            }
            return firstInterface;
        }
    }

    public static InetAddress getLocalHostAddress() {
        InetAddress result = null;
        try {
            result = InetAddress.getLocalHost();
        } catch (UnknownHostException ignore) {
        }
        return result;
    }

    public static boolean getPreferIpv6Addresses() {
        if (preferIPv6Addresses == null) {
            String propValue = null;
            boolean result = false;
            try {
                propValue = System.getProperty("java.net.preferIPv6Addresses", "false");
                result = Boolean.parseBoolean(propValue);
            } catch (Throwable t) {
                if (LOG.isLoggable(Level.WARNING)) {
                    LOG.log(Level.WARNING, "netutil.invalidPreferIPv6Addresses", new Object[] { t.getLocalizedMessage() });
                    LOG.log(Level.WARNING, "stack trace", t);
                }
            } finally {
                preferIPv6Addresses = new AtomicBoolean(result);
            }
        }
        if (LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE, "NetworkUtlity.getPreferIpv6Addresses=" + preferIPv6Addresses.get());
        }
        return preferIPv6Addresses.get();
    }

    /**
     * Return a first <code>InetAddress</code> of the first network interface check java property
     * java.net.preferIPv6Addresses for whether to favor IPv4 or IPv6. (java default is to favor IPv4 addresses) If unable
     * to find a valid network interface, then fallback to trying to get localhost address as last resort.
     *
     * @return a first found <code>InetAddress</code>.
     * @throws IOException if an I/O error occurs or a network interface was not found
     */
    public static InetAddress getFirstInetAddress() throws IOException { // check JDK defined property for whether to generate IPv4 or IPv6 addresses. Default
                                                                         // is ipv4.celtics
        boolean preferIPv6Addrs = getPreferIpv6Addresses();
        InetAddress firstInetAddress = NetworkUtility.getFirstInetAddress(preferIPv6Addrs);
        if (firstInetAddress == null) {
            firstInetAddress = NetworkUtility.getFirstInetAddress(!preferIPv6Addrs);
        }
        if (firstInetAddress == null) {

            // last ditch effort to get a valid public IP address.
            // just in case NetworkInterface methods such as isUp is working incorrectly on some platform,
            // Inspired by GLASSFISH-17195.
            firstInetAddress = NetworkUtility.getLocalHostAddress();

        }
        if (firstInetAddress == null) {
            throw new IOException("can not find a first InetAddress");
        } else {
            return firstInetAddress;
        }
    }

    /**
     * Return a first <code>InetAddress</code> of the first network interface But, if any network interfaces were not found
     * locally, <code>null</code> could be returned.
     *
     * @param preferIPv6 if true, prefer IPv6 InetAddress. otherwise prefer IPv4 InetAddress
     * @return a first found <code>InetAddress</code>.
     * @throws IOException if an I/O error occurs or a network interface was not found
     */
    public static InetAddress getFirstInetAddress(boolean preferIPv6) throws IOException {
//        LOG.info("enter getFirstInetAddress preferIPv6=" + preferIPv6);
        if (preferIPv6 && firstInetAddressV6 != null) {
//            LOG.info("exit getFirstInetAddress cached ipv6 result=" + firstInetAddressV6);
            return firstInetAddressV6;
        } else if (!preferIPv6 && firstInetAddressV4 != null) {
//            LOG.info("exit getFirstInetAddress cached ipv4 result=" + firstInetAddressV4);
            return firstInetAddressV4;
        }
        NetworkInterface anInterface = getFirstNetworkInterface(preferIPv6);
//        LOG.info("getFirstInetAddress: first network interface=" + anInterface);
        if (anInterface == null) {
            if (preferIPv6 && firstInetAddressV6 != null) {
                return firstInetAddressV6;
            } else {
                return firstInetAddressV4;
            }
        } else {
            return getNetworkInetAddress(anInterface, preferIPv6);
        }
    }

    /**
     * Return a first <code>InetAddress</code> of network interface But, if any network interfaces were not found locally,
     * <code>null</code> could be returned.
     *
     * @param anInterface the type of network interface
     * @param preferIPv6 if true, prefer IPv6 InetAddress. otherwise prefer IPv4 InetAddress
     * @return a first found <code>InetAddress</code>.
     * @throws IOException if an I/O error occurs or a network interface was not found
     */
    public static InetAddress getNetworkInetAddress(NetworkInterface anInterface, boolean preferIPv6) throws IOException {
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("enter getNetworkInetAddress networkInterface=" + anInterface + " preferIPv6=" + preferIPv6);
        }
        if (anInterface == null) {
            return null;
        }
        InetAddress firstInetAddressV6 = null;
        InetAddress firstInetAddressV4 = null;

        Enumeration<InetAddress> allIntfAddr = anInterface.getInetAddresses();
        while (allIntfAddr.hasMoreElements()) {
            InetAddress anAddr = allIntfAddr.nextElement();
//            LOG.info("getNetworkInetAddress: anAddr=" + anAddr);
            // allow loopback address. only work on a single machine. used for development.
            // if( anAddr.isLoopbackAddress() || anAddr.isAnyLocalAddress() )
            // continue;
            if (firstInetAddressV6 == null && anAddr instanceof Inet6Address) {
                firstInetAddressV6 = anAddr;
            } else if (firstInetAddressV4 == null && anAddr instanceof Inet4Address) {
                firstInetAddressV4 = anAddr;
            }
            if (firstInetAddressV6 != null && firstInetAddressV4 != null) {
                break;
            }
        }
        if (preferIPv6 && firstInetAddressV6 != null) {
//            LOG.info("exit getNetworkInetAddress ipv6 result=" + firstInetAddressV6);
            return firstInetAddressV6;
        } else {
//            LOG.info("exit getNetworkInetAddress ipv4 result=" + firstInetAddressV4);
            return firstInetAddressV4;
        }
    }

    public static boolean isLoopbackNetworkInterface(NetworkInterface anInterface) {
        if (anInterface == null) {
            return false;
        }
        try {
            return anInterface.isLoopback();
        } catch (Throwable t) {
        }
        boolean hasLoopback = false;
        Enumeration<InetAddress> allIntfAddr = anInterface.getInetAddresses();
        while (allIntfAddr.hasMoreElements()) {
            InetAddress anAddr = allIntfAddr.nextElement();
            if (anAddr.isLoopbackAddress()) {
                hasLoopback = true;
                break;
            }
        }
        return hasLoopback;
    }

    public static boolean supportsMulticast(NetworkInterface anInterface) {
        if (anInterface == null) {
            return false;
        }
        boolean result;
        try {
            result = anInterface.isUp();
        } catch (Throwable t) {
            result = false;
        }
        if (!result) {
            return result;
        } else if (IS_AIX_JDK) {

            // workaround for Network.supportsMulticast not working properly on AIX.
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("Workaround for java.net.NetworkInterface.supportsMulticast() returning false on AIX");
            }
            return true;
        } else {
            try {
                return anInterface.supportsMulticast();
            } catch (Throwable t) {
                // will just return false in this case
            }
        }

        return false;
    }

    public static boolean isUp(NetworkInterface anInterface) {
        if (anInterface == null) {
            return false;
        }
        try {
            return anInterface.isUp();
        } catch (Throwable t) {
            // will just return false in this case
        }
        return false;
    }

    public static void writeIntToByteArray(final byte[] bytes, final int offset, final int value) throws IllegalArgumentException {
        if (bytes == null) {
            return;
        }
        if (bytes.length < offset + 4) {
            throw new IllegalArgumentException("bytes' length is too small");
        }
        bytes[offset + 0] = (byte) ((value >> 24) & 0xFF);
        bytes[offset + 1] = (byte) ((value >> 16) & 0xFF);
        bytes[offset + 2] = (byte) ((value >> 8) & 0xFF);
        bytes[offset + 3] = (byte) (value & 0xFF);
    }

    public static int getIntFromByteArray(final byte[] bytes, final int offset) throws IllegalArgumentException {
        if (bytes == null) {
            return 0;
        }
        if (bytes.length < offset + 4) {
            throw new IllegalArgumentException("bytes' length is too small");
        }
        int ch1 = bytes[offset] & 0xff;
        int ch2 = bytes[offset + 1] & 0xff;
        int ch3 = bytes[offset + 2] & 0xff;
        int ch4 = bytes[offset + 3] & 0xff;
        return (ch1 << 24) + (ch2 << 16) + (ch3 << 8) + ch4;
    }

    public static int serialize(final OutputStream baos, final Map<String, Serializable> messages) throws MessageIOException {
        int count = 0;
        if (baos == null || messages == null) {
            return count;
        }
        String name = null;
        ObjectOutputStream oos = null;
        try {
            oos = new ObjectOutputStream(baos);
            for (Map.Entry<String, Serializable> entry : messages.entrySet()) {
                name = entry.getKey();
                Serializable obj = entry.getValue();
                count++;
                oos.writeObject(name);
                oos.writeObject(obj);
            }
            oos.flush();
        } catch (Throwable t) {
            throw new MessageIOException("failed to serialize a message : name = " + name + ".", t);
        } finally {
            if (oos != null) {
                try {
                    oos.close();
                } catch (IOException e) {
                }
            }
        }
        return count;
    }

    public static void deserialize(final InputStream is, final int count, final Map<String, Serializable> messages) throws MessageIOException {
        if (is == null || count <= 0 || messages == null) {
            return;
        }
        String name = null;
        ObjectInputStream ois = null;
        try {
            ois = new ObjectInputStream(is);
            Object obj = null;
            for (int i = 0; i < count; i++) {
                name = (String) ois.readObject();
                obj = ois.readObject();
                if (obj instanceof Serializable) {
                    messages.put(name, (Serializable) obj);
                }
            }
        } catch (Throwable t) {
            LOG.log(Level.WARNING, "netutil.deserialize.failure", new Object[] { messages.toString(), name, Thread.currentThread().getName() });
            throw new MessageIOException("failed to deserialize a message : name = " + name, t);
        } finally {
            if (ois != null) {
                try {
                    ois.close();
                } catch (IOException e) {
                }
            }
        }
    }

    public static boolean isBindAddressValid(String addressString) {
        ServerSocket socket = null;
        try {
            InetAddress ia = resolveBindInterfaceName(addressString);

            // calling ServerSocket with null for ia means to use any local address that is available.
            // thus, if ia is not non-null at this point, must return false here.
            if (ia == null) {
                return false;
            }

            // port 0 means any free port
            // backlog 0 means use default
            socket = new ServerSocket(0, 0, ia);

            // make extra sure
            boolean retVal = socket.isBound();
            if (!retVal) {
                LOG.log(Level.WARNING, "netutil.validate.bind.not.bound", addressString);
            }
            return retVal;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "netutil.validate.bind.address.exception", new Object[] { addressString, e.toString() });
            return false;
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException ioe) {
                    LOG.log(Level.FINE, "Could not close socket used to validate address.");
                }
            }
        }
    }

    public static InetAddress resolveBindInterfaceName(String addressString) {
        InetAddress ia = null;
        NetworkInterface ni = null;

        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("enter NetworkUtility.resolveBindInterfaceName(" + addressString + ")");
        }

        // due GLASSFISH-18047, check if addressString is a network interface before checking
        // if it is a host name or ip address.
        try {
            ni = NetworkInterface.getByName(addressString);
        } catch (Throwable ignored) {
            if (LOG.isLoggable(Level.FINE)) {
                LOG.log(Level.FINE, "resolveBindInterfaceName: call to NetworkInterface.getByName ignoring thrown exception", ignored);
            }
        }

        if (ni != null) {
            try {
                ia = getNetworkInetAddress(ni, getPreferIpv6Addresses());
            } catch (Throwable ignored) {
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.log(Level.FINE, "resolveBindInterfaceName: call to getNetworkAddress ignoring thrown exception", ignored);
                }
            }
            if (ia == null) {
                try {
                    ia = getNetworkInetAddress(ni, !getPreferIpv6Addresses());
                } catch (Throwable ignored) {
                    if (LOG.isLoggable(Level.FINE)) {
                        LOG.log(Level.FINE, "resolveBindInterfaceName: call to NetworkUtility.getNetworkAddress ignoring thrown exception", ignored);
                    }
                }
            }
        }
        if (ia == null) {
            try {
                ia = InetAddress.getByName(addressString);
            } catch (Throwable ignored) {
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.log(Level.FINE, "resolveBindInterfaceName: call to InetAddress.getByName ignoring thrown exception", ignored);
                }
            }
        }
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("return NetworkUtility.resolveBindInterfaceName(" + addressString + ")=" + ia);
        }
        return ia;
    }

    public static void main(String[] args) throws IOException {
        final String preferIPv6PropertyValue = System.getProperty("java.net.preferIPv6Addresses", "false");
        System.out.println("Java property java.net.preferIPv6Addresses=" + preferIPv6PropertyValue);
        boolean preferIPv6Addrs = Boolean.parseBoolean(preferIPv6PropertyValue);
        System.out.println("AllLocalAddresses() = " + getAllLocalAddresses());
        System.out.println("getFirstNetworkInterface(preferIPv6Addrs) = " + getFirstNetworkInterface(preferIPv6Addrs));
        System.out.println("getFirstInetAddress(preferIPv6Addresses:" + preferIPv6Addrs + ")=" + getFirstInetAddress(preferIPv6Addrs));
        System.out.println("getFirstInetAddress()=" + getFirstInetAddress());
        System.out.println("getFirstInetAddress( true ) = " + getFirstInetAddress(true));
        InetAddress ia = getFirstInetAddress(false);
        System.out.println("getFirstInetAddress( false ) = " + getFirstInetAddress(false));
        System.out.println("getLocalHostAddress = " + getLocalHostAddress());
        System.out.println("getFirstNetworkInteface(!preferIPv6Addrs) = " + NetworkUtility.getFirstNetworkInterface(!preferIPv6Addrs));
        System.out.println("getNetworkInetAddress(firstNetworkInteface, true) = "
                + NetworkUtility.getNetworkInetAddress(NetworkUtility.getFirstNetworkInterface(true), true));
        System.out.println("getNetworkInetAddress(firstNetworkInteface, false) = "
                + NetworkUtility.getNetworkInetAddress(NetworkUtility.getFirstNetworkInterface(false), false));
        Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
        System.out.println("\n-------------------------------------------------------");
        System.out.println("\nAll Network Interfaces");
        for (NetworkInterface netint : Collections.list(nets)) {
            System.out.println("\n\n**************************************************");
            displayInterfaceInformation(netint);
        }
    }

    static void displayInterfaceInformation(NetworkInterface netint) throws SocketException {
        System.out.printf("Display name: %s\n", netint.getDisplayName());
        System.out.printf("Name: %s\n", netint.getName());
        System.out.printf("PreferIPv6Addresses: %b\n", getPreferIpv6Addresses());

        Enumeration<InetAddress> inetAddresses = netint.getInetAddresses();
        for (InetAddress inetAddress : Collections.list(inetAddresses)) {
            System.out.printf("InetAddress: %s\n", inetAddress);
        }

        System.out.printf("Up? %s\n", isUp(netint));
        System.out.printf("Loopback? %s\n", netint.isLoopback());
        System.out.printf("PointToPoint? %s\n", netint.isPointToPoint());
        System.out.printf("Supports multicast? %s\n", netint.supportsMulticast());
        System.out.printf("Virtual? %s\n", netint.isVirtual());
        System.out.printf("Hardware address: %s\n", java.util.Arrays.toString(netint.getHardwareAddress()));
        System.out.printf("MTU: %s\n", netint.getMTU());
        try {
            System.out.printf("Network Inet Address (preferIPV6=false) %s\n", getNetworkInetAddress(netint, false));
        } catch (IOException ignore) {
        }
        try {
            System.out.printf("Network Inet Address (preferIPV6=true) %s\n", getNetworkInetAddress(netint, true));
        } catch (IOException ignore) {
        }
        InetAddress ia = resolveBindInterfaceName(netint.getName());
        String ia_string = ia != null ? ia.getHostAddress() : "<unresolved>";
        System.out.printf("resolveBindInterfaceName(%s)=%s", netint.getName(), ia_string);

        System.out.printf("\n");
    }
}
