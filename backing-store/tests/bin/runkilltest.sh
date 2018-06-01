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


BACKINGSTOREWORKSPACE=`pwd`/../..
BACKINGSTORETESTS=${BACKINGSTOREWORKSPACE}/tests
GMSWORKSPACE=${BACKINGSTOREWORKSPACE}/../gms

ADMINCLI_LOG_LEVEL=WARNING
ADMINCLI_SHOALGMS_LOG_LEVEL=WARNING

TEST_LOG_LEVEL=INFO
SHOALGMS_LOG_LEVEL=CONFIG

CLUSTER_CONFIGS="${BACKINGSTORETESTS}/configs/clusters"
if [ -f "${BACKINGSTORETESTS}/configs/clusters" ]; then
   echo "ERROR: the configs/clusters directory is missing"
   exit 1
fi
NUMOFOBJECTS=100
MSGSIZE=1024

TRANSPORT="-t grizzly"
NUMOFMEMBERS=10

TCPSTARTPORT=9121
TCPENDPORT=9160
GROUPNAME=killtestgroup

BINDINTERFACEADDRESS=""

INSTANCETOKILL=instance01

MULTICASTADDRESS=229.9.1.`${GMSWORKSPACE}/randomNumber.sh`
MULTICASTPORT=2299

DIST=false

usage () {
 echo "usage:"
 echo "   single machine:"
 echo "      [-h] [-t grizzly|jxta] [-bia address] [-itk instancetokill(default instance01)] [numberOfMembers(10 is default)] "
 echo "   distributed environment:"
 echo "      -d <-g groupname> [-t grizzly|jxta] [-itk instancetokill(default instance01)]"
 echo " "
 echo " Examples:"
 echo "     runkilltest.sh"
 echo "     runkilltest.sh 5 -bia 129.168.1.4 -itk instance02"
 echo "     runkilltest.sh -d -g testgroup"
 echo "     runkilltest.sh -d -g testgroup -itk instance02"
 exit 1
}


while [ $# -ne 0 ]
do
     case ${1} in
       -h)
         usage
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
       -itk)
         shift
         INSTANCETOKILL="-DINSTANCETOKILL=${1}"
         shift
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
         TMP=${1}
         shift
         if [ ! -z "${TMP}" ] ;then
            if [ "${TMP}" != "grizzly" -a "${TMP}" != "jxta" ]; then
               echo "ERROR: Invalid transport specified: ${TMP}"
               usage
            else
               TRANSPORT="-t ${TMP}"
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

echo ${MULTICASTADDRESS} > ./currentMulticastAddress.txt

echo Transport: ${TRANSPORT}

if [ $DIST = true ]; then
    NUMOFMEMBERS=`find ${CLUSTER_CONFIGS}/${GROUPNAME} -name "*.properties" | grep -v server.properties | sort | wc -w`
fi
echo NumberOfMembers: ${NUMOFMEMBERS}

LOGS_DIR=${BACKINGSTORETESTS}/LOGS/killtest

echo "LOGS_DIRS=${LOGS_DIR}"

#--------------------------
# killing of any previous processes
if [ $DIST = false ]; then
     ${GMSWORKSPACE}/killmembers.sh
else
   MEMBERS=`find ${CLUSTER_CONFIGS}/${GROUPNAME} -name "*.properties"  `
   for member in ${MEMBERS}
   do
       TMP=`egrep "^MACHINE_NAME" ${member}`
       MACHINE_NAME=`echo $TMP | awk -F= '{print $2}' `
       TMP=`egrep "^WORKSPACE_HOME" ${member}`
       WORKSPACE_HOME=`echo $TMP | awk -F= '{print $2}' `
       echo "Killing processes on ${MACHINE_NAME}"
       ${EXECUTE_REMOTE_CONNECT} ${MACHINE_NAME} "cd ${WORKSPACE_HOME}; ../../gms/killmembers.sh"
   done
fi

