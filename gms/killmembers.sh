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

if [ -z "${1}" ] ;then
   PID=`jps -mlVv | grep "com.sun.enterprise." | awk '{print $1}'`
else
   PID=`jps -mlVv | grep "com.sun.enterprise." | grep ${1} | awk '{print $1}'`
fi
if [ ! -z "$PID" ]; then
   echo "Killing instance PID(s) :|$PID|"
   echo "Date:";date
   kill -9  $PID
   if [ -z "${1}" ] ;then
       jps -v | grep ApplicationServer
   else
       jps -v | grep ${1}
   fi
else
   echo "No pid(s) found"
fi
