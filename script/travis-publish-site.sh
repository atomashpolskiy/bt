#!/bin/bash

if [ "$TRAVIS_REPO_SLUG" == "atomashpolskiy/bt" ] && [ "$TRAVIS_OS_NAME" == "linux" ] && [ "$TRAVIS_PULL_REQUEST" == "false" ] && [ "$TRAVIS_BRANCH" == "master" ] && [ "$MAIN_BUILD" == "true" ]; then

    mvn site

else

    echo "Skipping..."

fi