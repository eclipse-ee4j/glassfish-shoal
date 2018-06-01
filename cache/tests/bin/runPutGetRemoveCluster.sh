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

NUMOFOBJECTS=100
MSGSIZE=1024
GROUPNAME="putgetremovetestgroup"
TEST_LOG_LEVEL=INFO
SHOALGMS_LOG_LEVEL=INFO




CACHE_WORKSPACE_HOME=`pwd`/../..
CACHE_TESTS_DIR=`pwd`/..
LOGS_DIR=$CACHE_TESTS_DIR/LOGS/putgetremove
SHOAL_WORKSPACE_HOME=${CACHE_WORKSPACE_HOME}/../gms


usage () {
 echo "usage: [-h] [numberOfMembers(10 is default)]"
exit 1
}

if [ "$1" == "-h" ]; then
    usage
fi

NUMOFMEMBERS=10
if [ ! -z "${1}" ]; then
    NUMOFMEMBERS=`echo "${1}" | egrep "^[0-9]+$" `
    if [ "${NUMOFMEMBERS}" != "" ]; then
       if [ ${NUMOFMEMBERS} -le 0 ];then
          echo "ERROR: Invalid number of members specified"
          usage
       fi
    else
       echo "ERROR: Invalid number of members specified"
       usage
    fi
    shift
fi



MULTICASTPORT=2299
MULTICASTADDRESS=229.9.1.`${SHOAL_WORKSPACE_HOME}/randomNumber.sh`
echo ${MULTICASTADDRESS} > ./currentMulticastAddress.txt

mkdir -p ${LOGS_DIR}
echo "Removing old logs"
rm -f ${LOGS_DIR}/server.log ${LOGS_DIR}/instance??.log ./currentMulticastAddress.txt


echo "Starting admin... "
./runPutGetRemove.sh server ${GROUPNAME} -tl ${TEST_LOG_LEVEL} -sl ${SHOALGMS_LOG_LEVEL} -ts 9130 -te 9160 -ma ${MULTICASTADDRESS} -mp ${MULTICASTPORT} > ${LOGS_DIR}/server.log 2>&1 &
sleep 5

echo "Starting ${NUMOFMEMBERS} members"

instanceNum=1
sdtcp=9161
edtcp=`expr ${sdtcp} + 30`
count=1
while [ $count -le ${NUMOFMEMBERS} ]
do
   if [  ${instanceNum} -lt 10 ]; then
       iNum="0${instanceNum}"
    else
       iNum=${instanceNum}
    fi
    ./runPutGetRemove.sh instance${iNum} ${GROUPNAME}  ${NUMOFOBJECTS} ${MSGSIZE} -tl ${TEST_LOG_LEVEL} -sl ${SHOALGMS_LOG_LEVEL} -ts ${sdtcp} -te ${edtcp} -ma ${MULTICASTADDRESS} -mp ${MULTICASTPORT} > ${LOGS_DIR}/instance${iNum}.log 2>&1 &
    instanceNum=`expr ${instanceNum} + 1`
    sdtcp=`expr ${edtcp} + 1`
    edtcp=`expr ${sdtcp} + 30`
    count=`expr ${count} + 1`
done


PWD=`pwd`
cd ${SHOAL_WORKSPACE_HOME}
echo "Waiting for group [${GROUPNAME}] to complete startup"
./gms_admin.sh waits ${GROUPNAME} -tl ${TEST_LOG_LEVEL} -sl ${SHOALGMS_LOG_LEVEL} -ma ${MULTICASTADDRESS} -mp ${MULTICASTPORT}

echo "Group startup has completed, execute testing and wait for it to complete"
./gms_admin.sh test ${GROUPNAME} -tl ${TEST_LOG_LEVEL} -sl ${SHOALGMS_LOG_LEVEL} -ma ${MULTICASTADDRESS} -mp ${MULTICASTPORT}

echo "Group testing has completed, shutting down group [${GROUPNAME}]"
./gms_admin.sh stopc ${GROUPNAME} -tl ${TEST_LOG_LEVEL} -sl ${SHOALGMS_LOG_LEVEL} -ma ${MULTICASTADDRESS} -mp ${MULTICASTPORT}
cd ${PWD}

FAILURES=`grep -a "SEVERE" ${LOGS_DIR}/* `

NUMFAILED=`grep -a "SEVERE" ${LOGS_DIR}/* | wc -l | tr -d ' ' `
echo "FAILURES:${NUMFAILED}"
echo "========================="
echo "${FAILURES}"
echo "========================="
