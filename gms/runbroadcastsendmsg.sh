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

#-------------------------
#
# REVIEW THESE ENTRIES
#
#
# This needs to ne a complete path not a relative path to the shoal workspace
SHOALWORKSPACE=`pwd`

#PROVIDER="jxta"
PROVIDER="grizzly"

SENDTYPE="SYNCBROADCAST"
#SENDTYPE="UDP"

NUMOFINSTANCES="10"
NUMOFMSGS=1000
MSGSIZE=4000
STARTINSTANCENUM=101
STARTTCPPORT=9130
LOGLEVEL="INFO"

#
# if members are distributed across machines, the GROUPNAME
# must be set to a unique value so that all members join the
# same group
#
GROUPNAME="TestGroup_`uname -n`"
if [ "${GROUPNAME}" == "" ]; then
    GROUPNAME="TestGroup"
fi

#
#
#
#-------------------------
TMPDIR=$SHOALWORKSPACE/tmp
if [ ! -d ${TMPDIR} ]; then
    mkdir ${TMPDIR}
fi

LOGDIR=$SHOALWORKSPACE/LOGS/broadcastsendmsg
if [ ! -d ${LOGDIR} ] ; then
    mkdir ${LOGDIR}
else
    rm -rf ${LOGDIR}/instance*.log ${LOGDIR}/server.log
fi


#########################################
# Create the scripts used to run the test
#########################################

#===============================================
# Create the script that actually runs the test
#===============================================
rm -rf ${TMPDIR}/script1
cat << ENDSCRIPT > ${TMPDIR}/script1
#!/bin/sh +x

