#!/bin/sh
# Starts TEQZO. Settings come from .env or environment variables. Use: ./run.sh --check  to test the database only.
cd "$(dirname "$0")"
[ -d out ] || ./build.sh
exec java -Dfile.encoding=UTF-8 -cp "out:lib/*" com.teqzo.Main "$@"
