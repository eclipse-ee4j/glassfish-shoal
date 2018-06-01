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

Usage()
{
  echo "usage: [-h] [-u] [-r] filename"
  echo "      -u: get udp data only (default is all)"
  echo "      -r: remove previous file"
  echo "filename: name of the file to store the output in"

  exit 0
}

UDP=false
if [ "${1}" = "-u" ]; then
   UDP=true
   shift
fi
REMOVE=false
if [ "${1}" = "-r" ]; then
   REMOVE=true
   shift
fi

FILE=""
if [ ! -z "${1}" ]; then
   FILE=${1}
   shift
fi

if [ ${REMOVE} = true ]; then
   rm -rf ${FILE}
fi

LOGDIR=`dirname ${FILE}`
if [ ! -z "${LOGDIR}" ]; then
   if [ ! -d ${LOGDIR} ];then
      mkdir -p ${LOGDIR}
   fi
fi

touch ${FILE}
date >> ${FILE}

mkdir tmp > /dev/null 2>&1
rm -rf tmp/netstat.tmp

if [ ${UDP} = true ]; then
    netstat -s | sed -n -e '/^[uU]dp:/,$p' > tmp/netstat.tmp
    first=1
    while read line
    do
      if [ $first -eq 1 ]
      then
         echo "$line" >> ${FILE}
         first=0
      else
         a=`echo "$line" | grep '^.*:$'`
         if [ ! -z  "$a" ]
         then
            break
         else
            echo "$line" >> ${FILE}
         fi
      fi
    done  < tmp/netstat.tmp

else
   netstat -u >> ${FILE}
fi
