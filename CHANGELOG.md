# Changelog

## 3.1.0

- `uetlib-parent` updated to
  `2026.11.5`: [Changelog/2026.11.5](https://github.com/UbiqueInnovation/uetlib-parent/blob/main/CHANGELOG.md#2026115) [Diff/2026.11.2-2026.11.5](https://github.com/UbiqueInnovation/uetlib-parent/compare/v2026.11.2...v2026.11.5)
- Updated dependencies and actions.

- Pull requests from this repository run the Linux verification through Artifactory, so they can use releases
  that are not on Central yet.
- Fix the warnings of Error Prone's default checks: redundant parentheses, `LinkedList`, switch statements and
  a Javadoc tag.

## 3.0.5

- Compile with the shared Error Prone and NullAway checks from `uetlib-parent` instead of disabling annotation
  processing. All packages are `@NullMarked`; methods, fields and parameters that can be `null` are `@Nullable`.
- Fix a `NullPointerException` on the first event of a `Watcher` created without a listener.
- `MonitorService.getLastArrivalDate()` returns an `Instant` instead of a `java.util.Date`, or `null` if no
  events have been received. `getLastArrivalDate(Locale)` is removed.

## 3.0.4

- Use shared Maven workflows at `v1.1.1`.
- Build pull requests, including forks, and pushes to `main` instead of every branch push.

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
