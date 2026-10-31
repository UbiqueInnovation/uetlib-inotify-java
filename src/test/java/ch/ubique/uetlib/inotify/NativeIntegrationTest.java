package ch.ubique.uetlib.inotify;

import static org.junit.Assert.*;

import ch.ubique.uetlib.inotify.enums.Event;
import ch.ubique.uetlib.inotify.exceptions.InotifyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.BeforeClass;
import org.junit.Test;

public class NativeIntegrationTest {
    @BeforeClass
    public static void loadNativeLibrary() throws InotifyException {
        if (!Boolean.getBoolean("inotify.test.bundled")) {
            NativeInotify.loadLibrary("inotify-java-noglobal");
        }
    }

    @Test(timeout = 15000)
    public void deliversCreateCloseMoveDeleteAndIgnoredEvents() throws Exception {
        Path directory = Files.createTempDirectory("inotify-test-");
        Inotify watcher = new Inotify();
        LinkedBlockingQueue<InotifyEvent> events = new LinkedBlockingQueue<>();
        try {
            int wd =
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
                        public void filesystemEventOccurred(InotifyEvent event) {
                            events.add(event);
                        }

                        public void queueFull(EventQueueFull event) {
                            fail("Unexpected overflow");
                        }
                    });
            Path first = directory.resolve("first.txt");
            Path second = directory.resolve("second.txt");
            Files.writeString(first, "hello");
            await(events, Event.Create, "first.txt");
            await(events, Event.Close_Write, "first.txt");
            Files.move(first, second);
            InotifyEvent from = await(events, Event.Moved_From, "first.txt");
            InotifyEvent to = await(events, Event.Moved_To, "second.txt");
            assertEquals(from.getCookie(), to.getCookie());
            Files.delete(second);
            await(events, Event.Delete, "second.txt");
            Files.delete(directory);
            await(events, null, null);
        } finally {
            watcher.destroy();
            Files.deleteIfExists(directory.resolve("first.txt"));
            Files.deleteIfExists(directory.resolve("second.txt"));
            Files.deleteIfExists(directory);
        }
    }

    @Test(timeout = 15000)
    public void closesReaderThreadsAcrossRepeatedInstances() throws Exception {
        for (int i = 0; i < 20; i++) {
            Inotify watcher = new Inotify();
            String name = "ConcurrentReader:" + watcher.getFileDescriptor();
            watcher.destroy();
            Thread reader =
                    Thread.getAllStackTraces().keySet().stream()
                            .filter(t -> t.getName().equals(name))
                            .findFirst()
                            .orElse(null);
            if (reader != null) {
                reader.join(2000);
                assertFalse(reader.isAlive());
            }
            assertTrue(watcher.isInactive());
        }
    }

    @Test
    public void rejectsMissingWatchPath() throws Exception {
        Inotify watcher = new Inotify();
        try {
            watcher.addWatch("/nonexistent-inotify-" + java.util.UUID.randomUUID(), Event.Create);
            fail("Expected missing-path error");
        } catch (InotifyException expected) {
            assertNotNull(expected.getMessage());
        } finally {
            watcher.destroy();
        }
    }

    @Test
    public void recognizesRecoveredCombinedEventMasks() {
        assertTrue(Event.isSet(Event.Close, Event.Close_Write.value()));
        assertTrue(Event.isSet(Event.Close, Event.Close_No_Write.value()));
        assertTrue(Event.isSet(Event.Moved, Event.Moved_From.value()));
        assertTrue(Event.isSet(Event.Moved, Event.Moved_To.value()));
    }

    private static InotifyEvent await(
            LinkedBlockingQueue<InotifyEvent> events, Event expected, String name)
            throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            InotifyEvent event = events.poll(100, TimeUnit.MILLISECONDS);
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
