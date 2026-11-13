# Changelog

## 3.0.3

- Use shared Maven build and publish workflows at `v1.0.0` to enforce formatting checks in CI.
- Make `NativeInotify` `AutoCloseable`; `close()` invokes `destroy()`. Remove the
  deprecated `finalize()`, which could not run while reader threads held the instance.
- Extract the bundled native library into a private, randomly named temporary directory
  and delete it right after loading, instead of a predictable path under the temp
  directory that `destroy()` swept from `/tmp`.
- Build the native library and run tests only on Linux; other platforms only compile.
- Migrate tests to JUnit Jupiter.

## 3.0.2

- Move Java packages and class references from `ch.ubique.uetlib` to `ch.uetlib`.

- Require Java 25, inheriting the shared parent's Java target.

- Restore original LGPL-3.0-or-later sources and recovered Ubique Java changes.
- Reconstruct the native `noglobal` implementation using upstream's epoll loop
  and symbolized disassembly of the historical binary.
- Move Maven coordinates from `ch.ubique:inotify:2.0` to
  `ch.uetlib:uetlib-inotify-java`.
- Add Linux native build, generated JNI headers and behavioral comparison tests.
- Retain LGPL licensing, source provenance and explicit reconstruction limits.
- Fix shutdown double-free, epoll descriptor cleanup, omitted class-reference
  cleanup and stale ignored-event bookkeeping in explicit follow-up changes.
- Build and bundle the JNI library in the Maven lifecycle, so `mvn clean install`
  and Central releases never produce a JAR without it.
- Verify bundled native-library extraction and loading in a separate JVM using
  the packaged JAR, in addition to external-library integration tests.
- Move Java packages and JNI bindings to `ch.uetlib.inotify` in a separate
  commit after the upstream and recovered-code snapshots.
