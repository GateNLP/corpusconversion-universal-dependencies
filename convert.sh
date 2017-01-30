#!/bin/bash

export JAVA_OPTS=${JAVA_OPTS:=-Xmx2G}

echo JAVA_OPTS is set to $JAVA_OPTS
groovy -cp $GATE_HOME/bin/gate.jar:$GATE_HOME/lib/'*' convert.groovy "$@" 
