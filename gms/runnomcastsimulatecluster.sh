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

EXECUTE_REMOTE_CONNECT=/usr/bin/rsh

ADMINCLI_LOG_LEVEL=WARNING
ADMINCLI_SHOALGMS_LOG_LEVEL=WARNING

TEST_LOG_LEVEL=INFO
SHOALGMS_LOG_LEVEL=CONFIG

CLUSTER_CONFIGS="./configs/clusters"
if [ -f "./configs/clusters" ]; then
   echo "ERROR: the configs/clusters directory is missing"
   exit 1
fi
TRANSPORT=grizzly
CMD=default
NUMOFMEMBERS=10

TCPSTARTPORT=9160
TCPENDPORT=9220
GROUPNAME=testgroup
MULTICASTADDRESS=229.9.1.`./randomNumber.sh`
MULTICASTPORT=2299
BINDINTERFACEADDRESS=""
MAXMISSEDHEARTBEATS="-mmh 10"
DIST=false

usage () {
 echo "usage:"
 echo "   single machine:"
 echo "      [-h] [-t grizzly|jxta] [-bia address] [add|stop|kill|rejoin|default] [numberOfMembers(10 is default)] "
 echo "   distributed environment:"
 echo "      -d <-g groupname> [-t grizzly|jxta] [add|stop|kill|rejoin|default]"
 echo " "
 echo " Examples:"
 echo "     runsimulatecluster.sh"
 echo "     runsimulatecluster.sh 5 -bia 129.168.1.4 rejoin"
 echo "     runsimulatecluster.sh -d -g testgroup"
 echo "     runsimulatecluster.sh -d -g testgroup rejoin"
 exit 1
}

while [ $# -ne 0 ]
do
     case ${1} in
       -h)
         usage
       ;;
       add|stop|kill|rejoin)
         CMD=${1}
         shift
         if [ ! -z "${CMD}" ] ;then
            if [ "${CMD}" != "add" -a "${CMD}" != "stop" -a "${CMD}" != "kill" -a "${CMD}" != "rejoin" -a "${CMD}" != "default" ]; then
               echo "ERROR: Invalid command specified"
               usage
            fi
         else
            echo "ERROR: Missing command value"
            usage
         fi
         if [ "${CMD}" = "rejoin" ]; then
            MMH=${MAXMISSEDHEARTBEATS}
         else
            MMH=""
         fi
       ;;
       -bia)
         shift
         BINDINGINTERFACEADDRESS=${1}
         shift
         ;;
       -d)
         shift
         DIST=true
       ;;
       -g)
         shift
         GROUPNAME=${1}
         shift
         if [ -z "${GROUPNAME}" ] ;then
            echo "ERROR: Missing group name value"
            usage
         fi
       ;;
       -t)
         shift
         TRANSPORT=${1}
         shift
         if [ ! -z "${TRANSPORT}" ] ;then
            if [ "${TRANSPORT}" != "grizzly" -a "${TRANSPORT}" != "jxta" ]; then
               echo "ERROR: Invalid transport specified"
               usage
            fi
         else
            echo "ERROR: Missing transport value"
            usage
         fi
       ;;
       *)
         NUMOFMEMBERS=`echo "${1}" | egrep "^[0-9]+$" `
         shift
         if [ "${NUMOFMEMBERS}" != "" ]; then
            if [ ${NUMOFMEMBERS} -le 0 ];then
               echo "ERROR: Invalid number of members specified"
               usage
            fi
         else
            echo "ERROR: Invalid number of members specified"
            usage
         fi
       ;;
     esac
done

rm -rf ./currentMulticastAddress.txt
echo ${MULTICASTADDRESS} > ./currentMulticastAddress.txt
echo "The Multicast Address being used is: `cat ./currentMulticastAddress.txt`"

echo Comand: ${CMD}
echo Transport: ${TRANSPORT}

INSTANCE_EFFECTED=instance00
if [ $DIST = true ]; then
    NUMOFMEMBERS=`find ${CLUSTER_CONFIGS}/${GROUPNAME} -name "*.properties" | grep -v server.properties | sort | wc -w`
