#!/bin/bash

# previously it used to work automatically per mvn release:perform...
# need to investigate why it doesn't upload to Sonatype anymore
cd target/checkout
mvn nexus-staging:deploy-staged
