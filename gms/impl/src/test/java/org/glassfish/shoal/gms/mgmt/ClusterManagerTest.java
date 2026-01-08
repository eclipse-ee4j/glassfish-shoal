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

package org.glassfish.shoal.gms.mgmt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;

import org.glassfish.shoal.gms.api.core.GMSException;
import org.glassfish.shoal.gms.api.core.GroupManagementService;
import org.glassfish.shoal.gms.base.CustomTagNames;
import org.glassfish.shoal.gms.base.PeerID;
import org.glassfish.shoal.gms.base.SystemAdvertisement;
import org.glassfish.shoal.gms.logging.GMSLogDomain;
import org.glassfish.shoal.gms.mgmt.ClusterManager;
import org.glassfish.shoal.gms.mgmt.ClusterMessageListener;
import org.glassfish.shoal.gms.mgmt.ClusterView;
import org.glassfish.shoal.gms.mgmt.ClusterViewEvent;
import org.glassfish.shoal.gms.mgmt.ClusterViewEventListener;
import org.glassfish.shoal.gms.mgmt.ConfigConstants;
import org.glassfish.shoal.gms.mgmt.MasterNode;

import junit.framework.TestCase;

/**
 *
 * @author sdimilla
 */
public class ClusterManagerTest extends TestCase {

    private ClusterManager manager = null;
    private MasterNode masterNode = null;
    private ClusterManager watchdogManager = null;
    static final String name = "junit_instanceName";
    static final String groupName = "junit_groupName";
    static final String watchdogGroupName = "junit_watchdog_groupName";
    Properties props = null;
    Map<String, String> idMap = null;
    List<ClusterMessageListener> mListeners = null;
    List<ClusterViewEventListener> vListeners = null;

    public ClusterManagerTest(String testName) {
        super(testName);
        GMSLogDomain.getMcastLogger().setLevel(Level.FINEST);

    }

    private static Map<String, String> getIdMap(String memberType, String groupName) {
        final Map<String, String> idMap = new HashMap<String, String>();
        idMap.put(CustomTagNames.MEMBER_TYPE.toString(), memberType);
        idMap.put(CustomTagNames.GROUP_NAME.toString(), groupName);
        idMap.put(CustomTagNames.START_TIME.toString(), Long.valueOf(System.currentTimeMillis()).toString());
        return idMap;
    }

    // TODO: NOT YET IMPLEMENTED
    private static Properties getPropsForTest() {
        Properties props = new Properties();
        // shorten discovery time to next to nothing for this junit test.
        props.put(ConfigConstants.DISCOVERY_TIMEOUT.toString(), 50L);
        return props;
    }

    public void mySetUp() throws GMSException {
        GMSLogDomain.getMcastLogger().setLevel(Level.FINEST);
        System.out.println("ShoalLogger.mcast log level is " + GMSLogDomain.getMcastLogger().getLevel());
        props = getPropsForTest();
        idMap = getIdMap(name, groupName);
        vListeners = new ArrayList<ClusterViewEventListener>();
        mListeners = new ArrayList<ClusterMessageListener>();
        vListeners.add(new ClusterViewEventListener() {

            public void clusterViewEvent(final ClusterViewEvent event, final ClusterView view) {
                System.out.println("clusterViewEvent:event.getEvent=" + event.getEvent().toString());
                System.out.println("clusterViewEvent:event.getAdvertisement()=" + event.getAdvertisement().toString());
                System.out.println("clusterViewEvent:view.getPeerNamesInView=" + view.getPeerNamesInView().toString());
            }
        });
        mListeners.add(new ClusterMessageListener() {

            public void handleClusterMessage(SystemAdvertisement id, final Object message) {
                System.out.println("ClusterMessageListener:id.getName=" + id.getName());
                System.out.println("ClusterMessageListener:message.toString=" + message.toString());
            }
        });
        manager = new ClusterManager(groupName, name, idMap, props, vListeners, mListeners);
    }

    private boolean addProcessedMasterChangeEvent(PeerID master, long mvceSeqId) {
        final long TEST_EXPIRATION_DURATION_MS = 1000L;
        MasterNode.ProcessedMasterViewId processed = new MasterNode.ProcessedMasterViewId(master, mvceSeqId, TEST_EXPIRATION_DURATION_MS);
        boolean result;
        synchronized (masterNode.processedChangeEvents) {
            result = masterNode.processedChangeEvents.add(processed);
        }
        return result;
    }

    private void generateProcessedMasterChangeEvents(long mvceStartId, long mvceStopId) {
        for (long i = mvceStartId; i <= mvceStopId; i++) {
            boolean result = addProcessedMasterChangeEvent(masterNode.getMasterNodeID(), i);
            assertTrue("add of masterViewSeqId " + i + " should succeed", result);
        }
    }

   