fi
echo NumberOfMembers: ${NUMOFMEMBERS}

if [ ${NUMOFMEMBERS} -gt 1 ]; then
       # INSTANCE_EFFECTED=`./randomNumber.sh ${NUMOFMEMBERS}`
        INSTANCE_EFFECTED=`expr ${NUMOFMEMBERS} / 2 `
        if [  ${INSTANCE_EFFECTED} -lt 10 ]; then
            INSTANCE_EFFECTED="instance0${INSTANCE_EFFECTED}"
        else
            INSTANCE_EFFECTED="instance${INSTANCE_EFFECTED}"
        fi
        echo Instance Effected: ${INSTANCE_EFFECTED}
else
        echo "ERROR: The number of members specified [${NUMOFMEMBERS}] must be greater and 1 for command [${CMD}]"
        usage
fi

if [ "${CMD}" = "default" ]; then
   LOGS_DIR=LOGS/simulateCluster
else
   LOGS_DIR=LOGS/simulateCluster_${CMD}
fi

echo "LOGS_DIRS=${LOGS_DIR}"

#--------------------------
# killing of any previous processes and remove and create logs dir
if [ $DIST = false ]; then
   killmembers.sh
   rm -rf ${LOGS_DIR}
   mkdir -p ${LOGS_DIR}
else
   MEMBERS=`find ${CLUSTER_CONFIGS}/${GROUPNAME} -name "*.properties"  `
   for member in ${MEMBERS}
   do
       TMP=`egrep "^MACHINE_NAME" ${member}`
       MACHINE_NAME=`echo $TMP | awk -F= '{print $2}' `
       TMP=`egrep "^WORKSPACE_HOME" ${member}`
       WORKSPACE_HOME=`echo $TMP | awk -F= '{print $2}' `
       echo "Killing processes on ${MACHINE_NAME}"
       ${EXECUTE_REMOTE_CONNECT} ${MACHINE_NAME} "cd ${WORKSPACE_HOME};killmembers.sh; rm -rf ${LOGS_DIR}; mkdir -p ${LOGS_DIR}" &
   done
   echo "Waiting for remote process(es) to terminate"
   wait
fi

echo "Collecting initial netstat results from server"
echo "----------------------------------------------"
if [ $DIST = false ]; then
    netstat.sh -u -r ${LOGS_DIR}/netstat_server.log
else
    TMP=`egrep "^MACHINE_NAME" ${CLUSTER_CONFIGS}/${GROUPNAME}/server.properties`
    MASTER_MACHINE_NAME=`echo $TMP | awk -F= '{print $2}' `
    TMP=`egrep "^WORKSPACE_HOME" ${CLUSTER_CONFIGS}/${GROUPNAME}/server.properties`
    MASTER_WORKSPACE_HOME=`echo $TMP | awk -F= '{print $2}' `
    ${EXECUTE_REMOTE_CONNECT} ${MASTER_MACHINE_NAME} "cd ${MASTER_WORKSPACE_HOME}; netstat.sh -u -r ${LOGS_DIR}/netstat_server.log"
fi
if [ $DIST = false ]; then
    # single machine test
    count=1
    while [ $count -le ${NUMOFMEMBERS} ]
    do
        if [  ${count} -lt 10 ]; then
           INSTANCE_NAME="instance0${count}"
        else
           INSTANCE_NAME=instance${count}
        fi
        echo "Collecting netstat results from ${INSTANCE_NAME}"
        echo "---------------------------------------------------"
        netstat.sh -u -r ${LOGS_DIR}/netstat_${INSTANCE_NAME}.log
        count=`expr ${count} + 1`
    done
