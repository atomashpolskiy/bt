#!/bin/bash

if [ "$TRAVIS_REPO_SLUG" == "atomashpolskiy/bt" ] && [ "$TRAVIS_OS_NAME" == "linux" ] && [ "$TRAVIS_PULL_REQUEST" == "false" ] && [ "$TRAVIS_BRANCH" == "master" ] && [ "$MAIN_BUILD" == "true" ]; then

    #  prevent showing the token in travis logs
    # TODO: Sonar stopped accepting Java 8; need to upgrade to at least Java 11
    #mvn sonar:sonar -Dsonar.login=${SONAR_TOKEN} -Psonar > /dev/null 2>&1

else

    echo "Skipping..."

fi