    public void testStartRunStopClusterManager() throws GMSException {
        mySetUp();

        GMSLogDomain.getMcastLogger().setLevel(Level.FINEST);
        assertFalse(manager.isGroupStartup());
        manager.start();
        assertEquals(manager.getGroupName(), groupName);
        assertEquals(manager.getID(name).getInstanceName(), name);
        assertEquals(manager.getInstanceName(), name);
        assertEquals(manager.getPeerID().getInstanceName(), name);
        try {
            Thread.sleep(500); // long enough to announce being master.
        } catch (InterruptedException ie) {
        }
        masterNode = manager.getMasterNode();
        assertTrue(masterNode.isMaster());
        assertTrue(masterNode.isMasterAssigned());
        assertTrue(!masterNode.isDiscoveryInProgress());
        masterNode = manager.getMasterNode();

        // NOTE: this is a unit test and some of conditions would not happen in working program.
        // namely, master node would not typically be checking for missed messages.
        // However, since this unit test only starts one instance and it is the master,
        // this is just a junit level check of algorithm in checkForMissedMasterChangeEvents.
        generateProcessedMasterChangeEvents(2, 10);
        List<Long> missed = masterNode.checkForMissedMasterChangeEvents(masterNode.getMasterNodeID(), 5);
        assertTrue(missed.size() == 0);
        missed = masterNode.checkForMissedMasterChangeEvents(masterNode.getMasterNodeID(), 10);
        assertTrue(missed.size() == 0);
        generateProcessedMasterChangeEvents(11, 13);
        missed = masterNode.checkForMissedMasterChangeEvents(masterNode.getMasterNodeID(), 12);
        assertTrue(missed.size() == 0);
        missed = masterNode.checkForMissedMasterChangeEvents(masterNode.getMasterNodeID(), 12);
        assertTrue(missed.size() == 0);
        missed = masterNode.checkForMissedMasterChangeEvents(masterNode.getMasterNodeID(), 13);
        assertTrue(missed.size() == 0);
        generateProcessedMasterChangeEvents(14, 15);
        generateProcessedMasterChangeEvents(17, 18);
        missed = masterNode.checkForMissedMasterChangeEvents(masterNode.getMasterNodeID(), 17);
        assertTrue("found missed.size()=" + missed.size(), missed.size() == 1);
        assertTrue("detect that masterViewSeqId 16 was missed, found missed[0]=" + missed.get(0), missed.get(0) == 16L);
        missed = masterNode.checkForMissedMasterChangeEvents(masterNode.getMasterNodeID(), 22);
        assertTrue("found missed.size()=" + missed.size(), missed.size() == 4);
        assertTrue("detect that masterViewSeqId 19 was missed, found missed[0]=" + missed.get(0), missed.get(0) == 19L);
        assertTrue("detect that masterViewSeqId 20 was missed, found missed[1]=" + missed.get(1), missed.get(1) == 20L);
        assertTrue("detect that masterViewSeqId 21 was missed, found missed[2]=" + missed.get(2), missed.get(2) == 21L);
        assertTrue("detect that masterViewSeqId 22 was missed, found missed[3]=" + missed.get(3), missed.get(3) == 22L);
        generateProcessedMasterChangeEvents(19, 28);
        missed = masterNode.checkForMissedMasterChangeEvents(masterNode.getMasterNodeID(), 25);
        assertTrue(missed.size() == 0);
        missed = masterNode.checkForMissedMasterChangeEvents(masterNode.getMasterNodeID(), 27);
        assertTrue(missed.size() == 0);
        missed = masterNode.checkForMissedMasterChangeEvents(masterNode.getMasterNodeID(), 28);
        assertTrue(missed.size() == 0);
        missed = masterNode.checkForMissedMasterChangeEvents(masterNode.getMasterNodeID(), 29);
        assertTrue(missed.size() == 1);
        assertTrue("detect that masterViewSeqId 29 was missed, found missed[0]=" + missed.get(0), missed.get(0) == 29L);

        manager.stop(false);
        assertTrue(manager.isStopping());
        manager.isGroupStartup();
    }

   

    public void testisWatchdogFalse() throws GMSException {
        mySetUp();

        assertFalse(manager.isWatchdog());
    }

   

    public void testisWatchdogTrue() throws GMSException {
        mySetUp();

        watchdogManager = new ClusterManager(groupName, GroupManagementService.MemberType.WATCHDOG.toString(),
                getIdMap(GroupManagementService.MemberType.WATCHDOG.toString(), groupName), null, null, null);
        assertTrue(watchdogManager.isWatchdog());
    }
}
