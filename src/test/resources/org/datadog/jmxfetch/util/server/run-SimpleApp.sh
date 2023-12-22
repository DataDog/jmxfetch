#!/usr/bin/env sh
set -f

[ -n "$JAVA_OPTS" ] || JAVA_OPTS="-Xmx128M -Xms128M"
[ -n "$RMI_PORT" ] || RMI_PORT="9010"

echo "Using `java --version`"
echo "With JAVA_OPTS '${JAVA_OPTS}'"
CONTAINER_IP=`awk 'END{print $1}' /etc/hosts`

# shellcheck disable=SC2086
javac -d app app.java

echo "Starting app with hostname set to ${CONTAINER_IP}"

java -cp ./app \
    ${JAVA_OPTS} \
    -Dcom.sun.management.jmxremote=true \
    -Dcom.sun.management.jmxremote.port=${RMI_PORT} \
    -Dcom.sun.management.jmxremote.rmi.port=${RMI_PORT} \
    -Dcom.sun.management.jmxremote.authenticate=false \
    -Dcom.sun.management.jmxremote.ssl=false \
    -Djava.rmi.server.hostname=${CONTAINER_IP} \
    org.datadog.jmxfetch.util.server.SimpleApp

# java -jar jmxterm-1.0.2-uber.jar -l service:jmx:rmi:///jndi/rmi://localhost:9010/jmxrmi
