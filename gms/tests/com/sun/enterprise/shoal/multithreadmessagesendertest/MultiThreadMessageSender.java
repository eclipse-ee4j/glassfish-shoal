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

package com.sun.enterprise.shoal.multithreadmessagesendertest;

import com.sun.enterprise.ee.cms.core.*;
import com.sun.enterprise.ee.cms.impl.client.MessageActionFactoryImpl;
import com.sun.enterprise.ee.cms.impl.client.JoinNotificationActionFactoryImpl;
import com.sun.enterprise.ee.cms.impl.base.Utility;
import org.glassfish.shoal.gms.common.JoinNotificationSignalImpl;
import org.glassfish.shoal.gms.common.MessageSignalImpl;
import com.sun.enterprise.ee.cms.logging.GMSLogDomain;
import org.glassfish.shoal.gms.common.GMSContext;
import org.glassfish.shoal.gms.common.GMSContextFactory;

import java.util.Properties;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Simple test for sending messages using multiple threads
 *
 * @author leehui
 */

public class MultiThreadMessageSender implements CallBack{

	private GroupManagementService gms;
	private String memberToken;
	private String destMemberToken;
	private int sendingThreadNum;
	private String msg = "hello, world";
    private AtomicInteger masterMsgId = new AtomicInteger(-1);
    private Thread[] threads;
	
	public MultiThreadMessageSender(String memberToken,String destMemberToken,int sendingThreadNum){
		
		this.memberToken = memberToken;
		this.destMemberToken = destMemberToken;
		this.sendingThreadNum = sendingThreadNum;
        this.threads = new Thread[sendingThreadNum];
				
	}
	
	public void start(){
		initGMS();		
		startSenderThread();
	}

    public void waitTillDone() {
        boolean done = false;
        boolean threadDone[] = new boolean[threads.length];

        for (int i=0; i < threadDone.length; i++) {
            threadDone[i] = false;
        }

        while (! done) {
            done = true;
            int i = 0;
            for (Thread t : threads) {
                if (!threadDone[i]) {
                    if (t.isAlive()) {
                        logger.finer("thread " + t.getName() + " still alive");
                        done = false;
                    } else {
                        threadDone[i] = true;
                        System.out.println("thread " + t.getName() + " has completed");
                    }
                }
            }
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {}
        }
        System.out.println("Done waiting for sending threads (number=" + threads.length + ") to complete");
        while (! completedCheck.get()) {
            System.out.println("Waiting to complete processing of expected incoming messages...");
            synchronized(completedCheck) {
                try {
                    completedCheck.wait(10000);
                } catch (InterruptedException ie) {}
            }

        }
        System.out.println("Completed processing of incoming messages");
        try {
            Thread.sleep(1000);
        } catch (Throwable t)  {}
        GMSContext ctx = GMSContextFactory.getGMSContext("DemoGroup");
        ctx.getGMSMonitor().report();
        gms.shutdown(GMSConstants.shutdownType.INSTANCE_SHUTDOWN);
    }

