#!/bin/sh +x
#
# Copyright (c) 2010, 2018 Oracle and/or its affiliates. All rights reserved.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License v. 2.0, which is available at
# http://www.eclipse.org/legal/epl-2.0.
#
# This Source Code may also be made available under the following Secondary
# Licenses when the conditions for such availability set forth in the
# Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
# version 2 with the GNU Classpath Exception, which is available at
# https://www.gnu.org/software/classpath/license.html.
#
# SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
#

EXECUTE_REMOTE_CONNECT=rsh

ECHO=`which echo`

TEST_LOG_LEVEL=INFO
SHOALGMS_LOG_LEVEL=CONFIG

CLUSTER_CONFIGS="./configs/clusters"
if [ -f "./configs/clusters" ]; then
   ${ECHO} "ERROR: the configs/clusters directory is missing"
   exit 1
fi
GROUPNAME=ha_msg_repl_sim_group

LOGS_DIR=LOGS/${GROUPNAME}
COLLECT_LOGS_DIR=""

TRANSPORT=grizzly
CMD=normal
NUMOFMEMBERS=10

NUMOFOBJECTS=10
MSGSPEROBJECT=100
MSGSIZE=4096

MULTICASTADDRESS=229.9.1.`./randomNumber.sh`
MULTICASTPORT=2299

# in milliseconds
THINKTIME=10

REPLICATIONTYPE=buddy

PUBLISH_HOME=./dist
LIB_HOME=./lib

GRIZZLY_JARS=${PUBLISH_HOME}/shoal-gms-tests.jar:${PUBLISH_HOME}/shoal-gms.jar:${LIB_HOME}/grizzly2-framework.jar:${LIB_HOME}/grizzly-framework.jar:${LIB_HOME}/grizzly-utils.jar
JXTA_JARS=${PUBLISH_HOME}/shoal-gms-tests.jar:${PUBLISH_HOME}/shoal-gms.jar:${LIB_HOME}/grizzly2-framework.jar:${LIB_HOME}/grizzly-framework.jar:${LIB_HOME}/grizzly-utils.jar:${LIB_HOME}/jxta.jar
JARS=${GRIZZLY_JARS}


DIST=false

usage () {
 ${ECHO} "usage:"
 ${ECHO} "--------------------------------------------------------------------------------------------"
 ${ECHO} "single machine:"
 ${ECHO} "  [-h] [-r type] [-t transport] [-g groupname] [-noo num] [-mpo num] [-ms num] [-bia address] [-n numberOfMembers] [-tll level] [-sll level] [-tt num]"
 ${ECHO} "     -t :  Transport type grizzly|jxta(default is grizzly)"
 ${ECHO} "     -g :  group name  (default is ${GROUPNAME})"
 ${ECHO} "     -noo :  Number of Objects(default is 10)"
 ${ECHO} "     -mpo :  Messages Per Object (default is 100)"
 ${ECHO} "     -ms :  Message size (default is 4096)"
 ${ECHO} "     -n : Number of CORE members in the group (default is 10)"
 ${ECHO} "     -tll :  Test log level (default is INFO)"
 ${ECHO} "     -sll :  ShoalGMS log level (default is INFO)"
 ${ECHO} "     -bia :  Bind Interface Address, used on a multihome machine"
 ${ECHO} "     -tt :  Think time during sending in milliseconds"
 ${ECHO} "     -r :  The type of replication to use buddy or chash (consistent hash) (default is buddy)"
 ${ECHO} "--------------------------------------------------------------------------------------------"
 ${ECHO} "   distributed environment manditory args:"
 ${ECHO} "  -d  <-g groupname> <-cl collectlogdir>"
 ${ECHO} "     -d :  Indicates this is test is run distributed"
 ${ECHO} "     -g :  group name  (default is ${GROUPNAME})"
 ${ECHO} "     -cl :  log directory where logs are copied to and analyzed"
 ${ECHO} "--------------------------------------------------------------------------------------------"
 ${ECHO} " "
 ${ECHO} " Examples:"
 ${ECHO} "     runHAMessageReplicationSimulator.sh"
 ${ECHO} "     runHAMessageReplicationSimulator.sh -noo 5 -mpo 256 -ms 1024 -n 5 "
 ${ECHO} "     runHAMessageReplicationSimulator.sh -d -g testgroup -l /net/machine1/test"
 ${ECHO} "     runHAMessageReplicationSimulator.sh -d -g testgroup -l /net/machine1/test -noo 5 -mpo 256 -ms 1024"
 ${ECHO} "     runHAMessageReplicationSimulator.sh -r chash -g testgroup -noo 250 -mpo 100 -ms 262144 -d -cl /net/machine1/logs"

 exit 1
}



