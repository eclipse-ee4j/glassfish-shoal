#!/bin/sh +x
#
# Copyright (c) 2004, 2018 Oracle and/or its affiliates. All rights reserved.
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

PUBLISH_HOME=./dist
LIB_HOME=./lib

usage () {
    cat << USAGE 
Usage: $0 <parameters...> 
The required parameters are :
 <instance_id_token> <groupname> <membertype{CORE|SPECTATOR}> <Life In Milliseconds> <log level> <transport>{grizzly,jxta} <-l logdir> <-ts tcpstartport> <-tp tcpendport> <-ma multicastaddress> <-mp multicastport> <-bia bindinginterfaceaddress>

Life in milliseconds should be either 0 or at least 60000 to demo failure fencing.

<-l fullpathtologdir> <-ts tcpstartport>, <-te tcpendport>, <-ma multicastaddress>, <-mp multicastport> <-bia bindinginterfaceaddress> are optional parameters.
Grizzly and jxta transports have different defaults.
USAGE
   exit 0
}

MAINCLASS=com.sun.enterprise.ee.cms.tests.ApplicationServer

GRIZZLY_JARS=${PUBLISH_HOME}/shoal-gms-tests.jar:${PUBLISH_HOME}/shoal-gms.jar:${LIB_HOME}/grizzly2-framework.jar:${LIB_HOME}/grizzly-utils.jar
JXTA_JARS=${PUBLISH_HOME}/shoal-gms-tests.jar:${PUBLISH_HOME}/shoal-gms.jar:${LIB_HOME}/grizzly2-framework.jar:${LIB_HOME}/grizzly-utils.jar:${LIB_HOME}/jxta.jar
DEBUGARGS="-Xdebug -Xnoagent -Djava.compiler=NONE -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005 -DjxtaMulticastPoolsize=25"
NONDEBUGARGS="-Dcom.sun.management.jmxremote"

DEBUG=false
TCPSTARTPORT=""
TCPENDPORT=""
MULTICASTADDRESS="-DMULTICASTADDRESS=229.9.1.2"
MULTICASTPORT="-DMULTICASTPORT=2299"

TRANSPORT=grizzly
BINDINGINTERFACEADDRESS=""
MAXMISSEDHEARTBEATS=""
TEST_LOG_LEVEL=WARNING
LOG_LEVEL=CONFIG

#bump next log level to FINE for deeper analysis
MONITOR_LOG_LEVEL=INFO

JARS=${GRIZZLY_JARS}
DONEREQUIRED=false
while [ $# -ne 0 ]
do
     case ${1} in
       -h)
       usage
       exit 1
       ;;
       -debug)
       shift
       DEBUG=true
       ;;
       -bia)
       shift
       BINDINGINTERFACEADDRESS="${1}"
       shift
       ;;
       -mmh)
       shift
       MAXMISSEDHEARTBEATS="${1}"
       shift
       ;;
       -l)
       shift
       LOGS_DIR="${1}"
       shift
       ;;
       -tl)
       shift
       TEST_LOG_LEVEL="${1}"
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
       *)
       if [ ${DONEREQUIRED} = false ]; then
           INSTANCEID=$1
           shift
           CLUSTERNAME=$1
           shift
           MEMBERTYPE=$1
           shift
           LIFEINMILLIS=$1
           shift
           LOGLEVEL=$1
           shift
           TRANSPORT=$1
           shift
           DONEREQUIRED=true
       else
          echo "ERRROR: ignoring invalid argument $1"
          shift
       fi
       ;;
     esac
done

if [ -z "${INSTANCEID}" -o -z "${CLUSTERNAME}" -o -z "${MEMBERTYPE}" -o -z "${LIFEINMILLIS}" -o -z "${LOGLEVEL}" -o -z "${TRANSPORT}" ]; then
    echo "ERROR: Missing a required argument"
    usage;
fi

if [ "${MEMBERTYPE}" != "CORE" -a "${MEMBERTYPE}" != "SPECTATOR" -a "${MEMBERTYPE}" != "WATCHDOG" ]; then
    echo "ERROR: Invalid membertype specified"
    usage;
fi
if [ "${TRANSPORT}" != "grizzly" -a "${TRANSPORT}" != "jxta" ]; then
    echo "ERROR: Invalid transport specified"
    usage;
fi

if [ $TRANSPORT != "grizzly" ]; then
    JARS=${JXTA_JARS}
fi

if [ ${DEBUG} = false ]; then
    OTHERARGS=${NONDEBUGARGS}
else
    OTHERARGS=${DEBUGARGS}
fi

if [ ! -z "${BINDINGINTERFACEADDRESS}" ]; then
    BINDINGINTERFACEADDRESS="-DBIND_INTERFACE_ADDRESS=${BINDINGINTERFACEADDRESS}"
fi
if [ ! -z "${MAXMISSEDHEARTBEATS}" ]; then
    MAXMISSEDHEARTBEATS="-DMAX_MISSED_HEARTBEATS=${MAXMISSEDHEARTBEATS}"
fi

#  If you run shoal over grizzly on JDK7, NIO.2 multicast channel used. Otherwise, blocking multicast server used
CMD="java ${OTHERARGS} -DMEMBERTYPE=${MEMBERTYPE} -DINSTANCEID=${INSTANCEID} -DCLUSTERNAME=${CLUSTERNAME} -DMESSAGING_MODE=true -DLIFEINMILLIS=${LIFEINMILLIS} -DLOG_LEVEL=${LOGLEVEL} -DTEST_LOG_LEVEL=${TEST_LOG_LEVEL} -DMONITOR_LOG_LEVEL=${MONITOR_LOG_LEVEL} -cp ${JARS} ${TCPSTARTPORT} ${TCPENDPORT} -DSHOAL_GROUP_COMMUNICATION_PROVIDER=${TRANSPORT} ${MULTICASTADDRESS} ${MULTICASTPORT} ${BINDINGINTERFACEADDRESS} ${MAXMISSEDHEARTBEATS} -Djava.util.logging.config.file=gmsdemo_logging.properties ${MAINCLASS}"

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
   ${CMD}  >> ${LOGS_DIR}/${INSTANCEID}.log 2>&1 &
fi