else
   MEMBERS=`find ${CLUSTER_CONFIGS}/${GROUPNAME} -name "*.properties" | grep -v server.properties  `
   for member in ${MEMBERS}
   do
      TMP=`egrep "^MACHINE_NAME" ${member}`
      MACHINE_NAME=`echo $TMP | awk -F= '{print $2}' `
      TMP=`egrep "^INSTANCE_NAME" ${member}`
      INSTANCE_NAME=`echo $TMP | awk -F= '{print $2}' `
      TMP=`egrep "^WORKSPACE_HOME" ${member}`
      WORKSPACE_HOME=`echo $TMP | awk -F= '{print $2}' `
      echo "Collecting netstat results from ${INSTANCE_NAME}"
      echo "---------------------------------------------------"
      ${EXECUTE_REMOTE_CONNECT} ${MACHINE_NAME} "cd ${WORKSPACE_HOME};netstat.sh -u -r ${LOGS_DIR}/netstat_${INSTANCE_NAME}.log"
   done
fi

#--------------------------
# STARTING OF THE MASTER
if [ $DIST = false ]; then
    mkdir -p ${LOGS_DIR}
    echo "Removing old server log"
    rm -f ${LOGS_DIR}/server.log
    echo "Starting server"
    if [ ! -z "${BINDINGINTERFACEADDRESS}" ]; then
       BIA="-bia ${BINDINGINTERFACEADDRESS}"
    else
       BIA=""
    fi
    ./rungmsdemo_nomcast.sh server ${GROUPNAME} SPECTATOR 0 ${SHOALGMS_LOG_LEVEL} ${TRANSPORT} -tl ${TEST_LOG_LEVEL} -ts 9090 -te 9090 -ma ${MULTICASTADDRESS} -mp ${MULTICASTPORT} -l ${LOGS_DIR} ${BIA} ${MMH}
else
    if [ -f ${CLUSTER_CONFIGS}/${GROUPNAME}/server.properties ]; then
       TMP=`egrep "^MACHINE_NAME" ${CLUSTER_CONFIGS}/${GROUPNAME}/server.properties`
       MACHINE_NAME=`echo $TMP | awk -F= '{print $2}' `
       TMP=`egrep "^WORKSPACE_HOME" ${CLUSTER_CONFIGS}/${GROUPNAME}/server.properties`
       WORKSPACE_HOME=`echo $TMP | awk -F= '{print $2}' `
       TMP=`egrep "^TCPSTARTPORT" ${CLUSTER_CONFIGS}/${GROUPNAME}/server.properties`
       if [ ! -z "${TMP}" ]; then
           TSA="-ts `echo $TMP | awk -F= '{print $2}' ` "
       else
           TSA=""
       fi
       TMP=`egrep "^TCPENDPORT" ${CLUSTER_CONFIGS}/${GROUPNAME}/server.properties`
       if [ ! -z "${TMP}" ]; then
           TEA="-te `echo $TMP | awk -F= '{print $2}' ` "
       else
           TEA=""
       fi
       TMP=`egrep "^BIND_INTERFACE_ADDRESS" ${CLUSTER_CONFIGS}/${GROUPNAME}/server.properties`
       if [ ! -z "${TMP}" ]; then
           BIA="-bia `echo $TMP | awk -F= '{print $2}' ` "
       else
           BIA=""
       fi
       echo "Starting server on ${MACHINE_NAME}"
       ${EXECUTE_REMOTE_CONNECT} ${MACHINE_NAME} "cd ${WORKSPACE_HOME};killmembers.sh; rm -rf ${LOGS_DIR}/server.log; mkdir -p ${LOGS_DIR}; ${WORKSPACE_HOME}/rungmsdemo_nomcast.sh server ${GROUPNAME} SPECTATOR 0 ${SHOALGMS_LOG_LEVEL} ${TRANSPORT} -tl ${TEST_LOG_LEVEL} ${TSA} ${TEA} -ma ${MULTICASTADDRESS} -mp ${MULTICASTPORT} -l ${WORKSPACE_HOME}/${LOGS_DIR} ${BIA} ${MMH}"
    else
       echo "ERROR: Could not find ${CLUSTER_CONFIGS}/${GROUPNAME}/server.properties"
       exit 1
    fi
fi

