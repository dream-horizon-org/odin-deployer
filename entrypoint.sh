#!/usr/bin/env bash
set -euo pipefail

JAVA_OPTS="-XX:+HeapDumpOnOutOfMemoryError"
JMX_OPTS="-Dcom.sun.management.jmxremote=true -Dcom.sun.management.jmxremote.port=1099 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false"
APP_OPTS="-Dvertx.disableDnsResolver=true"
export JVM_OPTS=${JVM_OPTS:-""}
#shellcheck disable=SC2086
exec java -jar "${JAVA_OPTS}" "${JMX_OPTS}" "${APP_OPTS}" ${JVM_OPTS} odin-deployer-fat.jar
