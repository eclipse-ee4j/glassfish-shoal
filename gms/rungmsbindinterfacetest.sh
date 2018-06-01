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
 <instance_id_token> <groupname> <membertype{CORE|SPECTATOR}> <Life In Milliseconds> <log level> <bind_interface_ip_address>
Life in milliseconds should be at least 60000 to demo failure fencing.
<bind_interface_ip_address> refers to the IP address of a virtual or physical network interface which should be used 
by this test to bind to, for all communications. Currently this test only accepts IPv4 addresses. 
USAGE
   exit 0
}

if [ $# -lt 3 ]; then
    usage;
fi

if [ -n $5 ]; then 
	java -Dcom.sun.management.jmxremote -DMEMBERTYPE=$3 -DINSTANCEID=$1 -DCLUSTERNAME=$2 -DMESSAGING_MODE=true -DLIFEINMILLIS=$4 -DLOG_LEVEL=$5 -DBIND_INTERFACE_ADDRESS=$6 -cp ${publish_home}/shoal-gms-tests.jar:${publish_home}/shoal-gms.jar:${lib_home}/jxta.jar:${lib_home}/bcprov-jdk14.jar com.sun.enterprise.ee.cms.tests.ApplicationServer;
fi

