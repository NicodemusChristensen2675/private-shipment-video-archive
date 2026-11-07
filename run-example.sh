#!/usr/bin/env sh
set -eu

classes="${TMPDIR:-/tmp}/logistics-video-session-classes"
mkdir -p "$classes"
javac -d "$classes" $(find src/main/java -name '*.java')
java -cp "$classes" dev.learninglogistics.LogisticsLesson "${1:-SHIP-2048}" "${2:-DELIVERED}"
