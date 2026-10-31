#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${JAVA_HOME:?Set JAVA_HOME to a Linux JDK (9+)}"
[[ $(uname -s) == Linux ]] || { echo 'Native build requires Linux' >&2; exit 1; }
mkdir -p target/native
"${CXX:-g++}" -std=c++11 -O0 -fPIC -shared -fstack-protector-strong \
    -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux" -Itarget/native-headers \
    src/main/native/inotify-interface.cpp -o target/native/libinotify-java-noglobal.so