#--------------------------
# Give time for the master to startup before starting the other members
sleep 5
SDTCP=${TCPSTARTPORT}
EDTCP=${TCPENDPORT}
if [ $DIST = false ]; then
    # single machine startup
    echo `date` "Starting ${NUMOFMEMBERS} CORE members"
    count=1
    while [ $count -le ${NUMOFMEMBERS} ]
    do
        if [  ${count} -lt 10 ]; then
           INSTANCE_NAME="instance0${count}"
        else
           INSTANCE_NAME=instance${count}
        fi
        if [ ! -z "${BINDINGINTERFACEADDRESS}" ]; then
          BIA="-bia ${BINDINGINTERFACEADDRESS}"
        else
          BIA=""
        fi

        MEMBERSTARTCMD="./rungmsdemo_nomcast.sh ${INSTANCE_NAME} ${GROUPNAME} CORE 0 ${SHOALGMS_LOG_LEVEL} ${TRANSPORT} -tl ${TEST_LOG_LEVEL} -ts ${SDTCP} -te ${EDTCP} -ma ${MULTICASTADDRESS} -mp ${MULTICASTPORT} -l ${LOGS_DIR} ${BIA} ${MMH}"
        if [ "${CMD}" = "add" -a ${INSTANCE_NAME} = ${INSTANCE_EFFECTED} ]; then
           echo "Not Starting ${INSTANCE_NAME}, it will be started later"
        else
           echo "Starting ${INSTANCE_NAME}"
           ${MEMBERSTARTCMD} &
        fi

        if [ ${INSTANCE_NAME} = ${INSTANCE_EFFECTED} ]; then
           EFFECTED_MEMBERSTARTCMD=${MEMBERSTARTCMD}
        fi

# all instances use the exact same portrange. no more mutual exclusive range requirement.
        count=`expr ${count} + 1`
    done
else
   # distributed environment startup
    echo "Starting CORE members in the distributed environment"

   MEMBERS=`find ${CLUSTER_CONFIGS}/${GROUPNAME} -name "*.properties" | grep -v server.properties  `
   for member in ${MEMBERS}
   do

      TMP=`egrep "^MACHINE_NAME" ${member}`
      MACHINE_NAME=`echo $TMP | awk -F= '{print $2}' `
      TMP=`egrep "^INSTANCE_NAME" ${member}`
      INSTANCE_NAME=`echo $TMP | awk -F= '{print $2}' `
      TMP=`egrep "^WORKSPACE_HOME" ${member}`
      WORKSPACE_HOME=`echo $TMP | awk -F= '{print $2}' `
      TMP=`egrep "^TCPSTARTPORT" ${member}`
      if [ ! -z "${TMP}" ]; then
           SDTCP=`echo $TMP | awk -F= '{print $2}' `
      fi
      TMP=`egrep "^TCPENDPORT" ${member}`
      if [ ! -z "${TMP}" ]; then
           EDTCP=`echo $TMP | awk -F= '{print $2}' `
      fi
      TMP=`egrep "^BIND_INTERFACE_ADDRESS" ${member}`
      if [ ! -z "${TMP}" ]; then
           BIA="-bia `echo $TMP | awk -F= '{print $2}' ` "
      fi
      MEMBERSTARTCMD="./rungmsdemo_nomcast.sh ${INSTANCE_NAME} ${GROUPNAME} CORE 0 ${SHOALGMS_LOG_LEVEL} ${TRANSPORT} -tl ${TEST_LOG_LEVEL} -ts ${SDTCP} -te ${EDTCP} -ma ${MULTICASTADDRESS} -mp ${MULTICASTPORT} -l ${WORKSPACE_HOME}/${LOGS_DIR} ${BIA} ${MMH}"
      if [ "${CMD}" = "add" -a ${INSTANCE_NAME} = ${INSTANCE_EFFECTED} ]; then
         echo "Not Starting ${INSTANCE_NAME}, it will be started later"
      else
         echo "Starting ${INSTANCE_NAME} on ${MACHINE_NAME}"
         ${EXECUTE_REMOTE_CONNECT} ${MACHINE_NAME} "cd ${WORKSPACE_HOME};killmembers.sh; rm -rf ${LOGS_DIR}/ins*.log; mkdir -p ${LOGS_DIR}; ${MEMBERSTARTCMD}" &
      fi
      if [ ${INSTANCE_NAME} = ${INSTANCE_EFFECTED} ]; then
               EFFECTED_MEMBERSTARTCMD=${MEMBERSTARTCMD}
               EFFECTED_MEMBER_MACHINE_NAME=${MACHINE_NAME}
               EFFECTED_MEMBER_WORKSPACE_HOME=${WORKSPACE_HOME}
       fi
   done
   TMP=`egrep "^MACHINE_NAME" ${CLUSTER_CONFIGS}/${GROUPNAME}/server.properties`
   MASTER_MACHINE_NAME=`echo $TMP | awk -F= '{print $2}' `
   TMP=`egrep "^WORKSPACE_HOME" ${CLUSTER_CONFIGS}/${GROUPNAME}/server.properties`
   MASTER_WORKSPACE_HOME=`echo $TMP | awk -F= '{print $2}' `
