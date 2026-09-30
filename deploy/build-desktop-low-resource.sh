#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
exec systemd-run --user --scope --quiet -p CPUQuota=100% -p MemoryMax=2G -p MemorySwapMax=256M \
    nice -n 15 ionice -c 3 ./gradlew "$@" --no-daemon --max-workers=1 --no-parallel \
    -Pkotlin.compiler.execution.strategy=in-process \
    '-Dorg.gradle.jvmargs=-Xmx768m -XX:MaxMetaspaceSize=384m -XX:ActiveProcessorCount=1' --console=plain
