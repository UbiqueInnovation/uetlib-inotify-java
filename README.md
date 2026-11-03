# uetlib-inotify-java

Linux inotify JNI bindings, derived from Nick Bargnesi's inotify-java and the
recovered Ubique `ch.ubique:inotify:2.0` project. Java packages are
`ch.ubique.uetlib.inotify` (including matching JNI bindings). This library is
**LGPL-3.0-or-later**, including its native implementation; see LICENSE, COPYING and COPYING.LESSER.

Development coordinate: `ch.ubique.uetlib:uetlib-inotify-java:3.0.0-SNAPSHOT`.
Nothing has been published. The native reconstruction is not claimed to be a
byte-identical reproduction of the historical binary. See [PROVENANCE.md](PROVENANCE.md).

## Build and test

Linux JDK 25, Maven and g++ are required for the complete build (Java bytecode
targets Java 25). From the repository root:

```sh
./scripts/verify-linux.sh
```

Or use Docker, including amd64 emulation on an ARM host:

```sh
docker build --platform linux/amd64 -t uetlib-inotify-build .
docker run --rm --platform linux/amd64 -v "$PWD:/work" uetlib-inotify-build ./scripts/verify-linux.sh
```

The build generates JNI headers from Java sources and compiles the native library,
runs Linux filesystem integration tests with `-Xcheck:jni`, then packages that
library into the JAR. A separate JVM then runs the tests against the packaged JAR
with no external native-library path, verifying bundled-library extraction and loading.
No historical precompiled native binary is checked in.
Build on the target Linux architecture; the build currently includes one native
architecture per JAR, not a cross-platform distribution. No Windows/macOS support.

Always call `destroy()` when finished: recovered code uses non-daemon reader
threads. This initial reconstruction retains historical behavior; additional
resource/error-path hardening should be reviewed separately.

## Changes and provenance

The history separates the pristine upstream 2.0 Java/native sources, recovered ZIP
Java plus reconstructed native changes, and the package/JNI migration to
`ch.ubique.uetlib.inotify`. Build tooling, formatting and post-reconstruction fixes
follow separately. Original copyright headers are retained.

To compare behavior with the exact historical Java classes after building,
mount/provide your recovered JAR and run:

```sh
REFERENCE_JAR=/path/to/inotify-2.0.jar ./scripts/compare-reference.sh
```

The comparison builds a temporary copy of the current native implementation with
the old JNI namespace and adapts the test package to the historical JAR. This
compatibility build is used only for comparison; the packaged-library tests exercise
the new package and JNI bindings directly.

Set `NATIVE_LIBRARY_PATH` to a directory containing the historical
`libinotify-java-noglobal.so` to run the same suite against that reference binary.
Reference artifacts are external inputs, never downloaded or published by the build.
