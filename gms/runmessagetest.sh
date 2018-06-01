#!/bin/sh
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

publish_home=./dist
lib_home=./lib

usage () {
    cat << USAGE 
Usage: $0 <parameters...> 
The required parameters are :
 <instance_id_token> <sendto-instance_id_token> <sendingThreadNumber> <log level> <tcpstartport> <tcpendport>
<tcpstartport> and <tcpendport> are optional.  Grizzly and jxta transports have different defaults.
USAGE
   exit 0
}
java -Dcom.sun.management.jmxremote -DLOG_LEVEL=$4 -cp ${publish_home}/shoal-gms-tests.jar:${publish_home}/shoal-gms.jar:${lib_home}/grizzly2-framework.jar:${lib_home}/grizzly-framework.jar:${lib_home}/grizzly-utils.jar -DTCPSTARTPORT=$5 -DTCPENDPORT=$6 -DSHOAL_GROUP_COMMUNICATION_PROVIDER=grizzly com.sun.enterprise.shoal.multithreadmessagesendertest.MultiThreadMessageSender $1 $2 $3 \;