#--------------------------
# STARTING OF THE MASTER
if [ $DIST = false ]; then
    mkdir -p ${LOGS_DIR}
    echo "Removing old logs"
    rm -f ${LOGS_DIR}/*.log
    echo "Starting server"
    if [ ! -z ${BINDINGINTERFACEADDRESS} ]; then
       BIA="-bia ${BINDINGINTERFACEADDRESS}"
    else
       BIA=""
    fi
     ./runkill.sh server ${GROUPNAME} -tl ${TEST_LOG_LEVEL} -sl ${SHOALGMS_LOG_LEVEL} -ts ${TCPSTARTPORT} -te ${TCPENDPORT} -ma ${MULTICASTADDRESS} -mp ${MULTICASTPORT} -itk ${INSTANCETOKILL} -l ${LOGS_DIR} ${BIA}
else
    if [ -f ${CLUSTER_CONFIGS}/${GROUPNAME}/server.properties ]; then
       TMP=`egrep "^MACHINE_NAME" ${CLUSTER_CONFIGS}/${GROUPNAME}/server.properties`
       MACHINE_NAME=`echo $TMP | awk -F= '{print $2}' `
       TMP=`egrep "^WORKSPACE_HOME" ${CLUSTER_CONFIGS}/${GROUPNAME}/server.properties`
       WORKSPACE_HOME=`echo $TMP | awk -F= '{print $2}' `
       TMP=`egrep "^TCPSTARTPORT" ${CLUSTER_CONFIGS}/${GROUPNAME}/server.properties`
       if [ ! -z ${TMP} ]; then
           TSA="-ts `echo $TMP | awk -F= '{print $2}' ` "
       else
           TSA=""
       fi
       TMP=`egrep "^TCPENDPORT" ${CLUSTER_CONFIGS}/${GROUPNAME}/server.properties`
       if [ ! -z ${TMP} ]; then
           TEA="-te `echo $TMP | awk -F= '{print $2}' ` "
       else
           TEA=""
       fi
       TMP=`egrep "^BIND_INTERFACE_ADDRESS" ${CLUSTER_CONFIGS}/${GROUPNAME}/server.properties`
       if [ ! -z ${TMP} ]; then
           BIA="-bia `echo $TMP | awk -F= '{print $2}' ` "
       else
           BIA=""
       fi
       echo "Starting server on ${MACHINE_NAME}"
       ${EXECUTE_REMOTE_CONNECT} ${MACHINE_NAME} "cd ${WORKSPACE_HOME};../../gms/killmembers.sh; rm -rf ${LOGS_DIR}/server.log; mkdir -p ${LOGS_DIR}; cd ${WORKSPACE_HOME}/bin; ./runkill.sh server ${GROUPNAME} -tl ${TEST_LOG_LEVEL} -sl ${SHOALGMS_LOG_LEVEL} -ts ${TCPSTARTPORT} -te ${TCPENDPORT} -ma ${MULTICASTADDRESS} -mp ${MULTICASTPORT} -itk ${INSTANCETOKILL} -l ${LOGS_DIR} ${BIA}"

   else
       echo "ERROR: Could not find ${CLUSTER_CONFIGS}/${GROUPNAME}/server.properties"
       exit 1
    fi
fi

#--------------------------
# Give time for the master to startup before starting the other members
sleep 5

SDTCP=`expr ${TCPENDPORT} + 1`
EDTCP=`expr ${SDTCP} + 30`
if [ $DIST = false ]; then
    # single machine startup
    echo "Starting ${NUMOFMEMBERS} CORE members"
    count=1
    while [ $count -le ${NUMOFMEMBERS} ]
    do
        if [  ${count} -lt 10 ]; then
           INSTANCE_NAME="instance0${count}"
        else
           INSTANCE_NAME=instance${count}
        fi
        echo "Starting ${INSTANCE_NAME}"
        if [ ! -z ${BINDINGINTERFACEADDRESS} ]; then
          BIA="-bia ${BINDINGINTERFACEADDRESS}"
        else
          BIA=""
        fi
        MEMBERSTARTCMD="./runkill.sh ${INSTANCE_NAME} ${GROUPNAME}  ${NUMOFOBJECTS} ${MSGSIZE} ${TRANSPORT} -tl ${TEST_LOG_LEVEL} -sl ${SHOALGMS_LOG_LEVEL} -ts ${SDTCP} -te ${EDTCP} -ma ${MULTICASTADDRESS} -mp ${MULTICASTPORT} -itk ${INSTANCETOKILL} -l ${LOGS_DIR} ${BIA}"
        ${MEMBERSTARTCMD}

        SDTCP=`expr ${EDTCP} + 1`
        EDTCP=`expr ${SDTCP} + 30`
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
          if [ ! -z ${TMP} ]; then
               SDTCP=`echo $TMP | awk -F= '{print $2}' `
          fi
          TMP=`egrep "^TCPENDPORT" ${member}`
          if [ ! -z ${TMP} ]; then
               EDTCP=`echo $TMP | awk -F= '{print $2}' `
          fi
          TMP=`egrep "^BIND_INTERFACE_ADDRESS" ${member}`
          if [ ! -z ${TMP} ]; then
               BIA="-bia `echo $TMP | awk -F= '{print $2}' ` "
          fi
          echo "Starting ${INSTANCE_NAME} on ${MACHINE_NAME}"
          MEMBERSTARTCMD="./runkill.sh ${INSTANCE_NAME} ${GROUPNAME}  ${NUMOFOBJECTS} ${MSGSIZE} ${TRANSPORT} -tl ${TEST_LOG_LEVEL} -sl ${SHOALGMS_LOG_LEVEL} -ts ${SDTCP} -te ${EDTCP} -ma ${MULTICASTADDRESS} -mp ${MULTICASTPORT} -itk ${INSTANCETOKILL} -l ${LOGS_DIR} ${BIA}"
          ${EXECUTE_REMOTE_CONNECT} ${MACHINE_NAME} "cd ${WORKSPACE_HOME};../../gms/killmembers.sh; rm -rf ${LOGS_DIR}/$INSTANCE_NAME.log; mkdir -p ${LOGS_DIR}; cd bin; ${MEMBERSTARTCMD}"

   done
   TMP=`egrep "^MACHINE_NAME" ${CLUSTER_CONFIGS}/${GROUPNAME}/server.properties`
   MASTER_MACHINE_NAME=`echo $TMP | awk -F= '{print $2}' `
   TMP=`egrep "^WORKSPACE_HOME" ${CLUSTER_CONFIGS}/${GROUPNAME}/server.properties`
   MASTER_WORKSPACE_HOME=`echo $TMP | awk -F= '{print $2}' `
fi

#
# setting up the BIA for the admin cli
#
if [ $DIST = false ]; then
    if [ ! -z ${BINDINGINTERFACEADDRESS} ]; then
         BIA="-bia ${BINDINGINTERFACEADDRESS}"
    else
        BIA=""
    fi
else
    if [ -f ${CLUSTER_CONFIGS}/${GROUPNAME}/server.properties ]; then
       TMP=`egrep "^BIND_INTERFACE_ADDRESS" ${CLUSTER_CONFIGS}/${GROUPNAME}/server.properties`
       if [ ! -z ${TMP} ]; then
           BIA="-bia `echo $TMP | awk -F= '{print $2}' ` "
       else
           BIA=""
       fi
    else
       BIA=""
    fi
fi

echo "Waiting for group [${GROUPNAME}] to complete startup"
# we do not want test or shoal output unless we really needit, there we set both types of logging to the same value
ADMINCMD="gms_admin.sh waits ${GROUPNAME} ${TRANSPORT} -tl ${ADMINCLI_LOG_LEVEL} -sl ${ADMINCLI_SHOALGMS_LOG_LEVEL} -ma ${MULTICASTADDRESS} -mp ${MULTICASTPORT} ${BIA} -swh ${GMSWORKSPACE}"
if [ $DIST = false ]; then
    ${GMSWORKSPACE}/${ADMINCMD}
else
    ${EXECUTE_REMOTE_CONNECT} ${MASTER_MACHINE_NAME} "cd ${MASTER_WORKSPACE_HOME}/../../gms; ${ADMINCMD}"
fi

echo "Group startup has completed, execute testing and wait for it to complete"
ADMINCMD="gms_admin.sh test ${GROUPNAME} ${INSTANCETOKILL} ${TRANSPORT} -tl ${ADMINCLI_LOG_LEVEL} -sl ${ADMINCLI_SHOALGMS_LOG_LEVEL} -ma ${MULTICASTADDRESS} -mp ${MULTICASTPORT} ${BIA} -swh ${GMSWORKSPACE}"
if [ $DIST = false ]; then
     ${GMSWORKSPACE}/${ADMINCMD}
else
     ${EXECUTE_REMOTE_CONNECT} ${MASTER_MACHINE_NAME} "cd ${MASTER_WORKSPACE_HOME}/../../gms; ${ADMINCMD}"
fi
echo "Testing has completed, shutting down group [${GROUPNAME}]"
   # we do not want test or shoal output unless we really needit, there we set both types of logging to the same value
ADMINCMD="gms_admin.sh stopc ${GROUPNAME} ${TRANSPORT} -tl ${ADMINCLI_LOG_LEVEL} -sl ${ADMINCLI_SHOALGMS_LOG_LEVEL} -ma ${MULTICASTADDRESS} -mp ${MULTICASTPORT} ${BIA} -swh ${GMSWORKSPACE}"

echo "Checking logs for failures"
CMD="grep -a \"SEVERE\" ${LOGS_DIR}/* | grep -v \"Connection refused\" "
if [ $DIST = false ]; then
    ${GMSWORKSPACE}/${ADMINCMD}
    CMD="grep -a \"SEVERE\" ${LOGS_DIR}/* | grep -v \"Connection refused\" "
    NUMFAILED=`eval ${CMD} | wc -l | tr -d ' ' `
    echo "Number of FAILURES:${NUMFAILED}"
    echo "Failures:"
    eval ${CMD}
    if [ ${NUMFAILED} -gt 10 ]; then
       # repeat the number of failures if there is a lot of them
       echo "Number of FAILURES:${NUMFAILED}"
    fi
else
    ${EXECUTE_REMOTE_CONNECT} ${MASTER_MACHINE_NAME} "cd ${MASTER_WORKSPACE_HOME}/../../gms; ${ADMINCMD}"
    echo "You must collect all the logs and execute the following on them:"
    echo "${CMD}"

fi



