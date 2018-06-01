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

package com.sun.enterprise.ee.cms.tests;


public class GMSAdminConstants  {
    // Messages the gmsadmincli and the GMSAdminAgent can send back and forth
    public static final String STOPCLUSTER = "stop_cluster";
    public static final String STOPCLUSTERREPLY = "stop_cluster_reply";
    public static final String STOPINSTANCE = "stop_instance";
    public static final String STOPINSTANCEREPLY = "stop_instance_reply";
    public static final String KILLINSTANCE = "kill_instance";
    public static final String KILLINSTANCEREPLY = "kill_instance_reply";
    public static final String KILLALL = "kill_all";
    public static final String STOPCLUSTERRECEIVED = "stop_cluster_received";
    public static final String ISSTARTUPCOMPLETE = "is_startup_complete";
    public static final String ISSTARTUPCOMPLETEREPLY = "isstartup_complete_reply";
    public static final String STARTUPCOMPLETE = "startup_complete";
    public static final String STARTTESTING = "start_testing";
    public static final String TESTINGCOMPLETE = "testing_complete";


    // various states the GMSAdminAgent can go through
    public static final int UNASSIGNED = -1;
    public static final int RUN = 0;
    public static final int STOP = 1;
    public static final int SHUTDOWNCLUSTER = 2;
    public static final int KILL = 3;


    public static final String ADMINAGENT = "adminagent";
    public static final String ADMINCLI = "admincli";
    public static final String ADMINNAME = "server";
    public static final String INSTANCEPREFIX = "instance";
    public static final String TESTCOORDINATOR = "TestCoordinator";
    public static final String TESTEXECUTOR = "TestExceutor";




}
