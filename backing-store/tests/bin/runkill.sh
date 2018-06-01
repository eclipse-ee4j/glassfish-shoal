#!/bin/sh -x
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

TEST_LOG_LEVEL=WARNING
SHOALGMS_LOG_LEVEL=WARNING
TRANSPORT=grizzly

MAINCLASS="com.sun.enterprise.ee.tests.BackingStore.KillTest"
SHOAL_GROUP_COMMUNICATION_PROVIDER="GMS_GROUP_COMMUNICATION_PROVIDER=grizzly"

BACKINGSTOREWORKSPACE=`pwd`/../..
#LOGS_DIR=$BACKINGSTOREWORKSPACE/LOGS/kill

# Set up all the directory paths to the jars
GMSWORKSPACE=${BACKINGSTOREWORKSPACE}/../gms
GMS_LIB=$GMSWORKSPACE/lib
GMS_DIST=$GMSWORKSPACE/dist
GMS_API_TARGET=$GMSWORKSPACE/api/target
GMS_IMPL_TARGET=$GMSWORKSPACE/impl/target
CACHEWORKSPACE=${BACKINGSTOREWORKSPACE}/../cache
CACHE_TARGET=$CACHEWORKSPACE/target
BACKINGSTORE_TESTS_DIST=$BACKINGSTOREWORKSPACE/tests/dist
BACKINGSTORE_TARGET=$BACKINGSTOREWORKSPACE/target
BACKINGSTORE_TESTS_LIB=$BACKINGSTOREWORKSPACE/tests/lib


# Get all the jars required to run the program
GMS_JARS=`find ${GMS_LIB} -name "*.jar" `
GMS_JARS=${GMS_JARS}:`find ${GMS_DIST} -name "*.jar" `
GMS_JARS=${GMS_JARS}:`find ${GMS_API_TARGET} -name "*.jar" `
GMS_JARS=${GMS_JARS}:`find ${GMS_IMPL_TARGET} -name "*.jar"  `
GMS_JARS=${GMS_JARS}:${GMS_DIST}/shoal-gms-tests.jar
GRIZZLY_JARS=${GMS_LIB}/grizzly-framework.jar:${GMS_LIB}/grizzly-utils.jar
JXTA_JARS=${GMS_LIB}/jxta.jar
CACHE_JARS=`find ${CACHE_TARGET} -name "*.jar" `
BACKINGSTORE_JARS=`find ${BACKINGSTORE_TARGET} -name "*.jar" `
BACKINGSTORE_JARS=${BACKINGSTORE_JARS}:`find ${BACKINGSTORE_TESTS_DIST} -name "*.jar" `
HAAPI_JARS=${BACKINGSTORE_TESTS_LIB}/ha-api.jar
JARS=${GMS_JARS}:${GRIZZLY_JARS}:${CACHE_JARS}:${BACKINGSTORE_JARS}:${HAAPI_JARS}
JARS=`echo $JARS | sed -e 's/  / /g' | sed -e 's/ /:/g'`


usage () {
    java -cp ${JARS} $MAINCLASS -h
    exit 0
}

BINDINGINTERFACEADDRESS=""
TCPSTARTPORT=""
TCPENDPORT=""
MULTICASTADDRESS="-DMULTICASTADDRESS=229.9.1.2"
MULTICASTPORT="-DMULTICASTPORT=2299"
INSTANCETOKILL=""
DONEREQUIRED=false
while [ $# -ne 0 ]
do
     case ${1} in
       -h)
       usage
       exit 1
       ;;
       -bia)
       shift
       BINDINGINTERFACEADDRESS="${1}"
       shift
       ;;
       -t)
       shift
       TRANSPORT="${1}"
       shift
       ;;
       -l)
       shift
       LOGS_DIR="${1}"
       shift
       ;;
       -itk)
       shift
       INSTANCETOKILL="-DINSTANCETOKILL=${1}"
       shift
       ;;
       -ts)
       shift
       TCPSTARTPORT="-DTCPSTARTPORT=${1}"
       shift
       ;;
       -te)
       shift
       TCPENDPORT="-DTCPENDPORT=${1}"
       shift
       ;;
       -ma)
       shift
       MULTICASTADDRESS="-DMULTICASTADDRESS=${1}"
       shift
       ;;
       -mp)
       shift
       MULTICASTPORT="-DMULTICASTPORT=${1}"
       shift
       ;;
       -tl)
       shift
       TEST_LOG_LEVEL="${1}"
       shift
       ;;
       -sl)
       shift
       SHOALGMS_LOG_LEVEL="${1}"
       shift
       ;;
       *)
       if [ ${DONEREQUIRED} = false ]; then
           INSTANCEID=$1
           shift
           CLUSTERNAME=$1
           shift
           if [ "${INSTANCEID}" != "server" ];
           then
             NUMOBJECTS="-DNUMOBJECTS=$1"
             shift
             PAYLOADSIZE="-DPAYLOADSIZE=$1"
             shift
           fi
           DONEREQUIRED=true
       else
          echo "ERRROR: ignoring invalid argument $1"
          shift
       fi
       ;;
     esac
