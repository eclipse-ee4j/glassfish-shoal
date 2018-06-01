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

TEST_LOG_LEVEL=WARNING
SHOALGMS_LOG_LEVEL=WARNING
TRANSPORT=grizzly

MAINCLASS="com.sun.enterprise.ee.tests.DataStore.PutGetRemoveTest"
CACHEWORKSPACE=../..
LOGS_DIR=$CACHEWORKSPACE/LOGS/putgetremove
SHOALWORKSPACE=${CACHEWORKSPACE}/../gms
SHOAL_LIB=$SHOALWORKSPACE/lib
SHOAL_DIST=$SHOALWORKSPACE/dist

CACHE_TESTS_DIST=$CACHEWORKSPACE/tests/dist
CACHE_DIST=$CACHEWORKSPACE/target
SHOAL_GROUP_COMMUNICATION_PROVIDER="SHOAL_GROUP_COMMUNICATION_PROVIDER=grizzly"


usage () {
    java -cp ${JARS} $MAINCLASS -h
    exit 0
}


GRIZZLY_JARS=${SHOAL_DIST}/shoal-gms-tests.jar:${SHOAL_DIST}/shoal-gms.jar:${SHOAL_LIB}/grizzly-framework.jar:${SHOAL_LIB}/grizzly-utils.jar
CACHE_JARS=${CACHE_DIST}/shoal-cache.jar:${CACHE_TESTS_DIST}/cache-tests.jar
JARS=${GRIZZLY_JARS}:${CACHE_JARS}

TCPSTARTPORT=""
TCPENDPORT=""
MULTICASTADDRESS="-DMULTICASTADDRESS=229.9.1.2"
MULTICASTPORT="-DMULTICASTPORT=2299"

DONEREQUIRED=false
while [ $# -ne 0 ]
do
     case ${1} in
       -h)
       usage
       exit 1
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


echo Running using Shoal with transport ${TRANSPORT}
echo "=========================="
#echo java -Dcom.sun.management.jmxremote  -DINSTANCEID=${INSTANCEID} -DCLUSTERNAME=${CLUSTERNAME} -DNUMINSTANCES=${NUMINSTANCES} ${NUMOBJECTS} ${PAYLOADSIZE} -DTEST_LOG_LEVEL=${TEST_LOG_LEVEL} -DLOG_LEVEL=${SHOALGMS_LOG_LEVEL}   -cp ${JARS} ${TCPSTARTPORT} ${TCPENDPORT} -DSHOAL_GROUP_COMMUNICATION_PROVIDER=${TRANSPORT} ${MULTICASTADDRESS} ${MULTICASTPORT} ${MAINCLASS};
java -Dcom.sun.management.jmxremote  -DINSTANCEID=${INSTANCEID} -DCLUSTERNAME=${CLUSTERNAME} ${NUMOBJECTS} ${PAYLOADSIZE} -DTEST_LOG_LEVEL=${TEST_LOG_LEVEL} -DLOG_LEVEL=${SHOALGMS_LOG_LEVEL} -cp ${JARS} ${TCPSTARTPORT} ${TCPENDPORT} -DSHOAL_GROUP_COMMUNICATION_PROVIDER=${TRANSPORT} ${MULTICASTADDRESS} ${MULTICASTPORT} ${MAINCLASS};