fi
wait
#
# setting up the BIA for the admin cli
#
if [ $DIST = false ]; then
    if [ ! -z "${BINDINGINTERFACEADDRESS}" ]; then
         BIA="-bia ${BINDINGINTERFACEADDRESS}"
    else
        BIA=""
    fi
else
    if [ -f ${CLUSTER_CONFIGS}/${GROUPNAME}/server.properties ]; then
       TMP=`egrep "^BIND_INTERFACE_ADDRESS" ${CLUSTER_CONFIGS}/${GROUPNAME}/server.properties`
       if [ ! -z "${TMP}" ]; then
           BIA="-bia `echo $TMP | awk -F= '{print $2}' ` "
       else
           BIA=""
       fi
    else
       BIA=""
    fi
fi

echo `date` "Waiting for group [${GROUPNAME}] to complete startup"
# we do not want test or shoal output unless we really needit, there we set both types of logging to the same value
ADMINCMD="./gms_admin_nomcast.sh waits ${GROUPNAME} -t ${TRANSPORT} -tl ${ADMINCLI_LOG_LEVEL} -sl ${ADMINCLI_SHOALGMS_LOG_LEVEL} -ma ${MULTICASTADDRESS} -mp ${MULTICASTPORT} ${BIA}"
if [ $DIST = false ]; then
    ${ADMINCMD}
else
    ${EXECUTE_REMOTE_CONNECT} ${MASTER_MACHINE_NAME} "cd ${MASTER_WORKSPACE_HOME}; ${ADMINCMD}"
fi

echo `date` "Group startup has completed"