analyzeLogs(){
    echo  "The following logs contain failures:"
    echo  "==============="
    grep -a "FAILED" ${LOGS_DIR}/instance*log
    echo  "==============="
    echo  "The following are the time results for SENDING messages:"
    grep -a "Sending Messages Time data" ${LOGS_DIR}/instance*log
    echo  "---------------"
    ${ECHO} "          Time Delta          MsgsPerSec  BytesPerSecond      KBytesPerSecond      Messagesize    NumOfObjects  MessagesPerObject    Signal Queue      Message Queue"
    #Delta
    DELTAMIN=`grep "Sending Messages Time data" ${LOGS_DIR}/instance*log | grep Delta | sed -e 's/^.*Delta//' | sed -e 's/sec.*$//' | sed -e 's/\[//' | sed -e 's/]//' | sort -n | head -n 1 | sed -e 's/ //' `
    DELTAMAX=`grep "Sending Messages Time data" ${LOGS_DIR}/instance*log | grep Delta | sed -e 's/^.*Delta//' | sed -e 's/sec.*$//' | sed -e 's/\[//' | sed -e 's/]//' | sort -n | tail -n 1 | sed -e 's/ //' `
    #MsgsPerSec
    MPSMIN=`grep "Sending Messages Time data" ${LOGS_DIR}/instance*log | grep MsgsPerSec | sed -e 's/^.*MsgsPerSec//' | sed -e 's/,.*$//' | sed -e 's/\[//' | sed -e 's/]//' | sort -n | head -n 1 `
    MPSMAX=`grep "Sending Messages Time data" ${LOGS_DIR}/instance*log | grep MsgsPerSec | sed -e 's/^.*MsgsPerSec//' | sed -e 's/,.*$//' | sed -e 's/\[//' | sed -e 's/]//' | sort -n | tail -n 1 `
    #BytesPerSecond
    BPSMIN=`grep "Sending Messages Time data" ${LOGS_DIR}/instance*log | grep BytesPerSecond | sed -e 's/^.*BytesPerSecond//' | sed -e 's/,.*$//' | sed -e 's/\[//' | sed -e 's/]//' | sort -n | head -n 1 `
    BPSMAX=`grep "Sending Messages Time data" ${LOGS_DIR}/instance*log | grep BytesPerSecond | sed -e 's/^.*BytesPerSecond//' | sed -e 's/,.*$//' | sed -e 's/\[//' | sed -e 's/]//' | sort -n | tail -n 1 `
    #KBytesPerSecond
    KBPSMIN=`grep "Sending Messages Time data" ${LOGS_DIR}/instance*log | grep KbytesPerSecond | sed -e 's/^.*KbytesPerSecond//' | sed -e 's/,.*$//' | sed -e 's/\[//' | sed -e 's/]//' | sort -n | head -n 1 `
    KBPSMAX=`grep "Sending Messages Time data" ${LOGS_DIR}/instance*log | grep KbytesPerSecond | sed -e 's/^.*KbytesPerSecond//' | sed -e 's/,.*$//' | sed -e 's/\[//' | sed -e 's/]//' | sort -n | tail -n 1 `
    #MsgSize
    MSGSIZE=`grep "Sending Messages Time data" ${LOGS_DIR}/instance*log | grep MsgSize | sed -e 's/^.*MsgSize//' | sed -e 's/,.*$//' | sed -e 's/\[//' | sed -e 's/]//' | sort -n | head -n 1 `
    SIGNALQUEUEWATERMARKMIN=`grep "high water" ${LOGS_DIR}/instance*log | grep "signal queue" | sed -e 's/^.*mark://' | sed -e 's/ .*$//' | sort -n | head -n 1 `
    SIGNALQUEUEWATERMARKMAX=`grep "high water" ${LOGS_DIR}/instance*log | grep "signal queue" | sed -e 's/^.*mark://' | sed -e 's/ .*$//' | sort -n | tail -n 1 `

    MESSAGEQUEUEWATERMARKMIN=`grep "high water" ${LOGS_DIR}/instance*log | grep "message queue" | sed -e 's/^.*mark://' | sed -e 's/ .*$//' | sort -n | head -n 1 `
    MESSAGEQUEUEWATERMARKMAX=`grep "high water" ${LOGS_DIR}/instance*log | grep "message queue" | sed -e 's/^.*mark://' | sed -e 's/ .*$//' | sort -n | tail -n 1 `

    ${ECHO} "send     ${DELTAMIN}-${DELTAMAX}     ${MPSMIN}-${MPSMAX}      ${BPSMIN}-${BPSMAX}     ${KBPSMIN}-${KBPSMAX}      ${MSGSIZE}         ${NUMOFOBJECTS}           ${MSGSPEROBJECT}                 ${SIGNALQUEUEWATERMARKMIN}-${SIGNALQUEUEWATERMARKMAX}         ${MESSAGEQUEUEWATERMARKMIN}-${MESSAGEQUEUEWATERMARKMAX}"

    echo  "==============="
    echo  "The following are the time results for RECEIVING messages:"
    grep -a "Receiving Messages Time data" ${LOGS_DIR}/instance*log
    echo  "---------------"
    ${ECHO} "          Time Delta          MsgsPerSec  BytesPerSecond      KBytesPerSecond        Messagesize    NumOfObjects  MessagesPerObject"
    #Delta
    DELTAMIN=`grep "Receiving Messages Time data" ${LOGS_DIR}/instance*log | grep Delta | sed -e 's/^.*Delta//' | sed -e 's/sec.*$//' | sed -e 's/\[//' | sed -e 's/]//' | sort -n | head -n 1 | sed -e 's/ //' `
    DELTAMAX=`grep "Receiving Messages Time data" ${LOGS_DIR}/instance*log | grep Delta | sed -e 's/^.*Delta//' | sed -e 's/sec.*$//' | sed -e 's/\[//' | sed -e 's/]//' | sort -n | tail -n 1 | sed -e 's/ //' `
    #MsgsPerSec
    MPSMIN=`grep "Receiving Messages Time data" ${LOGS_DIR}/instance*log | grep MsgsPerSec | sed -e 's/^.*MsgsPerSec//' | sed -e 's/,.*$//' | sed -e 's/\[//' | sed -e 's/]//' | sort -n | head -n 1 `
    MPSMAX=`grep "Receiving Messages Time data" ${LOGS_DIR}/instance*log | grep MsgsPerSec | sed -e 's/^.*MsgsPerSec//' | sed -e 's/,.*$//' | sed -e 's/\[//' | sed -e 's/]//' | sort -n | tail -n 1 `
    #BytesPerSecond
    BPSMIN=`grep "Receiving Messages Time data" ${LOGS_DIR}/instance*log | grep BytesPerSecond | sed -e 's/^.*BytesPerSecond//' | sed -e 's/,.*$//' | sed -e 's/\[//' | sed -e 's/]//' | sort -n | head -n 1 `
    BPSMAX=`grep "Receiving Messages Time data" ${LOGS_DIR}/instance*log | grep BytesPerSecond | sed -e 's/^.*BytesPerSecond//' | sed -e 's/,.*$//' | sed -e 's/\[//' | sed -e 's/]//' | sort -n | tail -n 1 `
    #KBytesPerSecond
    KBPSMIN=`grep "Sending Messages Time data" ${LOGS_DIR}/instance*log | grep KbytesPerSecond | sed -e 's/^.*KbytesPerSecond//' | sed -e 's/,.*$//' | sed -e 's/\[//' | sed -e 's/]//' | sort -n | head -n 1 `
    KBPSMAX=`grep "Sending Messages Time data" ${LOGS_DIR}/instance*log | grep KbytesPerSecond | sed -e 's/^.*KbytesPerSecond//' | sed -e 's/,.*$//' | sed -e 's/\[//' | sed -e 's/]//' | sort -n | tail -n 1 `
    #MsgSize
    MSGSIZE=`grep "Receiving Messages Time data" ${LOGS_DIR}/instance*log | grep MsgSize | sed -e 's/^.*MsgSize//' | sed -e 's/,.*$//' | sed -e 's/\[//' | sed -e 's/]//' | sort -n | head -n 1 `
    ${ECHO} "receive  ${DELTAMIN}-${DELTAMAX}     ${MPSMIN}-${MPSMAX}      ${BPSMIN}-${BPSMAX}     ${KBPSMIN}-${KBPSMAX}      ${MSGSIZE}         ${NUMOFOBJECTS}           ${MSGSPEROBJECT}"

    echo  "==============="
    echo  "The following are EXCEPTIONS found in the logs:"
    echo  "==============="
    grep -a "Exception" ${LOGS_DIR}/instance*log
    grep -a "Exception" ${LOGS_DIR}/server.log
    echo  "==============="
    echo  "The following are SEVERE messages found in the logs:"
    echo  "==============="
    grep -a "SEVERE" ${LOGS_DIR}/instance*log
    grep -a "SEVERE" ${LOGS_DIR}/server.log
    echo  "==============="
}



