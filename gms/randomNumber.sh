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

DIVISOR=10000000
LIMIT=254
if [ ! -z "${1}" ]; then
    TMP=`echo "${1}" | egrep "^[0-9]+$" `
    if [ ! -z ${TMP} ]; then
       LIMIT=${TMP}
    fi
fi
if [ ${LIMIT} -lt 100 ]; then
   DIVISOR=${DIVISOR}0
elif [ ${LIMIT} -lt 10 ]; then
   DIVISOR=${DIVISOR}00
fi

seed=`( echo $$ ; w ; date ) | cksum | awk -F" "  '{print $1}' `
#echo "seed=${seed}"
NUM=`expr $seed / 10000000`
while [ ${NUM} -lt 1 -o ${NUM} -gt ${LIMIT} ];
do
   seed=`( echo $$ ; w ; date ) | cksum | awk -F" "  '{print $1}' `
   #echo "seed=${seed}"
   NUM=`expr $seed / ${DIVISOR}`
done
echo $NUM