if [ "${CMD}" = "stop" ]; then
       echo "Stopping ${INSTANCE_EFFECTED}"
       ADMINCMD="./gms_admin_nomcast.sh stopm ${GROUPNAME} ${INSTANCE_EFFECTED} -resend -t ${TRANSPORT} -tl ${ADMINCLI_LOG_LEVEL} -sl ${ADMINCLI_SHOALGMS_LOG_LEVEL} -ma ${MULTICASTADDRESS} -mp ${MULTICASTPORT} ${BIA}"

       if [ $DIST = false ]; then
           ${ADMINCMD}
       else
           ${EXECUTE_REMOTE_CONNECT} ${MASTER_MACHINE_NAME} "cd ${MASTER_WORKSPACE_HOME}; ${ADMINCMD}"
       fi
       echo "sleeping 15 seconds"
       sleep 15
       echo "Restarting ${INSTANCE_EFFECTED}"
       if [ $DIST = false ]; then
           ${EFFECTED_MEMBERSTARTCMD}
       else
           ${EXECUTE_REMOTE_CONNECT} ${EFFECTED_MEMBER_MACHINE_NAME} "cd ${EFFECTED_MEMBER_WORKSPACE_HOME}; ${EFFECTED_MEMBERSTARTCMD}"
       fi
       count=1
       CMD_OK=false
       while [ true ]
       do
         ADMINCMD="./gms_admin_nomcast.sh list ${GROUPNAME} ${INSTANCE_EFFECTED} -t ${TRANSPORT} -tl ${ADMINCLI_LOG_LEVEL} -sl ${ADMINCLI_SHOALGMS_LOG_LEVEL} -ma ${MULTICASTADDRESS} -mp ${MULTICASTPORT} ${BIA}"
         if [ $DIST = false ]; then
             TMP=`${ADMINCMD}`
         else
             TMP=`${EXECUTE_REMOTE_CONNECT} ${MASTER_MACHINE_NAME} "cd ${MASTER_WORKSPACE_HOME}; ${ADMINCMD}" `
         fi
         #echo $TMP
         _TMP=`echo ${TMP} | grep "WAS SUCCESSFUL"`
         if [ ! -z "${_TMP}" ];then
            CMD_OK=true
            break;
         fi
         count=`expr ${count} + 1`
         if [ ${count} -gt 10 ]; then
            break
         fi
         sleep 1
       done
       if [ ${CMD_OK} = true ]; then
            echo "Instance ${INSTANCE_EFFECTED} has restarted"
       else
            echo "ERROR: Instance ${INSTANCE_EFFECTED} DID NOT restarted"
       fi
elif [ "${CMD}" = "kill" ]; then
       echo "Killing ${INSTANCE_EFFECTED}"
       ADMINCMD="./gms_admin_nomcast.sh killm ${GROUPNAME} ${INSTANCE_EFFECTED} -t ${TRANSPORT} -tl ${ADMINCLI_LOG_LEVEL} -sl ${ADMINCLI_SHOALGMS_LOG_LEVEL} -ma ${MULTICASTADDRESS} -mp ${MULTICASTPORT} ${BIA}"
       if [ $DIST = false ]; then
           ${ADMINCMD}
       else
           ${EXECUTE_REMOTE_CONNECT} ${MASTER_MACHINE_NAME} "cd ${MASTER_WORKSPACE_HOME}; ${ADMINCMD}"
       fi
       echo "killed instance at" `date` ". sleeping 15 seconds"
       sleep 15
       echo "Restarting ${INSTANCE_EFFECTED}"
       if [ $DIST = false ]; then
           ${EFFECTED_MEMBERSTARTCMD}
       else
           ${EXECUTE_REMOTE_CONNECT} ${EFFECTED_MEMBER_MACHINE_NAME} "cd ${EFFECTED_MEMBER_WORKSPACE_HOME}; ${EFFECTED_MEMBERSTARTCMD}"
       fi
       count=1
       CMD_OK=false
       while [ true ]
       do
         ADMINCMD="./gms_admin_nomcast.sh list ${GROUPNAME} ${INSTANCE_EFFECTED} -t ${TRANSPORT} -tl ${ADMINCLI_LOG_LEVEL} -sl ${ADMINCLI_SHOALGMS_LOG_LEVEL} -ma ${MULTICASTADDRESS} -mp ${MULTICASTPORT} ${BIA}"
         if [ $DIST = false ]; then
             TMP=`${ADMINCMD}`
         else
             TMP=`${EXECUTE_REMOTE_CONNECT} ${MASTER_MACHINE_NAME} "cd ${MASTER_WORKSPACE_HOME}; ${ADMINCMD}" `
         fi
         #echo $TMP
         _TMP=`echo ${TMP} | grep "WAS SUCCESSFUL"`
         if [ ! -z "${_TMP}" ];then
            CMD_OK=true
            break;
         fi
         count=`expr ${count} + 1`
         if [ ${count} -gt 10 ]; then
            break
         fi
         sleep 1
       done
       if [ ${CMD_OK} = true ]; then
            echo "Instance ${INSTANCE_EFFECTED} has restarted"
       else
            echo "ERROR: Instance ${INSTANCE_EFFECTED} DID NOT restarted"
       fi
