# Changelog

## 3.0.0 (unreleased)

- Restore original LGPL-3.0-or-later sources and recovered Ubique Java changes.
- Reconstruct the native `noglobal` implementation using upstream's epoll loop
  and symbolized disassembly of the historical binary.
- Move Maven coordinates from `ch.ubique:inotify:2.0` to
  `ch.ubique.uetlib:uetlib-inotify-java`.
- Add Linux native build, generated JNI headers and behavioral comparison tests.
- Retain LGPL licensing, source provenance and explicit reconstruction limits.
- Fix shutdown double-free, epoll descriptor cleanup, omitted class-reference
  cleanup and stale ignored-event bookkeeping in explicit follow-up changes.
- Verify bundled native-library extraction and loading in a separate JVM using
  the packaged JAR, in addition to external-library integration tests.
- Move Java packages and JNI bindings to `ch.ubique.uetlib.inotify` in a separate
  commit after the upstream and recovered-code snapshots.