	private void initGMS(){
		try {
            Properties props = new Properties();
            props.put(ServiceProviderConfigurationKeys.INCOMING_MESSAGE_QUEUE_SIZE.toString(), "1500");
            props.put(ServiceProviderConfigurationKeys.MONITORING.toString(), "0");
			gms = (GroupManagementService) GMSFactory.startGMSModule(memberToken,"DemoGroup", GroupManagementService.MemberType.CORE, props);
			gms.addActionFactory(new MessageActionFactoryImpl(this),"SimpleSampleComponent");
            gms.addActionFactory(new JoinNotificationActionFactoryImpl(this));
			gms.join();
			Thread.sleep(5000);
		} catch (GMSException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {

		}
	}
	private void startSenderThread(){

        for(int i=0;i<sendingThreadNum; i++){
            final int i1 = i;
            threads[i] = new Thread(new Runnable(){
                String msg1 = msg + " "+ i1;
                public void run() {
                    int msgId = -1;
                    try {
                        List<String> members;
                        int i = -1;
                        members = gms.getGroupHandle().getAllCurrentMembers();
                        // System.out.println("Thead id: " + i1 + " members: " + members.toString());
                        while(true){
                            i++;
                            msgId = masterMsgId.getAndIncrement();
//                            if (msgId == 87) {
//                                // tmp test, skip one message sent to see if missed on receiving side.
//                                msgId = masterMsgId.getAndIncrement();
//                            }
                            msg1 = msg + " " + " threadid:" + i1 + " msgid:" + msgId + " " + payload.toString();
                            while (true) {
                                members = gms.getGroupHandle().getAllCurrentMembers();
                                try {
							        Thread.sleep(10);
                                    if (msgId >= EXPECTED_NUMBER_OF_MESSAGES) {
//                                        String stopMsg = "stop";
//                                        System.out.println("sending stop message");
//                                        gms.getGroupHandle().sendMessage(destMemberToken, "SimpleSampleComonent", stopMsg.getBytes());
//                                        gms.getGroupHandle().sendMessage(destMemberToken, "SimpleSampleComonent", stopMsg.getBytes());
                                        break;
                                    }
                                    if(members.size()>=2){
                                        gms.getGroupHandle().sendMessage(destMemberToken, "SimpleSampleComponent",msg1.getBytes());
                                        break;
                                    }
                               } catch (InterruptedException e) {
							        e.printStackTrace();
                                    //break;
                                } catch (GMSException e) {
                                    System.out.println("thread " + i + "caught GMSException " + e );
                                    e.printStackTrace();
                                //break;
                                }
                            }
                            if (msgId >= EXPECTED_NUMBER_OF_MESSAGES) {
                                break;
                            }
	                    }
                    } finally {
					    System.out.println("Exiting threadid " + i1);
                    }
                }
			}, "SendingThread_" + i);
            threads[i].start();
		}
	}


	public void processNotification(Signal arg0) {
        if (arg0 instanceof JoinNotificationSignal) {
            JoinNotificationSignal joinSig = (JoinNotificationSignal)arg0;
        } else if (arg0 instanceof MessageSignal) {
            int droppedMessages = 0;
            int localNumMsgReceived = 0;
            boolean localStopFlag = false;
            try {
                MessageSignal messageSignal = (MessageSignal) arg0;
                final String msgString = new String(messageSignal.getMessage());
                String outputStr = msgString;
                if (msgString.length() > 38) {
                    outputStr = msgString.substring(0,37) + "...truncated...";
                }
                int msgIdIdx = msgString.indexOf(" msgid:");
                if (msgIdIdx != -1) {
                    localNumMsgReceived = numMsgIdReceived.getAndIncrement();

                    String msgId = msgString.substring(msgIdIdx + 7, msgString.indexOf('X') - 1);
                    int msgIdInt = Integer.valueOf(msgId);
                    if (msgIdInt < msgIdReceived.length) {
                        msgIdReceived[msgIdInt] = true;
                    }
                    if (localNumMsgReceived >= EXPECTED_NUMBER_OF_MESSAGES) {
                        localStopFlag = true;
                    }
                } else {
                    System.out.println("comparing message:" + msgString + " to see if it is a stop command compareTo(stop)" + msgString.compareTo("stop"));
                    if (msgString.compareTo("stop") == 0) {
                        System.out.println("received stop message");
                        localStopFlag = true;
                    }
                }
                if ((localNumMsgReceived % 2500) == 0) {
                    System.out.println("received msg[" + localNumMsgReceived + "]: length:" + msgString.length() + " payload:" + outputStr);
                }
                if (localStopFlag && !completedCheck.get()) {
                    completedCheck.set(true);
                    System.out.println("Checking to see if first " + EXPECTED_NUMBER_OF_MESSAGES + " messages were received");

                    for (int i = 0; i < msgIdReceived.length; i++) {
                        if (!msgIdReceived[i]) {
                            droppedMessages++;
                            System.out.println("Never received msg id " + i);
                        }
                    }
                    if (droppedMessages == 0) {
                        System.out.println("PASS.  No dropped messages");
                    } else {
                        System.out.println("FAILED. Confirmed " + droppedMessages + " messages were dropped");
                    }
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }
    final static int NUM_MESSAGES_TO_SEND = 10000;
    static int EXPECTED_NUMBER_OF_MESSAGES;
    static private boolean msgIdReceived[];
    static private AtomicInteger numMsgIdReceived = new AtomicInteger(0);
    static private AtomicBoolean completedCheck = new AtomicBoolean(false);

    private static final Logger logger = GMSLogDomain.getLogger(GMSLogDomain.GMS_LOGGER);



    private static final int PAYLOADSIZE = 80 * 1024;
    private static final StringBuffer payload = new StringBuffer(PAYLOADSIZE);

	/**
	 * main
	 * 
	 * start serveral threads to send message
	 * 
	 * usage: start two instance using following two commands for the test 
	 * 			java MultiThreadMessageSender A B 20
	 * 		  	java MultiThreadMessageSender B A 0
	 * 
	 * @param args command line args
	 */
	public static void main(String[] args) {
		String memberToken = args[0];
		String destMemberToken = args[1];
		int sendingThreadNum = Integer.parseInt(args[2]);
        EXPECTED_NUMBER_OF_MESSAGES = NUM_MESSAGES_TO_SEND * sendingThreadNum;

        msgIdReceived = new boolean[EXPECTED_NUMBER_OF_MESSAGES];

        for (int i =0; i < msgIdReceived.length; i++) {
            msgIdReceived[i] = false;
        }
        Utility.setLogger(logger);
        Utility.setupLogHandler();
        logger.setLevel(Level.CONFIG);
        for (int i = 0; i < PAYLOADSIZE; i++) {
            payload.append('X');
        }        
		MultiThreadMessageSender multiThreadMessageSender = new MultiThreadMessageSender(memberToken,destMemberToken,sendingThreadNum);
		multiThreadMessageSender.start();
        multiThreadMessageSender.waitTillDone();
        logger.info("Test completed.");

	}
}

