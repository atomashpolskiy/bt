#!/bin/bash

export MAVEN_OPTS="-Xmx512m"

if [ "$MAIN_BUILD" == "true" ]; then

    mvn -U clean install -Pcodecov 2>&1 | grep -Ev 'DEBUG|TRACE'
    exit ${PIPESTATUS[0]} # make sure that maven's exit code is returned to travis build

elif [ "$TRAVIS_JDK_VERSION" == "oraclejdk9" ]; then

    mvn -U clean install -Pjdk9

fi