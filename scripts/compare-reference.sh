#!/usr/bin/env bash
# Run after verify-linux.sh. Historical Java uses a compatibility native build.
set -euo pipefail
cd "$(dirname "$0")/.."
: "${REFERENCE_JAR:?Set REFERENCE_JAR to the recovered inotify-2.0.jar}"
[[ -f "$REFERENCE_JAR" ]] || exit 1
mvn -B org.apache.maven.plugins:maven-dependency-plugin:3.9.0:build-classpath \
    -Dmdep.outputFile=target/test-classpath.txt -Dmdep.includeScope=test
mkdir -p target/reference-native-src target/reference-native target/reference-test-src target/reference-test-classes

# Adapt the current implementation back to the old namespace for the historical JAR.
unmigrate() {
    sed -e 's|ch\.uetlib\.inotify|com.den_4.inotify_java|g' \
        -e 's|ch/uetlib/inotify|com/den_4/inotify_java|g' \
        -e 's|Java_ch_uetlib_inotify_|Java_com_den_14_inotify_1java_|g' \
        -e 's|ch_uetlib_inotify_NativeInotify|com_den_4_inotify_java_NativeInotify|g' "$1"
}
if [[ -z "${NATIVE_LIBRARY_PATH:-}" ]]; then
    : "${JAVA_HOME:?Set JAVA_HOME to a Linux JDK 25}"
    for source in src/main/native/*; do
        unmigrate "$source" > "target/reference-native-src/$(basename "$source")"
    done
    unmigrate target/native-headers/ch_uetlib_inotify_NativeInotify.h \
        > target/reference-native-src/com_den_4_inotify_java_NativeInotify.h
    "${CXX:-g++}" -std=c++11 -O0 -fPIC -shared -fstack-protector-strong \
        -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux" -Itarget/reference-native-src \
        target/reference-native-src/inotify-interface.cpp \
        -o target/reference-native/libinotify-java-noglobal.so
fi
unmigrate src/test/java/ch/uetlib/inotify/NativeIntegrationTest.java \
    > target/reference-test-src/NativeIntegrationTest.java
javac --release 25 -cp "$REFERENCE_JAR:$(cat target/test-classpath.txt)" \
    -d target/reference-test-classes target/reference-test-src/NativeIntegrationTest.java
java -Xcheck:jni -Djava.library.path="${NATIVE_LIBRARY_PATH:-$PWD/target/reference-native}" \
    -cp "$REFERENCE_JAR:target/reference-test-classes:$(cat target/test-classpath.txt)" \
    org.junit.platform.console.ConsoleLauncher execute --fail-if-no-tests \
    --select-class=com.den_4.inotify_java.NativeIntegrationTest
