#!/bin/sh

CLASSNAME="basic.SimpleMain"

if [ -n "$1" ]
then
    CLASSNAME=$1
fi

dse spark-submit \
  --class "io.muvr.analytics.$CLASSNAME" \
  --jars common-protocol/target/common-protocol_2.10-1.0.0-SNAPSHOT.jar \
  --packages com.typesafe.akka:akka-persistence-experimental_2.10:2.3.12 \
  basic/target/basic-assembly-1.0.0-SNAPSHOT.jar
