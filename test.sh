#!/bin/sh
# Compiles sources + tests into ./out-test and runs all tests (no database needed).
set -e
cd "$(dirname "$0")"
rm -rf out-test && mkdir out-test
javac -encoding UTF-8 -d out-test $(find src test -name '*.java')
java -cp out-test com.teqzo.testkit.TestRunner out-test
