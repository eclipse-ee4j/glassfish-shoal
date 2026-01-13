/*
 * Copyright (c) 1997, 2018 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020 Payara Services Ltd.
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

import com.sun.enterprise.ee.cms.core.*;
import com.sun.enterprise.ee.cms.impl.client.*;
import com.sun.enterprise.ee.cms.impl.base.DistributedStateCacheImpl;
import com.sun.enterprise.ee.cms.logging.GMSLogDomain;

import static java.lang.Thread.sleep;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.Serializable;

 /**
 * This is a mock object that exists to demonstrate a GMS client.
 * This client is started by the mock ApplicationServer object which results in
 * this client retrieving an instance of GroupManagementService in order to
 * register action factories for its notification purposes.
 * It is assumed that this client module has already implemented the relevant
 * Action and ActionFactory pair for the particular notification requirements.
 * For Example, if this client requires Failure Notifications, it should
 * implement FailureNotificationActionFactory and FailureNotificationAction
 * and register the FailureNotificationActionFactory.
 *
 * @author Shreedhar Ganapathy
 *         Date: Mar 1, 2005
 * @version $Revision$
 */
public class GMSClientService implements Runnable, CallBack{
    private GroupManagementService gms;
     private Logger logger = GMSLogDomain.getLogger(GMSLogDomain.GMS_LOGGER);
    private Thread flag;
    private String memberToken;
    private boolean sendMessages;
    private String serviceName;
    private static final int MILLIS = 4000;
    public static final String IIOP_MEMBER_DETAILS_KEY = "IIOPListenerEndPoints";
    public static final String TXLOGLOCATION = "TX_LOG_DIR";

    public GMSClientService(final String serviceName,
                            final String memberToken,
                            final boolean sendMessages){
        this.sendMessages = sendMessages;
        this.serviceName = serviceName;
        this.memberToken = memberToken;
        try {
            gms = GMSFactory.getGMSModule();
        }
        catch ( GMSException e ) {
            logger.log(Level.WARNING, e.getLocalizedMessage());
        }
        gms.addActionFactory(new PlannedShutdownActionFactoryImpl(this));
        gms.addActionFactory(new JoinNotificationActionFactoryImpl(this));
        gms.addActionFactory(new FailureNotificationActionFactoryImpl(this));
        if (serviceName.equals("TransactionService") &&
            memberToken != null && ! memberToken.equals("server")) {
            gms.addActionFactory(serviceName,
                                 new FailureRecoveryActionFactoryImpl(this));

        }
        gms.addActionFactory(new MessageActionFactoryImpl(this), serviceName);
        gms.addActionFactory(new JoinedAndReadyNotificationActionFactoryImpl(this));
    }

    public void start(){
        flag = new Thread(this, "GMSClient:"+serviceName);
        flag.start();
    }

    public void run() {
        GroupHandle gh = gms.getGroupHandle();

        // don't test fencing anymore.
//        while((gh.isFenced( serviceName, memberToken))){
//            try {
//                logger.log(Level.FINEST, "Waiting for fence to be lowered");
//                sleep(2000);
//            }
//            catch ( InterruptedException e ) {
//                logger.log(Level.WARNING, "Thread interrupted:"+
//                        e.getLocalizedMessage());
//            }
//        }
//        logger.log(Level.INFO, serviceName+":"+memberToken+
//                               ": is not fenced now, starting GMSclient:"+
//                               serviceName);
        logger.log(Level.INFO, "DUMPING:"+
                   gms.getAllMemberDetails(IIOP_MEMBER_DETAILS_KEY));
/*        final Thread thisThread = Thread.currentThread();

        //if this client is being stopped by the parent thread through call
        // to stopClient(), this flag will be null.
        while(flag == thisThread)
        {
            try {
                sleep(10000);
                if(sendMessages)
                {
                    logger.log(Level.INFO,"Sending 10 messages");
                    for(int i=1; i<=10; i++){
                        try {
                           gms.getGroupHandle().getDistributedStateCache()
                                   .addToCache(serviceName,memberToken, "Message "+i,
                                   MessageFormat.format("Message {0} from {1}", i, serviceName));
                        } catch (GMSException e) {
                            logger.log(Level.INFO, e.getMessage());
                            break;
                        }
                    }
                }
            } catch (InterruptedException e) {
                logger.log(Level.WARNING, e.getMessage());
            }
        } */
    }

