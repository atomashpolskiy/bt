#!/bin/bash

export MAVEN_OPTS="-Xmx256m"
mvn clean install -Pcodecov 2>&1 | grep -Ev 'DEBUG|TRACE'
exit ${PIPESTATUS[0]} # make sure that maven's exit code is returned to travis build
