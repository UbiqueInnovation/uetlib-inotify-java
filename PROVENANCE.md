# Provenance and reconstruction

## Original sources

- Original LGPL-3.0-or-later 2.0 source archive:
  https://api.launchpad.net/1.0/inotify-java/2.0/2.0/+file/inotify-java-2.0-source.tbz2/file
- Upstream GitHub: https://github.com/nbargnesi/inotify-java
- Native event loop is taken from upstream commit
  `19782e37442655647df72a36d6c41fe58274516a` (`src/cpp/inotify-interface.cpp`).
  The recovered binary uses this later **epoll** loop, not original 2.0's select
  loop. The Java API is based on 2.0. Native source copyright reflects upstream.
- Original tutorial and distribution tooling are omitted; core API sources and
  native headers are preserved in the root commit.

## Recovered Ubique project

Both `inotify-java 1.zip` and `inotify-java 2.zip` had identical project files.
Their JAR matches the cached `ch.ubique:inotify:2.0` byte-for-byte:

`95a34e01e79c9b240626c7c7c130348abe23c95566eba300ff06195775e80254`

Build metadata: alig, 2021-03-22, Java 11.0.10, Maven 3.6.3. Java changes:
non-daemon ConcurrentReader, bundled-native loading/public pipe accessors in
NativeInotify, and compound close/move mask handling in Event. Archives contain
native binaries but no native source/build scripts. IDE/generated artifacts and
historical binaries are intentionally not imported.

## Native reconstruction

Reference `libinotify-java-noglobal.so` SHA-256:
`c32e0429093e18d32ffb07bde1ba737b122e243c46c2093c1083be99f9a61575`.
It is an unstripped ELF x86-64 binary without DWARF source information and records
Ubuntu GCC 7.5.0. Its symbolized disassembly shows:

- JNI version 9; JNI_ERR on load failure.
- Seven class global references in JNI_OnLoad, no cached method-ID setup there.
- Method IDs resolved at their use sites; no method-ID global-ref deletion.
- Later upstream epoll loop and event-construction/exception paths.

The reconstruction applies these changes to the available upstream native source.
Generated JNI declarations replace the historical header generation tool. **No exact source recovery or byte-for-byte
binary equivalence is claimed.**
Integration tests compare observable behavior, not all possible kernel/JVM errors.
Original native error paths and class-reference cleanup deserve a separate review.

## Packaging changes

The standalone POM uses current build plugins and Commons IO 2.21.0 instead of
historical 2.1; reference tests run the original JAR with the dependency versions
in this POM. The separate package migration changes Java packages, JNI entry points,
class lookups and descriptors to `ch.uetlib.inotify`. Reader-thread and
combined-mask semantics are preserved.

## Explicit post-reconstruction fixes

Separate from the reconstruction: clear the freed event-buffer pointer to prevent
shutdown double-free; close the epoll descriptor; release the omitted watch-limit
class global reference; check GetEnv and pending exceptions after Java pipe/FD
calls. Guard Java watch bookkeeping when a queued ignored event arrives after
explicit watch removal. Compile with the shared Error Prone and NullAway checks: JSpecify nullness
annotations, a `Watcher` without listeners no longer fails on its first event, and
`MonitorService.getLastArrivalDate()` returns an `Instant` instead of a `java.util.Date`. Fix Error Prone's
default warnings: redundant parentheses, `LinkedList`, switch statements and a Javadoc tag. These are
intentional corrections, not claims about the historical source.

## Runtime baseline

The current Docker build uses Ubuntu 24.04/GCC 13 and its output requires
`GLIBCXX_3.4.32`. This is not the historical GCC 7.5 runtime baseline. A production
release must choose/test the intended minimum Linux/libstdc++ baseline (or provide
a suitable toolchain image); successful tests in this image do not establish
compatibility with older distributions or other CPU architectures.

## Reviewable history

1. `75ecef1`: original 2.0 Java and native sources, verified byte-for-byte against
   the recovered upstream archive.
2. `1f8c67d`: exact recovered ZIP Java sources plus reconstructed `noglobal` native
   implementation. No Java formatter changes or post-reconstruction fixes.
3. `23e06f1`: package-only migration to `ch.uetlib.inotify`, including JNI
   symbols, descriptors and generated-header references.
4. Build tooling, AOSP formatting and the explicitly listed subsequent fixes.

The historical-JAR comparison temporarily maps the current native implementation
and integration tests back to the old namespace. It verifies behavior with the
original Java classes; normal and packaged-JAR tests verify the migrated API/JNI.
