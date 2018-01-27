#!/bin/bash

# In case there are several PGP keys, set GPG_KEYNAME to a particular keyid.
# E.g. GPG_KEYNAME=FF001234 ./prepare-release.sh

if [[ ${GPG_KEYNAME} == "" ]]
then
    echo "Using default PGP key..."
else
    GPG_KEYNAME="-Dgpg.keyname=${GPG_KEYNAME}"
fi

eval $(gpg-agent --daemon --no-grab)
export GPG_TTY=$(tty)
export GPG_AGENT_INFO
mvn -Darguments="-DskipTests -DautoVersionSubmodules ${GPG_KEYNAME}" -Prelease release:prepare
