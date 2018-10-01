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

package org.shoal.test.command;

import org.shoal.ha.cache.api.DataStoreContext;
import org.shoal.ha.cache.api.DataStoreException;
import org.shoal.ha.cache.api.ReplicatedDataStoreStatsHolder;
import org.shoal.ha.cache.impl.command.CommandManager;
import org.shoal.ha.group.GroupService;
import org.shoal.test.common.DummyGroupService;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * @author Mahesh Kannan
 */
public class CommandManagerTest extends TestCase {
    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public CommandManagerTest(String testName) {
        super(testName);
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite() {
        return new TestSuite(CommandManagerTest.class);
    }

    /**
     * Rigourous Test :-)
     */
    public void testCreateCommandManager() {
        GroupService gs = new DummyGroupService("inst1", "grp1");
        DataStoreContext dsc = new DataStoreContext("test", gs, this.getClass().getClassLoader());
        CommandManager cm = new CommandManager();
        dsc.setCommandManager(cm);
        cm.initialize(dsc);
        assertTrue(true);
    }

    public void testRegistercommands() {
        GroupService gs = new DummyGroupService("inst1", "grp1");
        DataStoreContext dsc = new DataStoreContext("test", gs, this.getClass().getClassLoader());
        CommandManager cm = new CommandManager();
        dsc.setCommandManager(cm);
        ReplicatedDataStoreStatsHolder dscMBean = new ReplicatedDataStoreStatsHolder(dsc);
        dsc.setDataStoreMBean(dscMBean);
        cm.initialize(dsc);

        cm.registerCommand(new NoopCommand());
        try {
            cm.execute(new NoopCommand());
            assert (true);
        } catch (DataStoreException dse) {
            assert (false);
        }
    }

    /*
     * public void testInterceptors() { GroupService gs = new DummyGroupService("inst1", "grp1"); DataStoreContext dsc = new
     * DataStoreContext("test", gs); CommandManager cm = dsc.getCommandManager(); cm.registerCommand(new NoopCommand());
     * CommandMonitorInterceptor cmi1 = new CommandMonitorInterceptor(); CommandMonitorInterceptor cmi2 = new
     * CommandMonitorInterceptor(); cm.registerExecutionInterceptor(cmi1); cm.registerExecutionInterceptor(cmi2);
     * cm.executeCommand(new NoopCommand());
     * 
     * boolean stat = cmi1.getTransmitCount() == 1; stat = stat && (cmi2.getTransmitCount() == cmi1.getTransmitCount());
     * assertTrue(stat); }
     * 
     * public void testLoopBackInterceptors() { GroupService gs = new DummyGroupService("inst1", "grp1"); DataStoreContext
     * dsc = new DataStoreContext("test", gs); CommandManager cm = dsc.getCommandManager();
     * 
     * cm.registerCommand(new NoopCommand()); cm.registerCommand(new BatchedNoopCommand()); NoopCommandInterceptor cmi1 =
     * new NoopCommandInterceptor(); BatchedNoopCommandInterceptor bat = new BatchedNoopCommandInterceptor();
     * cm.registerExecutionInterceptor(cmi1); cm.registerExecutionInterceptor(bat); cm.executeCommand(new NoopCommand());
     * 
     * System.out.println("****** testLoopBackInterceptors ******"); System.out.println("* cmi1.getTotalTransCount(): " +
     * cmi1.getTotalTransCount()); System.out.println("* cmi1.getNoopTransCount(): " + cmi1.getNoopTransCount());
     * System.out.println("* bat.getNoopTransCount(): " + bat.getTransmitCount()); boolean stat = cmi1.getTotalTransCount()
     * == 2; stat = stat && (cmi1.getNoopTransCount() == 1); stat = stat && (bat.getTransmitCount() == 1); assertTrue(stat);
     * }
     */
}
