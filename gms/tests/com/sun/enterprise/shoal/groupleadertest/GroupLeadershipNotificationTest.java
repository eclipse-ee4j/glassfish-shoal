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

package com.sun.enterprise.shoal.groupleadertest;

import com.sun.enterprise.ee.cms.core.*;
import com.sun.enterprise.ee.cms.impl.client.GroupLeadershipNotificationActionFactoryImpl;

import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.UUID;
import java.util.Properties;

public class GroupLeadershipNotificationTest {

    private final static Logger logger = Logger.getLogger( "GroupLeaderShipNotificationTest" );

    private final String group = "TestGroup";

    public static void main( String[] args ) {
        GroupLeadershipNotificationTest check = new GroupLeadershipNotificationTest();
        try {
            check.runSimpleSample();
        } catch( GMSException e ) {
            logger.log( Level.SEVERE, "Exception occured while joining group:" + e );
        }
    }

    private void runSimpleSample() throws GMSException {
        logger.log( Level.INFO, "Starting GroupLeaderShipNotificationTest...." );

        String serverName = UUID.randomUUID().toString();

        //initialize Group Management Service
        GroupManagementService gms = initializeGMS( serverName, group );

        //register for Group Events
        logger.log( Level.INFO, "Registering for group event notifications" );
        gms.addActionFactory( new GroupLeadershipNotificationActionFactoryImpl( new GroupLeadershipNotificationTest.GroupLeaderShipNotificationCallBack( serverName ) ) );

        //join group
        logger.log( Level.INFO, "Joining Group " + group );
        gms.join();

        //leaveGroupAndShutdown( serverName, gms );
    }

    private GroupManagementService initializeGMS( String serverName, String groupName ) {
        logger.log( Level.INFO, "Initializing Shoal for member: " + serverName + " group:" + groupName );
        return (GroupManagementService)GMSFactory.startGMSModule( serverName,
                                                                  groupName,
                                                                  GroupManagementService.MemberType.CORE,
                                                                  //null ); // Now if properties is null, NPE occurred.
                                                                  new Properties() );
    }

    private void leaveGroupAndShutdown( String serverName, GroupManagementService gms ) {
        logger.log( Level.INFO, "Shutting down gms " + gms + "for server " + serverName );
        gms.shutdown( GMSConstants.shutdownType.INSTANCE_SHUTDOWN );
    }

    private class GroupLeaderShipNotificationCallBack implements CallBack {

        private String serverName;

        public GroupLeaderShipNotificationCallBack( String serverName ) {
            this.serverName = serverName;
        }

        public void processNotification( Signal notification ) {
            if( !( notification instanceof GroupLeadershipNotificationSignal ) ) {
                logger.log( Level.SEVERE, "received unkown notification type:" + notification );
                return;
            }
            GroupLeadershipNotificationSignal groupLeadershipNotification = (GroupLeadershipNotificationSignal)notification;
            GroupManagementService gms = null;
            try {
                gms = (GroupManagementService)GMSFactory.getGMSModule();
            } catch( GMSException e ) {
                e.printStackTrace();
                return;
            }
            GroupHandle groupHandle = gms.getGroupHandle();
            logger.log( Level.INFO,
                        "***GroupLeaderShipNotification received: GroupLeader = " + groupHandle.isGroupLeader() +
                        ", Signal.getMemberToken() = " + groupLeadershipNotification.getMemberToken() +
                        ", Signal.getGroupName() = " + groupLeadershipNotification.getGroupName() +
                        ", Signal.getPreviousView() = " + groupLeadershipNotification.getPreviousView() +
                        ", Signal.getCurrentView() = " + groupLeadershipNotification.getCurrentView() +
                        ", Signal.getCurrentCoreMembers() = " + groupLeadershipNotification.getCurrentCoreMembers() +
                        ", Signal.getAllCurrentMembers() = " + groupLeadershipNotification.getAllCurrentMembers() +
                        ", ServerName = " + serverName +
                        ", Leader = " + groupHandle.getGroupLeader() );
        }
    }
}
