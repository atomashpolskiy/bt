#!/bin/bash

git remote add -f mldht https://github.com/the8472/mldht.git
git fetch mldht
git merge -X subtree=bt-dht/the8472/mldht --squash --allow-unrelated-histories mldht/master
