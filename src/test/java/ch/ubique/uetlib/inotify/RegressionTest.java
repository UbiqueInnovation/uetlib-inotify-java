package ch.ubique.uetlib.inotify;

import static org.junit.Assert.*;

import ch.ubique.uetlib.inotify.enums.Event;
import ch.ubique.uetlib.inotify.exceptions.InotifyException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.BeforeClass;
import org.junit.Test;

public class RegressionTest {
    @BeforeClass
    public static void loadNativeLibrary() throws InotifyException {
        NativeIntegrationTest.loadNativeLibrary();
    }

    @Test(timeout = 10000)
    public void toleratesIgnoredEventAfterExplicitWatchRemoval() throws Exception {
        Path directory = Files.createTempDirectory("inotify-remove-");
        Inotify watcher = new Inotify();
        try {
            int wd = watcher.addWatch(directory.toString(), Event.Create);
            assertTrue(watcher.removeWatch(wd));
            watcher.eventHandler(
                    new InotifyEvent(
                            wd, ch.ubique.uetlib.inotify.enums.EventModifier.Ignored.value(), 0));
            assertNull(watcher.getPath(wd));
        } finally {
            watcher.destroy();
            Files.deleteIfExists(directory);
        }
    }
}