elif [ "${CMD}" = "rejoin" ]; then
       echo "Rejoining ${INSTANCE_EFFECTED}"
       ADMINCMD="./gms_admin_nomcast.sh killm ${GROUPNAME} ${INSTANCE_EFFECTED} -t ${TRANSPORT} -tl ${ADMINCLI_LOG_LEVEL} -sl ${ADMINCLI_SHOALGMS_LOG_LEVEL} -ma ${MULTICASTADDRESS} -mp ${MULTICASTPORT} ${BIA}"
       if [ $DIST = false ]; then
           ${ADMINCMD}
       else
           ${EXECUTE_REMOTE_CONNECT} ${MASTER_MACHINE_NAME} "cd ${MASTER_WORKSPACE_HOME}; ${ADMINCMD}"
       fi
       echo "Restarting ${INSTANCE_EFFECTED}"
       if [ $DIST = false ]; then
           ${EFFECTED_MEMBERSTARTCMD}
       else
           ${EXECUTE_REMOTE_CONNECT} ${EFFECTED_MEMBER_MACHINE_NAME} "cd ${EFFECTED_MEMBER_WORKSPACE_HOME}; ${EFFECTED_MEMBERSTARTCMD}"
       fi
       count=1
       CMD_OK=false
       while [ true ]
       do
         ADMINCMD="./gms_admin_nomcast.sh list ${GROUPNAME} ${INSTANCE_EFFECTED} -t ${TRANSPORT} -tl ${ADMINCLI_LOG_LEVEL} -sl ${ADMINCLI_SHOALGMS_LOG_LEVEL} -ma ${MULTICASTADDRESS} -mp ${MULTICASTPORT} ${BIA}"
         if [ $DIST = false ]; then
             TMP=`${ADMINCMD}`
         else
             TMP=`${EXECUTE_REMOTE_CONNECT} ${MASTER_MACHINE_NAME} "cd ${MASTER_WORKSPACE_HOME}; ${ADMINCMD}" `
         fi
         _TMP=`echo ${TMP} | grep "WAS SUCCESSFUL"`
         if [ ! -z "${_TMP}" ];then
            CMD_OK=true
            break;
         fi
         count=`expr ${count} + 1`
         if [ ${count} -gt 10 ]; then
            break
         fi
         sleep 1
       done
       if [ ${CMD_OK} = true ]; then
            echo "Instance ${INSTANCE_EFFECTED} has restarted"
       else
            echo "ERROR: Instance ${INSTANCE_EFFECTED} DID NOT restarted"
       fi
       # do a quick little sleep just to make sure everything gets started
       # since everyone might not notice the instance went down and up quickly
       sleep 5
elif [ "${CMD}" = "add" ]; then
       echo "Starting New CORE member ${INSTANCE_EFFECTED} on ${EFFECTED_MEMBER_MACHINE_NAME}"
       if [ $DIST = false ]; then
           ${EFFECTED_MEMBERSTARTCMD}
       else
           ${EXECUTE_REMOTE_CONNECT} ${EFFECTED_MEMBER_MACHINE_NAME} "cd ${EFFECTED_MEMBER_WORKSPACE_HOME}; ${EFFECTED_MEMBERSTARTCMD}"
       fi
       count=1
       CMD_OK=false
       while [ true ]
       do
         ADMINCMD="./gms_admin_nomcast.sh list ${GROUPNAME} ${INSTANCE_EFFECTED} -t ${TRANSPORT} -tl ${ADMINCLI_LOG_LEVEL} -sl ${ADMINCLI_SHOALGMS_LOG_LEVEL} -ma ${MULTICASTADDRESS} -mp ${MULTICASTPORT} ${BIA}"
         if [ $DIST = false ]; then
             TMP=`${ADMINCMD}`
         else
             TMP=`${EXECUTE_REMOTE_CONNECT} ${MASTER_MACHINE_NAME} "cd ${MASTER_WORKSPACE_HOME}; ${ADMINCMD}" `
         fi
         _TMP=`echo ${TMP} | grep "WAS SUCCESSFUL"`
         if [ ! -z "${_TMP}" ];then
            CMD_OK=true
            break;
         fi
         count=`expr ${count} + 1`
         if [ ${count} -gt 10 ]; then
            break
         fi
         sleep 1
       done
       if [ ${CMD_OK} = true ]; then
            echo "Instance ${INSTANCE_EFFECTED} has started"
       else
            echo "ERROR: Instance ${INSTANCE_EFFECTED} DID NOT started"
       fi
