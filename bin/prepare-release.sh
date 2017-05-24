#!/bin/bash

eval $(gpg-agent --daemon --no-grab --write-env-file $HOME/.gpg-agent-info)
export GPG_TTY=$(tty)
export GPG_AGENT_INFO
mvn -Darguments="-Dskip.unit.tests -Dskip.integration.tests -DskipTests" -Prelease release:prepare
