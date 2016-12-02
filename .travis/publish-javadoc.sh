#!/bin/bash

if [ "$TRAVIS_REPO_SLUG" == "atomashpolskiy/bt" ] && ["$TRAVIS_OS_NAME" == "linux"] && [ "$TRAVIS_PULL_REQUEST" == "false" ] && [ "$TRAVIS_BRANCH" == "master" ]; then

  git config --global user.email "travis@travis-ci.org"
  git config --global user.name "travis-ci"

  rm -rf $HOME/gh-pages
  mkdir -p $HOME/gh-pages/javadoc/latest
  cd $HOME/gh-pages

  git init
  git remote add javadoc https://${TRAVIS_TOKEN}@github.com/atomashpolskiy/bt.git
  git fetch --depth=1 javadoc gh-pages

  cp -R $TRAVIS_BUILD_DIR/target/site/apidocs $HOME/gh-pages/javadoc/latest

  git add --all
  git commit -m "latest javadoc for commit $TRAVIS_COMMIT (build $TRAVIS_BUILD_NUMBER)"
  git merge --no-edit -s ours remotes/javadoc/gh-pages
  git push javadoc master:gh-pages

fi