fi
#
# setting up the BIA for the admin cli
#
if [ $DIST = false ]; then
    if [ ! -z "${BINDINGINTERFACEADDRESS}" ]; then
         BIA="-bia ${BINDINGINTERFACEADDRESS}"
    else
        BIA=""
    fi
else
    if [ -f ${CLUSTER_CONFIGS}/${GROUPNAME}/server.properties ]; then
       TMP=`egrep "^BIND_INTERFACE_ADDRESS" ${CLUSTER_CONFIGS}/${GROUPNAME}/server.properties`
       if [ ! -z "${TMP}" ]; then
           BIA="-bia `echo $TMP | awk -F= '{print $2}' ` "
       else
           BIA=""
       fi
    else
       BIA=""
    fi
fi

echo `date` "Shutting down group [${GROUPNAME}]"
   # we do not want test or shoal output unless we really needit, there we set both types of logging to the same value
ADMINCMD="./gms_admin_nomcast.sh stopc ${GROUPNAME} -t ${TRANSPORT} -tl ${ADMINCLI_LOG_LEVEL} -sl ${ADMINCLI_SHOALGMS_LOG_LEVEL} -ma ${MULTICASTADDRESS} -mp ${MULTICASTPORT} ${BIA}"
if [ $DIST = false ]; then
    ${ADMINCMD}
else
    ${EXECUTE_REMOTE_CONNECT} ${MASTER_MACHINE_NAME} "cd ${MASTER_WORKSPACE_HOME}; ${ADMINCMD}"
fi



echo "Collecting final netstat results from server"
echo "--------------------------------------------"

if [ $DIST = false ]; then
    netstat.sh -u ${LOGS_DIR}/netstat_server.log
else
    ${EXECUTE_REMOTE_CONNECT} ${MASTER_MACHINE_NAME} "cd ${MASTER_WORKSPACE_HOME};netstat.sh -u  ${LOGS_DIR}/netstat_server.log"
fi
if [ $DIST = false ]; then
    # single machine test
    count=1
    while [ $count -le ${NUMOFMEMBERS} ]
    do
        if [  ${count} -lt 10 ]; then
           INSTANCE_NAME="instance0${count}"
        else
           INSTANCE_NAME=instance${count}
        fi
        echo "Collecting netstat results from ${INSTANCE_NAME}"
        echo "---------------------------------------------------"
        netstat.sh -u  ${LOGS_DIR}/netstat_${INSTANCE_NAME}.log
        count=`expr ${count} + 1`
    done
else
   MEMBERS=`find ${CLUSTER_CONFIGS}/${GROUPNAME} -name "*.properties" | grep -v server.properties  `
   for member in ${MEMBERS}
   do
      TMP=`egrep "^MACHINE_NAME" ${member}`
      MACHINE_NAME=`echo $TMP | awk -F= '{print $2}' `
      TMP=`egrep "^INSTANCE_NAME" ${member}`
      INSTANCE_NAME=`echo $TMP | awk -F= '{print $2}' `
      TMP=`egrep "^WORKSPACE_HOME" ${member}`
      WORKSPACE_HOME=`echo $TMP | awk -F= '{print $2}' `
      echo "Collecting netstat results from ${INSTANCE_NAME}"
      echo "---------------------------------------------------"
      ${EXECUTE_REMOTE_CONNECT} ${MACHINE_NAME} "cd ${WORKSPACE_HOME}; netstat.sh -u ${LOGS_DIR}/netstat_${INSTANCE_NAME}.log" 
   done
fi
