#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
exec sh ./deploy/run-low-resource.sh ./gradlew "$@" --no-daemon --max-workers=2 --no-parallel \
    -Pkotlin.compiler.execution.strategy=in-process \
    '-Dorg.gradle.jvmargs=-Xmx768m -XX:MaxMetaspaceSize=384m -XX:ActiveProcessorCount=2' --console=plain