ECHO=\`which echo\`

publish_home=$SHOALWORKSPACE/dist
lib_home=$SHOALWORKSPACE/lib
\$ECHO "Arg1=\${1}"
\$ECHO "Arg2=\${2}"
\$ECHO "Arg3=\${3}"
\$ECHO "Arg4=\${4}"
\$ECHO "Arg5=\${5}"
\$ECHO "Arg6=\${6}"
\$ECHO "Arg7=\${7}"
\$ECHO "Arg8=\${8}"
\$ECHO "Arg9=\${9}"


if [ "\${1}" == "master" ] ; then
     java -Dcom.sun.management.jmxremote -DSHOAL_GROUP_COMMUNICATION_PROVIDER=${PROVIDER} -DLOG_LEVEL=\$6 -cp \${publish_home}/shoal-gms-tests.jar:\${publish_home}/shoal-gms.jar:\${lib_home}/bcprov-jdk14.jar:\${lib_home}/grizzly2-framework.jar:\${lib_home}/grizzly-framework.jar:\${lib_home}/grizzly-utils.jar -DTCPSTARTPORT=\$4 -DTCPENDPORT=\$5 com.sun.enterprise.shoal.messagesenderreceivertest.BroadcastSendMsg \$1 \$2 \$3
else
     java -Dcom.sun.management.jmxremote -DSHOAL_GROUP_COMMUNICATION_PROVIDER=${PROVIDER} -DLOG_LEVEL=\$9 -cp \${publish_home}/shoal-gms-tests.jar:\${publish_home}/shoal-gms.jar:\${lib_home}/bcprov-jdk14.jar:\${lib_home}/grizzly2-framework.jar:\${lib_home}/grizzly-framework.jar:\${lib_home}/grizzly-utils.jar -DTCPSTARTPORT=\$7 -DTCPENDPORT=\$8 com.sun.enterprise.shoal.messagesenderreceivertest.BroadcastSendMsg \$1 \$2 \$3 \$4 \$5 \$6
fi


ENDSCRIPT
#===============================================

#=====================================================================
# Create the script monitors that monitors when  testing is complete
#=====================================================================
rm -rf ${TMPDIR}/script2
cat << ENDSCRIPT > ${TMPDIR}/script2
#!/bin/sh +x

ECHO=\`which echo\`
num=\$1
LOGDIR=\$2

#num=\`ls -al instance*log | wc -l | sed -e 's/ //g' \`
count=\`grep -a "Testing Complete" \${LOGDIR}/instance*log | wc -l | sed -e 's/ //g' \`
\$ECHO "Waiting for the (\$num) instances to complete testing"
\$ECHO -n "\$count"
while [ \$count -ne \$num ]
do
\$ECHO -n ",\$count"
count=\`grep -a "Testing Complete" \${LOGDIR}/instance*log | wc -l | sed -e 's/ //g' \`
sleep 5
done
\$ECHO ", \$count"

count=\`grep -a "Testing Complete" \${LOGDIR}/server.log | wc -l | sed -e 's/ //g' \`
\$ECHO "Waiting for the MASTER to complete testing"
\$ECHO -n "\$count"
while [ \$count -ne 1 ]
do
\$ECHO -n ",\$count"
count=\`grep -a "Testing Complete" \${LOGDIR}/server.log | wc -l | sed -e 's/ //g' \`
sleep 5
done

\$ECHO  ", \$count"
\$ECHO  "The following logs contain failures:"
\$ECHO  "==============="
grep -a "FAILED" \${LOGDIR}/instance*log
\$ECHO  "==============="
\$ECHO  "The following are the time results for SENDING messages:"
grep -a "Sending Messages Time data" \${LOGDIR}/instance*log
\$ECHO  "==============="
\$ECHO  "The following are the time results for RECEIVING messages:"
grep -a "Receiving Messages Time data" \${LOGDIR}/instance*log
\$ECHO  "==============="
\$ECHO  "The following are EXCEPTIONS found in the logs:"
\$ECHO  "==============="
grep -a "Exception" \${LOGDIR}/instance*log
grep -a "Exception" \${LOGDIR}/server.log
\$ECHO  "==============="
\$ECHO  "The following are SEVERE messages found in the logs:"
\$ECHO  "==============="
grep -a "SEVERE" \${LOGDIR}/instance*log
grep -a "SEVERE" \${LOGDIR}/server.log
\$ECHO  "==============="

exit 0

ENDSCRIPT
#=====================================================================

#=====================================================================
# Create the script monitors that monitors when  testing is complete
#=====================================================================
rm -rf ${TMPDIR}/script3
cat << ENDSCRIPT > ${TMPDIR}/script3
#!/bin/sh +x


ECHO=\`which echo\`
num=\$1
LOGDIR=\$2
#num=\`ls -al instance*log | wc -l | sed -e 's/ //g' \`
count=\`grep "All members have joined the group" \${LOGDIR}/instance*log | wc -l | sed -e 's/ //g' \`
\$ECHO "Waiting for the (\$num) instances to join group"
\$ECHO -n "\$count"
while [ \$count -ne \$num ]
do
\$ECHO -n ",\$count"
count=\`grep "All members have joined the group" \${LOGDIR}/instance*log | wc -l | sed -e 's/ //g' \`
sleep 5
done
\$ECHO  ", \$count"
\$ECHO "All (\$num) instances have joined the group, testing will now begin"

exit 0

ENDSCRIPT
#=====================================================================

############################################
# This is where test execution really begins
############################################

usage () {
    cat << USAGE
Usage: $0
`${TMPDIR}/script1 -h`
USAGE
exit 1
}


ECHO=`which echo`

chmod 755 ${TMPDIR}/script1
chmod 755 ${TMPDIR}/script2
chmod 755 ${TMPDIR}/script3

if [ "$1" == "-h" ]; then
    usage
fi

$ECHO "Number of messages=${NUMOFMSGS}"
$ECHO "Message size=${MSGSIZE}"
$ECHO "Log Directory=${LOGDIR}"

sdtcp=${STARTTCPPORT}
edtcp=`expr ${sdtcp} + 30`

$ECHO "Starting MASTER node"
${TMPDIR}/script1 master  ${GROUPNAME} ${NUMOFINSTANCES} ${sdtcp} ${edtcp} ${LOGLEVEL} >& ${LOGDIR}/server.log &

# give time for the MASTER to start
sleep 5
$ECHO "Starting ${numInstances} instances"

instNum=${STARTINSTANCENUM}
sdtcp=`expr ${edtcp} + 1`
edtcp=`expr ${sdtcp} + 30`
count=1
while [ $count -le ${NUMOFINSTANCES} ]
do
     ${TMPDIR}/script1 instance${instNum} ${GROUPNAME} ${NUMOFINSTANCES} ${NUMOFMSGS} ${MSGSIZE} ${SENDTYPE} ${sdtcp} ${edtcp} ${LOGLEVEL}  >& ${LOGDIR}/instance${instNum}.log &
     instNum=`expr ${instNum} + 1`
     sdtcp=`expr ${edtcp} + 1`
     edtcp=`expr ${sdtcp} + 30`
     count=`expr ${count} + 1`

done

#/tmp/script1 instance101 ${numInstances} ${msgSize} ${numOfMsgs} 9130 9160 INFO >& ${LOGDIR}/instance101.log &
#/tmp/script1 instance102 ${numInstances} ${msgSize} ${numOfMsgs} 9160 9190 INFO >& ${LOGDIR}/instance102.log &
#/tmp/script1 instance103 ${numInstances} ${msgSize} ${numOfMsgs} 9230 9260 INFO >& ${LOGDIR}/instance103.log &

# give time for the instances to start
sleep 3
# monitor for the testing to begin
${TMPDIR}/script3 ${NUMOFINSTANCES} ${LOGDIR}
# monitor when the testing is complete
${TMPDIR}/script2 ${NUMOFINSTANCES} ${LOGDIR}