done

if [ -z ${INSTANCEID} -o -z ${CLUSTERNAME} ]; then
    echo "ERROR: Missing a required argument"
    usage;
fi
if [ "${INSTANCEID}" != "server" ];
then
    if [ -z ${NUMOBJECTS} ]; then
        echo "ERROR: Missing a required argument"
        usage;
    fi
fi
if [ "${TRANSPORT}" != "grizzly" -a "${TRANSPORT}" != "jxta" ]; then
    echo "ERROR: Invalid transport specified"
    usage;
fi

if [ $TRANSPORT != "grizzly" ]; then
    JARS=${JXTA_JARS}:${JARS}
fi
if [ ! -z ${BINDINGINTERFACEADDRESS} ]; then
    BINDINGINTERFACEADDRESS="-DBIND_INTERFACE_ADDRESS=${BINDINGINTERFACEADDRESS}"
fi
echo Running using Shoal with transport ${TRANSPORT}
echo "=========================="
#echo java -Dcom.sun.management.jmxremote  -DINSTANCEID=${INSTANCEID} -DCLUSTERNAME=${CLUSTERNAME} -DNUMINSTANCES=${NUMINSTANCES} ${NUMOBJECTS} ${PAYLOADSIZE} -DTEST_LOG_LEVEL=${TEST_LOG_LEVEL} -DLOG_LEVEL=${SHOALGMS_LOG_LEVEL}   -cp ${JARS} ${TCPSTARTPORT} ${TCPENDPORT} -DGMS_GROUP_COMMUNICATION_PROVIDER=${TRANSPORT} ${MULTICASTADDRESS} ${MULTICASTPORT} ${MAINCLASS}
CMD="java -Dcom.sun.management.jmxremote  -DINSTANCEID=${INSTANCEID} -DCLUSTERNAME=${CLUSTERNAME} ${NUMOBJECTS} ${PAYLOADSIZE} -DTEST_LOG_LEVEL=${TEST_LOG_LEVEL} -DLOG_LEVEL=${SHOALGMS_LOG_LEVEL} -cp ${JARS} ${TCPSTARTPORT} ${TCPENDPORT} -DSHOAL_GROUP_COMMUNICATION_PROVIDER=${TRANSPORT} ${MULTICASTADDRESS} ${MULTICASTPORT} ${INSTANCETOKILL} ${BINDINGINTERFACEADDRESS} ${MAINCLASS}"


if [ -z "${LOGS_DIR}" ]; then
   echo "Running using Shoal with transport ${TRANSPORT}"
   echo "=========================="
   echo ${CMD}
   echo "=========================="
   ${CMD} &
else
   if [ ! -d ${LOGS_DIR} ];then
      mkdir -p ${LOGS_DIR}
   fi
   #echo "LOGS_DIR=${LOGS_DIR}"
   echo "Running using Shoal with transport ${TRANSPORT}" >> ${LOGS_DIR}/${INSTANCEID}.log
   echo "==========================" >> ${LOGS_DIR}/${INSTANCEID}.log
   echo ${CMD} >> ${LOGS_DIR}/${INSTANCEID}.log
   echo "==========================" >> ${LOGS_DIR}/${INSTANCEID}.log
   eval ${CMD}  >> ${LOGS_DIR}/${INSTANCEID}.log 2>&1 &
fi
