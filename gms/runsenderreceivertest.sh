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

PWD=`pwd`
ECHO=`which echo`

#########################################
# Create the scripts used to run the test
#########################################

#===============================================
# Create the script that actually runs the test
#===============================================
rm -rf /tmp/script1
cat << ENDSCRIPT > /tmp/script1
#!/bin/sh +x

ECHO=\`which echo\`

publish_home=$PWD/dist
lib_home=$PWD/lib
\$ECHO "Arg1=\${1}"
\$ECHO "Arg2=\${2}"
\$ECHO "Arg3=\${3}"
\$ECHO "Arg4=\${4}"
\$ECHO "Arg5=\${5}"
\$ECHO "Arg6=\${6}"
\$ECHO "Arg7=\${7}"

java -Dcom.sun.management.jmxremote -DLOG_LEVEL=\$7 -cp \${publish_home}/shoal-gms-test.jar:\${publish_home}/shoal-gms.jar:\${lib_home}/bcprov-jdk14.jar:\${lib_home}/grizzly2-framework.jar:\${lib_home}/grizzly-framework.jar:\${lib_home}/grizzly-utils.jar -DTCPSTARTPORT=\$5 -DTCPENDPORT=\$6 -DSHOAL_GROUP_COMMUNICATION_PROVIDER=grizzly com.sun.enterprise.shoal.messagesenderreceivertest.SenderReceiver \$1 \$2 \$3 \$4

ENDSCRIPT
#===============================================

#=====================================================================
# Create the script monitors that monitors when  testing is complete
#=====================================================================
rm -rf /tmp/script2
cat << ENDSCRIPT > /tmp/script2
#!/bin/sh +x

ECHO=\`which echo\`
num=\$1
logdir=\$2

#num=\`ls -al instance*log | wc -l | sed -e 's/ //g' \`
count=\`grep "Testing Complete" \${logdir}/instance*log | wc -l | sed -e 's/ //g' \`
\$ECHO "Waiting for the (\$num) instances to complete testing"
\$ECHO -n "\$count"
while [ \$count -ne \$num ]
do
\$ECHO -n ",\$count"
count=\`grep "Testing Complete" \${logdir}/instance*log | wc -l | sed -e 's/ //g' \`
sleep 5
done
\$ECHO ", \$count"

count=\`grep "Testing Complete" \${logdir}/server.log | wc -l | sed -e 's/ //g' \`
\$ECHO "Waiting for the DAS to complete testing"
\$ECHO -n "\$count"
while [ \$count -ne 1 ]
do
\$ECHO -n ",\$count"
count=\`grep "Testing Complete" \${logdir}/server.log | wc -l | sed -e 's/ //g' \`
sleep 5
done

\$ECHO  ", \$count"
\$ECHO  "The following logs contain failures:"
\$ECHO  "==============="
grep "FAILED" \${logdir}/instance*log
\$ECHO  "==============="
\$ECHO  "The following are the time results for sending messages:"
grep "Sending Messages Time data" \${logdir}/instance*log
\$ECHO  "==============="
\$ECHO  "The following are the time results for receiving messages:"
grep "Receiving Messages Time data" \${logdir}/instance*log
\$ECHO  "==============="
\$ECHO  "The following are EXCEPTIONS found in the logs:"
\$ECHO  "==============="
grep "Exception" \${logdir}/instance*log
grep "Exception" \${logdir}/server.log
\$ECHO  "==============="
\$ECHO  "The following are SEVERE messages found in the logs:"
\$ECHO  "==============="
grep "SEVERE" \${logdir}/instance*log
grep "SEVERE" \${logdir}/server.log
\$ECHO  "==============="

exit 0

ENDSCRIPT
#=====================================================================

#=====================================================================
# Create the script monitors that monitors when  testing is complete
#=====================================================================
rm -rf /tmp/script3
cat << ENDSCRIPT > /tmp/script3
#!/bin/sh +x


ECHO=\`which echo\`
num=\$1
logdir=\$2
#num=\`ls -al instance*log | wc -l | sed -e 's/ //g' \`
count=\`grep "All members have joined the group" \${logdir}/instance*log | wc -l | sed -e 's/ //g' \`
\$ECHO "Waiting for the (\$num) instances to join group"
\$ECHO -n "\$count"
while [ \$count -ne \$num ]
do
\$ECHO -n ",\$count"
count=\`grep "All members have joined the group" \${logdir}/instance*log | wc -l | sed -e 's/ //g' \`
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
`/tmp/script1 -h`
The optional parameters are : <log level> <tcpstartport num> <tcpendport>

   <tcpstartport> and <tcpendport> are optional.  Grizzly and jxta transports have different defaults.
USAGE
exit 1
}


chmod 755 /tmp/script1
chmod 755 /tmp/script2
chmod 755 /tmp/script3

logdir=`pwd`/LOGS/senderreceivertest
if [ ! -d ${logdir} ] ; then
    mkdir ${logdir}
else
    rm -rf ${logdir}/instance*.log ${logdir}/server.log
fi

if [ "$1" == "-h" ]; then
    usage
fi

numInstances="10"

msgSize=1024
numOfMsgs=7000
startInstanceNum=101
startdtcpport=9130

$ECHO "Message size=${msgSize}"
$ECHO "Number of messages=${numOfMsgs}"
$ECHO "Log Directory=${logdir}"

$ECHO "Starting DAS"
/tmp/script1 server ${numInstances} >& ${logdir}/server.log &

# give time for the DAS to start
sleep 5
$ECHO "Starting ${numInstances} instances"

sin=${startInstanceNum}
sdtcp=${startdtcpport}
edtcp=`expr ${sdtcp} + 30`
count=1
while [ $count -le ${numInstances} ]
do
     /tmp/script1 instance${sin} ${numInstances} ${msgSize} ${numOfMsgs} ${sdtcp} ${edtcp} INFO >& ${logdir}/instance${sin}.log &
     sin=`expr ${sin} + 1`
     sdtcp=`expr ${edtcp} + 1`
     edtcp=`expr ${sdtcp} + 30`
     count=`expr ${count} + 1`

done
#/tmp/script1 instance101 ${numInstances} ${msgSize} ${numOfMsgs} 9130 9160 INFO >& ${logdir}/instance101.log &
#/tmp/script1 instance102 ${numInstances} ${msgSize} ${numOfMsgs} 9160 9190 INFO >& ${logdir}/instance102.log &
#/tmp/script1 instance103 ${numInstances} ${msgSize} ${numOfMsgs} 9230 9260 INFO >& ${logdir}/instance103.log &
#/tmp/script1 instance104 ${numInstances} ${msgSize} ${numOfMsgs} 9261 9290 INFO >& ${logdir}/instance104.log &
#/tmp/script1 instance105 ${numInstances} ${msgSize} ${numOfMsgs} 9330 9360 INFO >& ${logdir}/instance105.log &
#/tmp/script1 instance106 ${numInstances} ${msgSize} ${numOfMsgs} 9361 9390 INFO >& ${logdir}/instance106.log &
#/tmp/script1 instance107 ${numInstances} ${msgSize} ${numOfMsgs} 9430 9460 INFO >& ${logdir}/instance107.log &
#/tmp/script1 instance108 ${numInstances} ${msgSize} ${numOfMsgs} 9461 9490 INFO >& ${logdir}/instance108.log &
#/tmp/script1 instance109 ${numInstances} ${msgSize} ${numOfMsgs} 9530 9560 INFO >& ${logdir}/instance109.log &
#/tmp/script1 instance110 ${numInstances} ${msgSize} ${numOfMsgs} 9561 9590 INFO >& ${logdir}/instance110.log &

# give time for the instances to start
sleep 3
# monitor for the testing to begin
/tmp/script3 ${numInstances} ${logdir}
# monitor when the testing is complete
/tmp/script2 ${numInstances} ${logdir}
