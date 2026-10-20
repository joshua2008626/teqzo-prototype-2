#!/bin/sh
# Compiles TEQZO into ./out (needs JDK 17+; lib/ may hold the MySQL driver jar).
set -e
cd "$(dirname "$0")"
rm -rf out && mkdir out
javac -encoding UTF-8 -d out $(find src -name '*.java')
echo "Built into ./out"