while [ $# -ne 0 ]
do
     case ${1} in
       -h)
         usage
       ;;
       -d)
         shift
         DIST=true
       ;;
       -cl)
         shift
         COLLECT_LOGS_DIR="${1}"
         shift
         if [ ! -d ${COLLECT_LOGS_DIR} ];then
            ${ECHO} "ERROR: Collect Log directory does not exist"
            usage
         fi
       ;;
       -g)
         shift
         GROUPNAME=${1}
         shift
         if [ -z "${GROUPNAME}" ] ;then
            ${ECHO} "ERROR: Missing group name value"
            usage
         fi
       ;;
       -bia)
         shift
         BIND_INTERFACE_ADDRESS="${1}"
         shift
         if [ -z "${BIND_INTERFACE_ADDRESS}" ]; then
            ${ECHO} "ERROR: Missing bind interface address value"
            usage
         fi
         BIND_INTERFACE_ADDRESS="-bia ${BIND_INTERFACE_ADDRESS}"
       ;;
       -noo)
         shift
         NUMOFOBJECTS=`${ECHO} "${1}" | egrep "^[0-9]+$" `
         shift
         if [ "${NUMOFOBJECTS}" != "" ]; then
            if [ ${NUMOFOBJECTS} -le 0 ];then
               ${ECHO} "ERROR: Invalid number of objects specified"
               usage
            fi
         else
            ${ECHO} "ERROR: Invalid number of objects specified"
            usage
         fi
       ;;
       -mpo)
         shift
         MSGSPEROBJECT=`${ECHO} "${1}" | egrep "^[0-9]+$" `
         shift
         if [ "${MSGSPEROBJECT}" != "" ]; then
            if [ ${MSGSPEROBJECT} -le 0 ];then
               ${ECHO} "ERROR: Invalid messages per object specified"
               usage
            fi
         else
            ${ECHO} "ERROR: Invalid messages per object specified"
            usage
         fi
       ;;
       -ms)
         shift
         MSGSIZE=`${ECHO} "${1}" | egrep "^[0-9]+$" `
         shift
         if [ "${MSGSIZE}" != "" ]; then
            if [ ${MSGSIZE} -le 0 ];then
               ${ECHO} "ERROR: Invalid messages size specified"
               usage
            fi
         else
            ${ECHO} "ERROR: Invalid messages size specified"
            usage
         fi
       ;;
       -t)
         shift
         TRANSPORT=${1}
         shift
         if [ ! -z "${TRANSPORT}" ] ;then
            if [ "${TRANSPORT}" != "grizzly" -a "${TRANSPORT}" != "jxta" ]; then
               ${ECHO} "ERROR: Invalid transport specified"
               usage
            fi
         else
            ${ECHO} "ERROR: Missing transport value"
            usage
         fi
       ;;
       -tll)
         shift
         TEST_LOG_LEVEL="${1}"
         shift
       ;;
       -sll)
         shift
         SHOALGMS_LOG_LEVEL="${1}"
         shift
       ;;
       -n)
         shift
         NUMOFMEMBERS=`${ECHO} "${1}" | egrep "^[0-9]+$" `
         shift
         if [ "${NUMOFMEMBERS}" != "" ]; then
            if [ ${NUMOFMEMBERS} -le 0 ];then
               ${ECHO} "ERROR: Invalid number of members specified"
               usage
            fi
         else
            ${ECHO} "ERROR: Invalid number of members specified"
            usage
         fi
       ;;
       -tt)
         shift
         THINKTIME=`${ECHO} "${1}" | egrep "^[0-9]+$" `
         shift
         if [ "${THINKTIME}" != "" ]; then
            if [ ${THINKTIME} -le 0 ];then
               ${ECHO} "ERROR: Invalid think time specified"
               usage
            fi
         else
            ${ECHO} "ERROR: Invalid think time specified"
            usage
         fi
       ;;
       -r)
         shift
         REPLICATIONTYPE="${1}"
         shift
         if [ ! -z "${REPLICATIONTYPE}" ] ;then
            if [ "${REPLICATIONTYPE}" != "buddy" -a "${REPLICATIONTYPE}" != "chash" ]; then
               ${ECHO} "ERROR: Invalid replication type specified"
               usage
            fi
         else
            ${ECHO} "ERROR: Missing or invalid argument for replication type"
            usage
         fi
       ;;
       *)
         ${ECHO} "ERROR: Invalid argument specified [${1}]"
         usage
       ;;
     esac
