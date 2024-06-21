#!/usr/bin/env bash

mvn clean install
cp nifi-jsonld-nar/target/nifi-jsonld-nar-2.0.0-M2.nar /home/parklize/Documents/code/Tools/nifi-2.0.0-M2/nifi-2.0.0-M2/extensions/nifi-jsonld-nar-2.0.0-M2.nar 
/home/parklize/Documents/code/Tools/nifi-2.0.0-M2/nifi-2.0.0-M2/bin/nifi.sh restart
