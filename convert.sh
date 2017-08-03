#!/bin/bash

export JAVA_OPTS=${JAVA_OPTS:=-Xmx2G}

PRG="$0"
CURDIR="`pwd`"
# need this for relative symlinks
while [ -h "$PRG" ] ; do
  ls=`ls -ld "$PRG"`
  link=`expr "$ls" : '.*-> \(.*\)$'`
  if expr "$link" : '/.*' > /dev/null; then
    PRG="$link"
  else
    PRG=`dirname "$PRG"`"/$link"
  fi
done
SCRIPTDIR=`dirname "$PRG"`
SCRIPTDIR=`cd "$SCRIPTDIR"; pwd -P`

echo JAVA_OPTS is set to $JAVA_OPTS
groovy -cp $GATE_HOME/bin/gate.jar:$GATE_HOME/lib/'*' "$SCRIPTDIR"/convert.groovy "$@" 
