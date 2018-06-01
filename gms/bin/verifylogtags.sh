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

usage () {
 echo "usage: [-h] [-v] directorytosearch  propertiesfile"
 echo "     -h   help"
 echo "     -v   keep the working files"
 echo "     path of directory to search"
 echo "     logical or virtual path to propertes file"
 echo " "
 echo "Examples:"
 echo "  ./verifylogtags.sh ../impl src/main/resources/com/sun/enterprise/ee/cms/logging/LogStrings.properties"
 echo "  ./verifylogtags.sh ../api src/main/resources/com/sun/enterprise/ee/cms/core/LogStrings.properties"
 echo "  ./verifylogtags.sh /workspaces/shoal/trunk/gms/impl /workspaces/shoal/trunk/gms/impl/src/main/resources/com/sun/enterprise/ee/cms/logging/LogStrings.properties"
 exit 1
}

if [ "$1" = "-h" ];then
  usage
fi

VERBOSE=false
if [ "$1" = "-v" ];then
  VERBOSE=true
  shift
fi

DIR="$1"
PROPFILE="$2"
PWD=`pwd`

cd $DIR
rm -rf logstrings.out*
find . -name "*.java" -print -exec grep "LOG.log" {} \; > logstrings.out

cat logstrings.out | egrep "Level.INFO|Level.WARNING|Level.SEVERE|\.java" > logstrings.out1

cat logstrings.out1 | sed -e "s/^.*LOG/LOG/g" | egrep ", \"|,$|\.java$" > logstrings.out2

echo "Checking for split lines"
echo "------------------------"
#for item in `cat logstrings.out2`
cat logstrings.out2 | while read item
do
   if [ "`echo $item | grep java`" == "" ]; then
      if [ "`echo $item | grep ',$' | grep -v '\"' `" != "" ]; then
        echo "Found split line: File = $file, Item = $item"
      fi
   else
      file=$item
   fi
done
echo "-------------------------"

FOUND=0
cat logstrings.out2 | while read item
do
   if [ "`echo $item | grep java`" == "" ]; then
      tmp="`echo \"$item\" |  awk -F, '{print $2}' | sed -e 's/\"//g' | sed -e 's/);//g' | sed -e 's/ //' | grep '\.' | grep -v '+' `"
      #echo "TMP=$tmp"
      if [ "$tmp" != "" ]; then
        # if we found a log entry write the filename once
        if [ $FOUND -eq 0 ]; then
            echo $file >> logstrings.out3
            FOUND=1
        fi
        echo $tmp >> logstrings.out3
      fi
   else
      FOUND=0
      file=$item
   fi
done

echo "Checking for missing tags"
echo "-------------------------"
for item in `cat logstrings.out3`
do
   if [ "`echo $item | grep java`" == "" ]; then
      if [ "`grep $item $PROPFILE`" == "" ]; then
         echo "Found missing tag: File = $file, Item = $item"
      fi
   else
      file=$item
   fi
done
echo "-------------------------"

if [ $VERBOSE = false ];then
   rm -rf logstrings.out*
fi

cd $PWD
