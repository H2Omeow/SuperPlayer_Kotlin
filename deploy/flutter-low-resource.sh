#!/bin/sh
set -eu
cd "$(dirname "$0")/../desktop_flutter"
flutter_bin=${FLUTTER_BIN:-flutter}
if [ "$#" -eq 0 ]; then
    echo "Usage: $0 analyze | test | build linux | run" >&2
    exit 2
fi
if [ "$1" = test ]; then
    shift
    set -- test --concurrency=1 "$@"
fi
exec sh ../deploy/run-low-resource.sh "$flutter_bin" "$@"
