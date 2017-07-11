#!/bin/bash

eval $(gpg-agent --daemon --no-grab --write-env-file $HOME/.gpg-agent-info)
export GPG_TTY=$(tty)
export GPG_AGENT_INFO
mvn -Darguments="-DskipTests" -Prelease release:prepare
