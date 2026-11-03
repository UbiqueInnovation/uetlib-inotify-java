#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
# The Maven build compiles and bundles the native library.
mvn -B clean verify spotless:check

# Exercise extraction/loading from the deliverable JAR without the build .so on java.library.path.
mvn -B org.apache.maven.plugins:maven-dependency-plugin:3.9.0:build-classpath \
    -Dmdep.outputFile=target/test-classpath.txt -Dmdep.includeScope=test
jars=(target/uetlib-inotify-java-*.jar)
[[ ${#jars[@]} -eq 1 ]] || { echo 'Expected one packaged library JAR' >&2; exit 1; }
java -Xcheck:jni -Dinotify.test.bundled=true -Djava.library.path=/nonexistent \
    -cp "${jars[0]}:target/test-classes:$(cat target/test-classpath.txt)" \
    org.junit.runner.JUnitCore ch.ubique.uetlib.inotify.NativeIntegrationTest \
    ch.ubique.uetlib.inotify.RegressionTest