done


echo ${MULTICASTADDRESS} > ./currentMulticastAddress.txt

echo Transport: ${TRANSPORT}
if [ $TRANSPORT != "grizzly" ]; then
    JARS=${JXTA_JARS}
fi

if [ $DIST = true ]; then
    NUMOFMEMBERS=`find ${CLUSTER_CONFIGS}/${GROUPNAME} -name "*.properties" | grep -v server.properties | sort | wc -w`
    if [ -z "${COLLECT_LOGS_DIR}" ];then
       ${ECHO} "ERROR: When using distributed mode, you must specified the -cl option so logs can be saved and analyzed"
       usage
    fi
    touch ${COLLECT_LOGS_DIR}/test
    if [ ! -f "${COLLECT_LOGS_DIR}/test" ];then
       ${ECHO} "ERROR: Unable to write to the directory specified by -l [${COLLECT_LOGS_DIR}]"
       usage
    fi
    rm -rf ${COLLECT_LOGS_DIR}/test
fi
${ECHO} "NumberOfMembers: ${NUMOFMEMBERS}"
${ECHO} "LOGS_DIRS=${LOGS_DIR}"
${ECHO} "Killing any existing members and cleaning out old logs"
if [ $DIST = false ]; then
   ./killmembers.sh
   rm -rf ${LOGS_DIR}/*.log
else
    if [ -f ${CLUSTER_CONFIGS}/${GROUPNAME}/server.properties ]; then
       MEMBERS=`find ${CLUSTER_CONFIGS}/${GROUPNAME} -name "*.properties"  `
       for member in ${MEMBERS}
       do
          TMP=`egrep "^MACHINE_NAME" ${member}`
          MACHINE_NAME=`echo $TMP | awk -F= '{print $2}' `
          TMP=`egrep "^INSTANCE_NAME" ${member}`
          INSTANCE_NAME=`echo $TMP | awk -F= '{print $2}' `
          TMP=`egrep "^WORKSPACE_HOME" ${member}`
          WORKSPACE_HOME=`echo $TMP | awk -F= '{print $2}' `
          ${ECHO} "killing ${INSTANCE_NAME} on ${MACHINE_NAME}"
          ${EXECUTE_REMOTE_CONNECT} ${MACHINE_NAME} "cd ${WORKSPACE_HOME};./killmembers.sh;rm -rf ${LOGS_DIR}/*.log"
       done
    else
       ${ECHO} "ERROR: Could not find ${CLUSTER_CONFIGS}/${GROUPNAME}/server.properties"
       exit 1
    fi

fi

#--------------------------
# STARTING OF THE MASTER
if [ $DIST = false ]; then
    mkdir -p ${LOGS_DIR}
    ${ECHO} "Removing old logs"
    rm -f ${LOGS_DIR}/*.log
    ${ECHO} "Starting server"
    ./_HAMessageReplicationSimulator.sh server SPECTATOR ${NUMOFMEMBERS} -g ${GROUPNAME} -tll ${TEST_LOG_LEVEL} -sll ${SHOALGMS_LOG_LEVEL} -ma ${MULTICASTADDRESS} -mp ${MULTICASTPORT} ${BIND_INTERFACE_ADDRESS} -r ${REPLICATIONTYPE} >& ${LOGS_DIR}/server.log &
else
    if [ -f ${CLUSTER_CONFIGS}/${GROUPNAME}/server.properties ]; then
       TMP=`egrep "^MACHINE_NAME" ${CLUSTER_CONFIGS}/${GROUPNAME}/server.properties`
       MACHINE_NAME=`echo $TMP | awk -F= '{print $2}' `
       TMP=`egrep "^WORKSPACE_HOME" ${CLUSTER_CONFIGS}/${GROUPNAME}/server.properties`
       WORKSPACE_HOME=`echo $TMP | awk -F= '{print $2}' `
       TMP=`egrep "^BIND_INTERFACE_ADDRESS" ${CLUSTER_CONFIGS}/${GROUPNAME}/server.properties`
       BIND_INTERFACE_ADDRESS=`echo $TMP | awk -F= '{print $2}' `
       if [ ! -z ${BIND_INTERFACE_ADDRESS} ];then
          BIND_INTERFACE_ADDRESS="-bia ${BIND_INTERFACE_ADDRESS}"
       fi
       ${ECHO} "Starting server on ${MACHINE_NAME}"
       ${EXECUTE_REMOTE_CONNECT} ${MACHINE_NAME} "cd ${WORKSPACE_HOME}; mkdir -p ${LOGS_DIR};./_HAMessageReplicationSimulator.sh server SPECTATOR ${NUMOFMEMBERS} -g ${GROUPNAME} -tll ${TEST_LOG_LEVEL} -sll ${SHOALGMS_LOG_LEVEL} -ma ${MULTICASTADDRESS} -mp ${MULTICASTPORT} ${BIND_INTERFACE_ADDRESS}  -r ${REPLICATIONTYPE} -l ${WORKSPACE_HOME}/${LOGS_DIR}"
    else
       ${ECHO} "ERROR: Could not find ${CLUSTER_CONFIGS}/${GROUPNAME}/server.properties"
       exit 1
    fi
fi

#--------------------------
# Give time for the server to startup before starting the other members
sleep 5

sdtcp=9161
edtcp=`expr ${sdtcp} + 30`
if [ $DIST = false ]; then
    # single machine startup
    ${ECHO} "Starting ${NUMOFMEMBERS} CORE members"
    count=1
    while [ $count -le ${NUMOFMEMBERS} ]
    do
        INSTANCE_NAME="instance`expr ${count} + 100 `"
        ${ECHO} "Starting ${INSTANCE_NAME}"
        MEMBERSTARTCMD="./_HAMessageReplicationSimulator.sh ${INSTANCE_NAME} CORE ${NUMOFMEMBERS} -noo ${NUMOFOBJECTS} -mpo ${MSGSPEROBJECT} -ms ${MSGSIZE} -g ${GROUPNAME} -tll ${TEST_LOG_LEVEL} -sll ${SHOALGMS_LOG_LEVEL} -ts ${sdtcp} -te ${edtcp} -ma ${MULTICASTADDRESS} -mp ${MULTICASTPORT} ${BIND_INTERFACE_ADDRESS} -l ${LOGS_DIR}  -r ${REPLICATIONTYPE} -tt ${THINKTIME}"
        ${MEMBERSTARTCMD}

        sdtcp=`expr ${edtcp} + 1`
        edtcp=`expr ${sdtcp} + 30`
        count=`expr ${count} + 1`
    done
else
   # distributed environment startup
   ${ECHO} "Starting CORE members in the distributed environment"

   MEMBERS=`find ${CLUSTER_CONFIGS}/${GROUPNAME} -name "*.properties" | grep -v server.properties  `
   for member in ${MEMBERS}
   do
      TMP=`egrep "^MACHINE_NAME" ${member}`
      MACHINE_NAME=`echo $TMP | awk -F= '{print $2}' `
      TMP=`egrep "^INSTANCE_NAME" ${member}`
      INSTANCE_NAME=`echo $TMP | awk -F= '{print $2}' `
      TMP=`egrep "^WORKSPACE_HOME" ${member}`
      WORKSPACE_HOME=`echo $TMP | awk -F= '{print $2}' `
      TMP=`egrep "^BIND_INTERFACE_ADDRESS" ${member}`
      BIND_INTERFACE_ADDRESS=`echo $TMP | awk -F= '{print $2}' `
      if [ ! -z ${BIND_INTERFACE_ADDRESS} ];then
          BIND_INTERFACE_ADDRESS="-bia ${BIND_INTERFACE_ADDRESS}"
      fi
      ${ECHO} "Starting ${INSTANCE_NAME} on ${MACHINE_NAME}"

      MEMBERSTARTCMD="./_HAMessageReplicationSimulator.sh ${INSTANCE_NAME} CORE ${NUMOFMEMBERS} -noo ${NUMOFOBJECTS} -mpo ${MSGSPEROBJECT} -ms ${MSGSIZE} -g ${GROUPNAME} -tll ${TEST_LOG_LEVEL} -sll ${SHOALGMS_LOG_LEVEL} -ts ${sdtcp} -te ${edtcp} -ma ${MULTICASTADDRESS} -mp ${MULTICASTPORT} ${BIND_INTERFACE_ADDRESS} -l ${WORKSPACE_HOME}/${LOGS_DIR} -r ${REPLICATIONTYPE} -tt ${THINKTIME}"
      ${EXECUTE_REMOTE_CONNECT} ${MACHINE_NAME} "cd ${WORKSPACE_HOME}; mkdir -p ${LOGS_DIR}; ${MEMBERSTARTCMD}"
   done
fi

${ECHO} "Waiting for testing to complete"
if [ $DIST = false ]; then
    ./_HAMessageReplicationSimulator.sh -wait ${NUMOFMEMBERS} -g ${GROUPNAME} -l ${LOGS_DIR}
    analyzeLogs
else
    # we are running in a dist mode and we want to wait until all the instances are done and the server.log contains Testing Complete
    if [ -f ${CLUSTER_CONFIGS}/${GROUPNAME}/server.properties ]; then
       TMP=`egrep "^MACHINE_NAME" ${CLUSTER_CONFIGS}/${GROUPNAME}/server.properties`
       MACHINE_NAME=`echo $TMP | awk -F= '{print $2}' `
       TMP=`egrep "^WORKSPACE_HOME" ${CLUSTER_CONFIGS}/${GROUPNAME}/server.properties`
       WORKSPACE_HOME=`echo $TMP | awk -F= '{print $2}' `
       ${ECHO} "Waiting for server on ${MACHINE_NAME} to complete testing"
       ${EXECUTE_REMOTE_CONNECT} ${MACHINE_NAME} "cd ${WORKSPACE_HOME};./_HAMessageReplicationSimulator.sh -wait ${NUMOFMEMBERS} -g ${GROUPNAME} -l ${WORKSPACE_HOME}/${LOGS_DIR}"

       ${ECHO} "Collecting logs from all machines"
       # remove any existing files before doing copy
       rm -rf ${COLLECT_LOGS_DIR}/*

       MEMBERS=`find ${CLUSTER_CONFIGS}/${GROUPNAME} -name "*.properties"  `
       for member in ${MEMBERS}
       do
          TMP=`egrep "^MACHINE_NAME" ${member}`
          MACHINE_NAME=`echo $TMP | awk -F= '{print $2}' `
          TMP=`egrep "^INSTANCE_NAME" ${member}`
          INSTANCE_NAME=`echo $TMP | awk -F= '{print $2}' `
          TMP=`egrep "^WORKSPACE_HOME" ${member}`
          WORKSPACE_HOME=`echo $TMP | awk -F= '{print $2}' `
          ${ECHO} "Collecting logs from ${INSTANCE_NAME} on ${MACHINE_NAME}"

         ${EXECUTE_REMOTE_CONNECT} ${MACHINE_NAME} "cd ${WORKSPACE_HOME}; cp -r ${LOGS_DIR}/* ${COLLECT_LOGS_DIR}"
      done
      LOGS_DIR=${COLLECT_LOGS_DIR}
      ./analyzeLogs
    else
       ${ECHO} "ERROR: Could not find ${CLUSTER_CONFIGS}/${GROUPNAME}/server.properties"
       exit 1
    fi
fi
${ECHO} "Testing Complete"
