#!/bin/bash

DIR=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )

UNLOCK="-XX:+UnlockExperimentalVMOptions -XX:+UnlockDiagnosticVMOptions"

# PROFILER="-agentpath:$DIR/../lib/libjprofilerti.so=port=10040,address=127.0.0.1,nowait"
# PROFILER="-agentpath:$DIR/../lib/libyjpagent.so=listen=127.0.0.1:10040,telemetryperiod=100"

GC="-Xms300m -Xmx1500M -XX:+UseConcMarkSweepGC -XX:+UseParNewGC -XX:MaxGCPauseMillis=15 -XX:GCTimeRatio=19 -XX:CMSInitiatingOccupancyFraction=70 -XX:+UseCMSInitiatingOccupancyOnly"

LOG=(-Xloggc:logs/gc.log -XX:+PrintGCDateStamps -XX:+PrintGCTimeStamps -XX:+PrintGCDetails -XX:+HeapDumpBeforeFullGC -XX:+HeapDumpOnOutOfMemoryError "-XX:OnOutOfMemoryError=kill -9 %p")

DEBUG="-Xdebug -Xrunjdwp:transport=dt_socket,address=127.0.0.1:10044,server=y,suspend=n"

OPTO="-XX:+AggressiveOpts -XX:MaxInlineLevel=12 -XX:+TrustFinalNonStaticFields -XX:+AggressiveUnboxing"

PERF_EVENTS="-XX:+PreserveFramePointer"


( java $UNLOCK $PROFILER $GC $DEBUG "${LOG[@]}" $OPTO $PERF_EVENTS -cp "$DIR/../target/*:$DIR/../target/dependency/*" the8472.mldht.Launcher & ) &
