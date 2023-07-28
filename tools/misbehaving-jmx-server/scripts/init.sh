#!/usr/bin/env bash

# Wait for port 8088 to be available
until curl -s -o /dev/null localhost:8088
do
  sleep 5
done

# Make a curl request
curl http://localhost:8080/your-api-endpoint
# http POST localhost:8088/init rmiHostname=192.168.160.3
IP_ADDRESS=`awk 'END{print $1}' /etc/hosts`
echo "Host is ${IP_ADDRESS}"
curl --silent --show-error -o /dev/null -X POST -H 'Content-Type: application/json'  -d "{\"rmiHostname\":\"${IP_ADDRESS}\"}" localhost:8088/init

# http POST localhost:8080/beans/Bohnanza  numDesiredBeans=5
