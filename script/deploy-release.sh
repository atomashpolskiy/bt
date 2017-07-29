#!/bin/bash

#
# Run this in case the staged repository is not uploaded to Nexus
# (release:perform will contain lines like 'Execution skipped to the last project')
#

cd target/checkout
mvn nexus-staging:deploy-staged
