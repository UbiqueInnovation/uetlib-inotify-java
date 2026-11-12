package ch.uetlib.inotify;

import static org.junit.jupiter.api.Assertions.*;

import ch.uetlib.inotify.enums.Event;
import ch.uetlib.inotify.exceptions.InotifyException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Files;

public class RegressionTest {
    @BeforeAll
    public static void loadNativeLibrary() throws InotifyException {
        NativeIntegrationTest.loadNativeLibrary();
    }

    @Test
    @Timeout(10)
    public void toleratesIgnoredEventAfterExplicitWatchRemoval() throws Exception {
        final var directory = Files.createTempDirectory("inotify-remove-");
        try (final var watcher = new Inotify()) {
            final var wd = watcher.addWatch(directory.toString(), Event.Create);
            assertTrue(watcher.removeWatch(wd));
            watcher.eventHandler(
                    new InotifyEvent(wd, ch.uetlib.inotify.enums.EventModifier.Ignored.value(), 0));
            assertNull(watcher.getPath(wd));
        } finally {
            Files.deleteIfExists(directory);
        }
    }
}
