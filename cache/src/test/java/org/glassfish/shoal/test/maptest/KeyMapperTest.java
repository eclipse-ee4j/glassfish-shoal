/*
 * Copyright (c) 1997, 2018 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024 Contributors to the Eclipse Foundation. All rights reserved.
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

package org.glassfish.shoal.test.maptest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.glassfish.shoal.ha.cache.mapper.DefaultKeyMapper;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Unit test for simple App.
 */
public class KeyMapperTest extends TestCase {
    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public KeyMapperTest(String testName) {
        super(testName);
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite() {
        return new TestSuite(KeyMapperTest.class);
    }

    private static void mapStringKeyTest(DefaultKeyMapper km) {
        String[] keys = new String[] { "Key0", "Key1", "Key2" };

        System.out.print("{");
        String delim = "";
        for (String key : keys) {
            System.out.print(delim + key + " => " + km.getMappedInstance("g1", key));
            delim = ", ";
        }

        System.out.println("}");
    }

    public void testRegisterOnly() {
        DefaultKeyMapper km = new DefaultKeyMapper("n0");

        String[] aliveInstances = new String[] { "n0", "n1" };
        String[] previousView = new String[] {};
        km.onViewChange("n0", Arrays.asList(aliveInstances), Arrays.asList(previousView), true);

        assert (true);
    }

    public void testUnregisterOnly() {
        DefaultKeyMapper km = new DefaultKeyMapper("n0");
        String[] aliveInstances = new String[] {};
        String[] previousView = new String[] { "n0", "n1" };
        km.onViewChange("n0", Arrays.asList(aliveInstances), Arrays.asList(previousView), false);

        assert (true);
    }

    public void testRegisterAndUnregister() {
        DefaultKeyMapper km = new DefaultKeyMapper("n0");

        String[] aliveInstances = new String[] { "n0", "n1" };
        String[] previousView = new String[] {};
        km.onViewChange("n0", Arrays.asList(aliveInstances), Arrays.asList(previousView), true);
        aliveInstances = new String[] {};
        previousView = new String[] { "n0", "n1" };
        km.onViewChange("n1", Arrays.asList(aliveInstances), Arrays.asList(previousView), false);

        assert (true);
    }

    public void testEmptyMapTest() {
        DefaultKeyMapper km = new DefaultKeyMapper("n0");
        String mappedInstance = km.getMappedInstance("g1", "Key1");
        String[] replicaInstances = (km.findReplicaInstance("g1", "Key1", null));

        assert (mappedInstance == null);
        assert (replicaInstances.length == 1);

        System.out.println("* Test[testEmptyMapTest] => " + mappedInstance + "; " + replicaInstances.length + "; ");
    }

    public void testRegisterAndTest() {
        DefaultKeyMapper km = new DefaultKeyMapper("n0");

        String[] aliveInstances = new String[] { "n0", "n1" };
        String[] previousView = new String[] {};
        km.onViewChange("n0", Arrays.asList(aliveInstances), Arrays.asList(previousView), true);
        mapStringKeyTest(km);

        aliveInstances = new String[] { "n0", "n1", "n2", "inst1", "instance2", "someInstance" };
        previousView = new String[] {};
        km.onViewChange("**NON-EXISTENT-INSTANCE", Arrays.asList(aliveInstances), Arrays.asList(previousView), true);
        mapStringKeyTest(km);

        assert (true);
    }

    public void testMappedToMyself() {
        DefaultKeyMapper km = new DefaultKeyMapper("inst0");

        String[] aliveInstances = new String[10];
        for (int i = 0; i < 10; i++) {
            aliveInstances[i] = "inst" + i;
        }

        String[] previousView = new String[] {};
        km.onViewChange("inst0", Arrays.asList(aliveInstances), Arrays.asList(previousView), true);

        Integer[] keys = new Integer[14];
        for (int i = 0; i < 14; i++) {
            keys[i] = i;
        }

        boolean result = true;
        for (int i = 0; i < keys.length; i++) {
            if (km.getMappedInstance("g1", keys[i]).equals("inst0")) {
                result = false;
                System.err.println("For key: " + keys[i] + " was mapped to me!!");
            }
        }
        System.out.println("* Test[testMappedToMyself] => " + result);
        assert (result);
    }

    public void testReplicaUponFailure() {
        DefaultKeyMapper km1 = new DefaultKeyMapper("inst2");

        String[] aliveInstances = new String[10];
        for (int i = 0; i < 10; i++) {
            aliveInstances[i] = "inst" + i;
        }

        String[] previousView = new String[] {};
        km1.onViewChange("inst2", Arrays.asList(aliveInstances), Arrays.asList(previousView), true);

        String[] keys = new String[16];
        String[] replicaInstanceNames = new String[16];

        int count = keys.length;

        km1.printMemberStates("Before MAP");
        for (int i = 0; i < count; i++) {
            keys[i] = "Key-" + Math.random();
            replicaInstanceNames[i] = km1.getMappedInstance("g1", keys[i]);
        }

        DefaultKeyMapper km4 = new DefaultKeyMapper("inst4");
        List<String> currentMembers = new ArrayList();
        currentMembers.addAll(Arrays.asList(aliveInstances));
        currentMembers.remove("inst2");
        km4.onViewChange("inst2", currentMembers, Arrays.asList(aliveInstances), false);

        km4.printMemberStates("!@#$#$#$%^%^%^%%&");
        boolean result = true;
        for (int i = 0; i < keys.length; i++) {
            String mappedInstanceName = km4.findReplicaInstance("g1", keys[i], null)[0];
            if (!mappedInstanceName.equals(replicaInstanceNames[i])) {
                result = false;
                System.err.println("For key: " + keys[i] + " exptected Replica was: " + replicaInstanceNames[i] + " but got mapped to: " + mappedInstanceName);
            } else {
                System.out.println("**KeyMapperTest:testReplicaUponFailure; expected: " + replicaInstanceNames[i] + " and got: " + mappedInstanceName);
            }
        }
        System.out.println("* Test[testReplicaUponFailure] => " + result);
        assert (result);
    }

    /*
     * public void testReplicaUponFailureFromAllOtherNodes() {
     *
     * String[] aliveInstances = new String[10]; for (int i=0; i<10; i++) {aliveInstances[i] = "inst"+i;}
     *
     * String[] previousView = new String[] {};
     *
     * int sz = 10; DefaultKeyMapper<String>[] mappers = new DefaultKeyMapper[sz]; for (int i = 0; i < sz; i++) { mappers[i]
     * = new DefaultKeyMapper("n"+i, "g1"); mappers[i].onViewChange(Arrays.asList(aliveInstances),
     * Arrays.asList(previousView), true); }
     *
     * String[] keys = new String[16]; String[] replicaInstanceNames = new String[16];
     *
     * int count = keys.length;
     *
     * for (int i = 0; i < count; i++) { keys[i] = "Key-" + Math.random(); replicaInstanceNames[i] =
     * mappers[0].getMappedInstance("g1", keys[i]); }
     *
     * for (int i = 0; i < sz; i++) { List<String> currentMembers = new ArrayList();
     * currentMembers.addAll(Arrays.asList(aliveInstances)); currentMembers.remove("inst5");
     * mappers[i].onViewChange(currentMembers, Arrays.asList(aliveInstances), false); }
     *
     * boolean result = true; for (int id = 1; id < sz; id++) { for (int i = 0; i < keys.length; i++) { String
     * mappedInstanceName = mappers[id].findReplicaInstance("g1", keys[i]); if
     * (!mappedInstanceName.equals(replicaInstanceNames[i])) { result = false; System.err.println("For key: " + keys[i] +
     * " exptected Replica was: " + replicaInstanceNames[i] + " but got mapped to: " + mappedInstanceName); } else { //
     * System.out.println("**KeyMapperTest:testReplicaUponFailure; Mapper[" + id + "]: expected: " // +
     * replicaInstanceNames[i] + " and got: " + mappedInstanceName); } } }
     * System.out.println("* Test[testReplicaUponFailureFromAllOtherNodes] => " + result); assert (result); }
     */

    /*
     * public void testReplicaUponRestart() { Integer[] keys = new Integer[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13,
     * 14}; String[] expectedReplica = new String[]{ "n0", "n1", "n2", "n4", "n4", "n5", "n0", "n1", "n2", "n5", "n4", "n5",
     * "n0", "n1", "n2"};
     *
     * boolean result = true;
     *
     *
     *
     *
     * DefaultKeyMapper mapper = new DefaultKeyMapper("n3", "g1"); mapper.registerInstance("n0");
     * mapper.registerInstance("n1"); mapper.registerInstance("n2"); mapper.registerInstance("n4");
     * mapper.registerInstance("n5"); mapper.removeInstance("n3"); mapper.registerInstance("n3"); for (int i = 0; i <
     * keys.length; i++) { if (!mapper.findReplicaInstance("g1", keys[i]).equals(expectedReplica[i])) { result = false;
     * System.err.println("testReplicaUponRestart:: For key: " + keys[i] + " exptected Replica was: " + expectedReplica[i] +
     * " but got mapped to: " + mapper.findReplicaInstance("g1", keys[i])); } }
     *
     *
     * System.out.println("* Test[testReplicaUponRestart] => " + result); assert (result); }
     */

}
