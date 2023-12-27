#!/usr/bin/env sh

set -f

echo "Running $@"

[ -n "$MISBEHAVING_OPTS" ] || MISBEHAVING_OPTS="-Xmx128M -Xms128M"

echo "Using `java --version`"
echo "With MISBEHAVING_OPTS '${MISBEHAVING_OPTS}'"

# shellcheck disable=SC2086
java -Xmx64M -Xms64M \
  -cp misbehavingjmxserver-1.0-SNAPSHOT-jar-with-dependencies.jar \
  "$@"
