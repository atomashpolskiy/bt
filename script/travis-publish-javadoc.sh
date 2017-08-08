#!/bin/bash

if [ "$TRAVIS_REPO_SLUG" == "atomashpolskiy/bt" ] && [ "$TRAVIS_OS_NAME" == "linux" ] && [ "$TRAVIS_PULL_REQUEST" == "false" ] && [ "$TRAVIS_BRANCH" == "master" ] && [ "$MAIN_BUILD" == "true" ]; then

  git config --global user.email "travis@travis-ci.org"
  git config --global user.name "travis-ci"

  cd $HOME
  rm -rf gh-pages

  git clone https://${TRAVIS_TOKEN}@github.com/atomashpolskiy/bt.git gh-pages
  cd gh-pages
  git checkout gh-pages

  rm -rf javadoc/latest
  mkdir -p javadoc/latest

  cp -R $TRAVIS_BUILD_DIR/target/site/apidocs/* $HOME/gh-pages/javadoc/latest

  git add --all
  git commit -m "latest javadoc for commit $TRAVIS_COMMIT (build $TRAVIS_BUILD_NUMBER)"
  #  prevent showing the token in travis logs
  git push origin HEAD:gh-pages > /dev/null 2>&1

else

    echo "Skipping..."

fi
