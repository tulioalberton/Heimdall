#!/bin/sh


echo "Starting Heimdall SDN controller ..."

#java -Dlogback.configurationFile="./logback.xml" -cp ./target/floodlight.jar:./lib/* net.floodlightcontroller.core.Main -cf src/main/resources/heimdallDefault.properties

java -Dlogback.configurationFile="./logback.xml" -cp ./target/floodlight.jar:./lib/* net.floodlightcontroller.core.Main -cf $@


