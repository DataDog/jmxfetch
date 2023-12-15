#!/usr/bin/env sh

set -f

echo "Running $@"

[ -n "$JAVA_OPTS" ] || JAVA_OPTS="-Xmx128M -Xms128M"

echo "Using `java --version`"
echo "With JAVA_OPTS '${JAVA_OPTS}'"

# shellcheck disable=SC2086
java \
  ${JAVA_OPTS} \
  -cp misbehavingjmxserver-1.0-SNAPSHOT-jar-with-dependencies.jar \
  "$@"
