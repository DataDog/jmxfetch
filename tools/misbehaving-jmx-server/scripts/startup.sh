#!/usr/bin/env bash

nohup ./init.sh &

java -cp misbehavingjmxserver-1.0-SNAPSHOT-jar-with-dependencies.jar "org.datadog.supervisor.App"
