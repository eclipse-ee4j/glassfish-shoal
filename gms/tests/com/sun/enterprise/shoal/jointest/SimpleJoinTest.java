/*
 * Copyright (c) 2008, 2018 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.enterprise.shoal.jointest;

import com.sun.enterprise.ee.cms.core.*;
import com.sun.enterprise.ee.cms.impl.client.JoinNotificationActionFactoryImpl;
import com.sun.enterprise.ee.cms.logging.GMSLogDomain;

import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.UUID;
import java.util.Properties;

public class SimpleJoinTest {

    private static final Logger logger = GMSLogDomain.getLogger(GMSLogDomain.GMS_LOGGER);

    private final String group = "TestGroup";

    public static void main( String[] args ) {
        String serverName = null;
        if( args != null && args.length == 1 ) {
            serverName = args[0];
        }

        SimpleJoinTest check = new SimpleJoinTest();
        try {
            check.runSimpleSample( serverName );
        } catch( GMSException e ) {
            logger.log( Level.SEVERE, "Exception occured while joining group:" + e );
        }
    }

    private void runSimpleSample( String serverName ) throws GMSException {
        logger.log( Level.INFO, "Starting SimpleJoinTest...." );

        if( serverName == null || serverName.length() <= 0 )
            serverName = UUID.randomUUID().toString();;

        //initialize Group Management Service
        GroupManagementService gms = initializeGMS( serverName, group );

        //register for Group Events
        logger.log( Level.INFO, "Registering for group event notifications" );
        gms.addActionFactory( new JoinNotificationActionFactoryImpl( new JoinNotificationCallBack( serverName ) ) );

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

    private class JoinNotificationCallBack implements CallBack {

        private String serverName;

        public JoinNotificationCallBack( String serverName ) {
            this.serverName = serverName;
        }

        public void processNotification( Signal notification ) {
            if ( !( notification instanceof JoinNotificationSignal ) )
                logger.log( Level.SEVERE, "received unkown notification type:" + notification );
            logger.log( Level.INFO, "***JoinNotification received: ServerName = " + serverName + ", Signal.getMemberToken() = " + notification.getMemberToken() );
        }
    }
}

