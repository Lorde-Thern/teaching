#!/bin/sh
# Compile and run the lessons. Needs Java 17 or newer.
#   ./run.sh        every lesson
#   ./run.sh 4      just lesson 4
cd "$(dirname "$0")"
rm -rf out
javac -d out src/space/*.java || exit 1
java -cp out space.Main "$@"
