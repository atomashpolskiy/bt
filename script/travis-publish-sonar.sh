#!/bin/bash

if [ "$TRAVIS_REPO_SLUG" == "atomashpolskiy/bt" ] && [ "$TRAVIS_OS_NAME" == "linux" ] && [ "$TRAVIS_PULL_REQUEST" == "false" ] && [ "$TRAVIS_BRANCH" == "master" ] && [ "$MAIN_BUILD" == "true" ]; then

    #  prevent showing the token in travis logs
    mvn sonar:sonar -Dsonar.login=${SONAR_TOKEN} -Psonar > /dev/null 2>&1

else

    echo "Skipping..."

fi
