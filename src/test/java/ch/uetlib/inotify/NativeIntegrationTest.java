package ch.uetlib.inotify;

import static org.junit.jupiter.api.Assertions.*;

import ch.uetlib.inotify.enums.Event;
import ch.uetlib.inotify.exceptions.InotifyException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Files;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class NativeIntegrationTest {
    @BeforeAll
    public static void loadNativeLibrary() throws InotifyException {
        if (!Boolean.getBoolean("inotify.test.bundled")) {
            NativeInotify.loadLibrary("inotify-java-noglobal");
        }
    }

    @Test
    @Timeout(15)
    void deliversCreateCloseMoveDeleteAndIgnoredEvents() throws Exception {
        final var directory = Files.createTempDirectory("inotify-test-");
        final var events = new LinkedBlockingQueue<InotifyEvent>();
        try (final var watcher = new Inotify()) {
            final var wd =
                    watcher.addWatch(
                            directory.toString(),
                            Event.Create,
                            Event.Close_Write,
                            Event.Moved_From,
                            Event.Moved_To,
                            Event.Delete);
            watcher.addListener(
                    wd,
                    new InotifyEventListener() {
                        public void filesystemEventOccurred(final InotifyEvent event) {
                            events.add(event);
                        }

                        public void queueFull(final EventQueueFull event) {
                            fail("Unexpected overflow");
                        }
                    });
            final var first = directory.resolve("first.txt");
            final var second = directory.resolve("second.txt");
            Files.writeString(first, "hello");
            await(events, Event.Create, "first.txt");
            await(events, Event.Close_Write, "first.txt");
            Files.move(first, second);
            final var from = await(events, Event.Moved_From, "first.txt");
            final var to = await(events, Event.Moved_To, "second.txt");
            assertEquals(from.getCookie(), to.getCookie());
            Files.delete(second);
            await(events, Event.Delete, "second.txt");
            Files.delete(directory);
            await(events, null, null);
        } finally {
            Files.deleteIfExists(directory.resolve("first.txt"));
            Files.deleteIfExists(directory.resolve("second.txt"));
            Files.deleteIfExists(directory);
        }
    }

    @Test
    @Timeout(15)
    void closesReaderThreadsAcrossRepeatedInstances() throws Exception {
        for (var i = 0; i < 20; i++) {
            final String name;
            final Inotify closed;
            try (final var watcher = new Inotify()) {
                name = "ConcurrentReader:" + watcher.getFileDescriptor();
                closed = watcher;
            }
            final var reader =
                    Thread.getAllStackTraces().keySet().stream()
                            .filter(t -> t.getName().equals(name))
                            .findFirst()
                            .orElse(null);
            if (reader != null) {
                reader.join(2000);
                assertFalse(reader.isAlive());
            }
            assertTrue(closed.isInactive());
        }
    }

    @Test
    void rejectsMissingWatchPath() throws Exception {
        try (final var watcher = new Inotify()) {
            final var expected =
                    assertThrows(
                            InotifyException.class,
                            () ->
                                    watcher.addWatch(
                                            "/nonexistent-inotify-" + java.util.UUID.randomUUID(),
                                            Event.Create));
            assertNotNull(expected.getMessage());
        }
    }

    @Test
    void closeDestroysInstance() throws Exception {
        final Inotify closed;
        try (final var watcher = new Inotify()) {
            closed = watcher;
            assertTrue(watcher.isActive());
        }
        assertTrue(closed.isInactive());
    }

    @Test
    void recognizesRecoveredCombinedEventMasks() {
        assertTrue(Event.isSet(Event.Close, Event.Close_Write.value()));
        assertTrue(Event.isSet(Event.Close, Event.Close_No_Write.value()));
        assertTrue(Event.isSet(Event.Moved, Event.Moved_From.value()));
        assertTrue(Event.isSet(Event.Moved, Event.Moved_To.value()));
    }

    private static InotifyEvent await(
            final LinkedBlockingQueue<InotifyEvent> events, final Event expected, final String name)
            throws Exception {
        final var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            final var event = events.poll(100, TimeUnit.MILLISECONDS);
            if (event != null
                    && (expected == null
                            ? event.isIgnored()
                            : Event.isSet(expected, event.getMask()))
                    && java.util.Objects.equals(name, event.getName())) {
                return event;
            }
        }
        throw new AssertionError("Missing " + expected + " event for " + name);
    }
}