    public synchronized void processNotification(final Signal notification){
        final String serverToken;
        final GroupHandle gh = gms.getGroupHandle();
        if (notification.getMemberToken().equals("admincli")) {
            return;
        }
        logger.log(Level.FINEST, new StringBuilder().append(serviceName)
                .append(": Notification Received for:")
                .append(notification.getMemberToken())
                .append(":[")
                .append(notification.toString())
                .append("] has been processed")
                .toString());
        if (notification instanceof FailureRecoverySignal) {
            FailureRecoverySignal signal = (FailureRecoverySignal) notification;

            final boolean SIMULATE_DSC_FAILURE = true;
            DistributedStateCacheImpl dsc = (DistributedStateCacheImpl) gms.getGroupHandle().getDistributedStateCache();
            if (SIMULATE_DSC_FAILURE) {

                // simulate that the FailureRecovery agent never received the DSC update for TX_LOG_DIR for the failed instance.
                // fix in GMS is if the instance that set the value is not current member, to ask the
                // longest running member what the value should be.  This simulation verifies that the
                // value is retrieved from the distributed state cache maintained on another cluster member.
                dsc.removeAllForMember(signal.getMemberToken());
                logger.info("in FailureRecovery notification: simulate missing TX_LOG_DIR for member:" + signal.getMemberToken() + " dsc:" + dsc);
            }
            String logdir = null;
            Map<Serializable, Serializable> failedMemberDetails = notification.getMemberDetails();
            if (failedMemberDetails != null) {
                logdir = (String) failedMemberDetails.get(TXLOGLOCATION);
            }
            if (logdir == null) {
                logger.severe("failed regression test for GLASSFISH-16422: distributed state cache value TX_LOG_DIR incorrectly null in FailureRecovery. dsc=" + dsc);
            } else {
                logger.info("FailureRecoveryAction for targetComponent: " + signal.getComponentName() + "  TX_LOG_DIR is " + logdir);
            }
            extractMemberDetails(notification, notification.getMemberToken());

        } else if (notification instanceof JoinNotificationSignal) {
            serverToken =
                    notification.getMemberToken();
            logger.info("Received JoinNotificationSignal for member " + serverToken + " componentService:" + this.serviceName +
                    " with state set to " + ((JoinNotificationSignal) notification).getMemberState().toString());

            // Commented out getting member details in join.
            // It is a bad idea to access distributed state cache info in join handler, it is too
            // early to have this info and just results in a lot pulling of data from origin
            // member.  Okey to call this in JoinedAndReady notification.
            //extractMemberDetails( notification, serverToken );

        } else if (notification instanceof JoinedAndReadyNotificationSignal) {
            serverToken =
                    notification.getMemberToken();
            JoinedAndReadyNotificationSignal jrSignal = (JoinedAndReadyNotificationSignal) notification;
            logger.info("Received " + notification.toString() + " for member " + serverToken);
            logger.info("JoinedAndReady for member:" + serverToken + ":" + serviceName +
                    " getPreviousAliveAndReadyCoreView()=" + gh.getPreviousAliveAndReadyCoreView() +
                    " getCurrentAliveAndReadyCoreView()=" + gh.getCurrentAliveAndReadyCoreView());
            logger.info("JoinedAndReady for member:" + serverToken + ":" + serviceName +
                    " signal.getPreviousView()=" + jrSignal.getPreviousView() +
                    " signal.getCurrentView()=" + jrSignal.getCurrentView());
            try {
                gms.getGroupHandle().sendMessage(serverToken, serviceName, "hello".getBytes());
                gms.getGroupHandle().sendMessage("server", serviceName, "hello".getBytes());
                gms.getGroupHandle().sendMessage("server", "nonExistentTargetComponent", "hello".getBytes());
                logger.log(Level.INFO, "send hello from member: " + gms.getInstanceName() + " to joinedandready instance: " + serverToken + " succeeded.");
            } catch (GMSException e) {
                logger.log(Level.WARNING, "failed to send hello message to newly joined member:" + serverToken + " trying one more time", e);
                try {
                    gms.getGroupHandle().sendMessage(serverToken, serviceName, "hello".getBytes());
                    logger.log(Level.INFO, "retry send hello from member: " + gms.getInstanceName() + " to joinedandready instance: " + serverToken + " succeeded.");
                } catch (GMSException ee) {
                    logger.log(Level.WARNING, "retried: failed to send hello message to newly joined member:" + serverToken, ee);
                }
            }

            extractMemberDetails(notification, serverToken);

        }
        else if ( notification instanceof FailureNotificationSignal || notification instanceof PlannedShutdownSignal) {
            AliveAndReadySignal arSignal = (AliveAndReadySignal)notification;
            serverToken = notification.getMemberToken();
            logger.info("Received "+ notification.toString() +" for member "+serverToken);
            logger.info(notification.getClass().getSimpleName() + " for member:" + serverToken +
                    " getPreviousAliveAndReadCoreView()=" + gh.getPreviousAliveAndReadyCoreView()+
                    " currentCoreView()=" + gh.getCurrentAliveAndReadyCoreView());
            logger.info(notification.getClass().getSimpleName() + " for member:" + serverToken +
                    " signal.getPreviousView()=" + arSignal.getPreviousView() +
                    " getCurrentView()=" + arSignal.getCurrentView());
            // ensure that FailureRecovery is first time trying to access TX_LOG_DIR info to simulate
            // GF-16422.
            //extractMemberDetails( notification, serverToken );
        } else if (notification instanceof MessageSignal) {
            MessageSignal msgSig = (MessageSignal)notification;
            logger.info("Received message " + msgSig.getMessage() + " from:" + msgSig.getMemberToken() + " targetComponent:" + msgSig.getTargetComponent());
        }
    }

    private void extractMemberDetails (
            final Signal notification, final String serverToken ) {
        logger.log(Level.FINEST, serviceName+":Now getting member details...");
        final Map memberDetails =
                notification.getMemberDetails();
        if(memberDetails.size() ==0){
            logger.log(Level.FINEST, "No Details available");
        }
        else{
            logger.log(Level.FINEST, memberDetails.toString());
            for(Object key : memberDetails.keySet()){
                logger.log(Level.FINEST,
                           new StringBuilder()
                           .append( "Got Member Details for " )
                           .append( serverToken ).toString());
                logger.log(Level.FINEST,
                           new StringBuilder().append( "Key:" )
                           .append( key )
                           .append( ":Value:" )
                           .append( memberDetails.get( key )
                                    .toString() )
                           .toString() );
            }
        }
    }

    public void stopClient(){
        flag = null;
    }
}
