#!/bin/bash

if [ "$TRAVIS_REPO_SLUG" == "atomashpolskiy/bt" ] && [ "$TRAVIS_OS_NAME" == "linux" ] && [ "$MAIN_BUILD" == "true" ]; then

    bash <(curl -s https://codecov.io/bash) -X gcov -X coveragepy

else

    echo "Skipping..."

fi