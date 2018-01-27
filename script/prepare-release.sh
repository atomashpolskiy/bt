#!/bin/bash

# In case there are several PGP keys, set GPG_KEYNAME to a particular keyid.
# E.g. GPG_KEYNAME=FF001234 ./prepare-release.sh

if [[ ${GPG_KEYNAME} == "" ]]
then
    echo "Using default PGP key..."
else
    GPG_KEYNAME="-Dgpg.keyname=${GPG_KEYNAME}"
fi

mvn -Darguments="-DskipTests -DautoVersionSubmodules ${GPG_KEYNAME}" -Prelease release:prepare
