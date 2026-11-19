/**
 * Copyright © 2009 Nick Bargnesi <nick@den-4.com>. All rights reserved.
 *
 * <p>inotify-java is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * <p>inotify-java is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with
 * inotify-java. If not, see <http://www.gnu.org/licenses/>.
 *
 * <p>File: Inotify.java Project: inotify-java Package: ch.uetlib.inotify Author: Nick Bargnesi
 */
package ch.uetlib.inotify;

import ch.uetlib.inotify.exceptions.InotifyException;
import ch.uetlib.inotify.exceptions.InsufficientKernelMemoryException;
import ch.uetlib.inotify.exceptions.InvalidWatchDescriptorException;
import ch.uetlib.inotify.exceptions.SystemLimitException;
import ch.uetlib.inotify.exceptions.UserInstanceLimitException;
import ch.uetlib.inotify.exceptions.UserWatchLimitException;

import org.jspecify.annotations.Nullable;

import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This class serves as the base class for all Inotify types, providing a bridge to the native
 * implementation and interface.
 *
 * @since Version 2
 * @author Nick Bargnesi
 */
public abstract class NativeInotify implements AutoCloseable {

    /** Watch descriptor associated with this inotify instance. */
    protected int fileDescriptor;

    /** Flag used to indicate an active file descriptor. */
    private boolean fdActive;

    /** Provides thread-safety for {@link #fdActive} */
    private final Semaphore activeSem;

    /* Flag indicating whether the native library is loaded. */
    private static boolean nativeLibLoaded;
    /* Guarantee library loading is thread-safe. */
    private static final ReentrantLock lock = new ReentrantLock();

    private static final String LIB = "/native/";
    private static final String INOTIFY_32 = "libinotify-java-32.so";
    private static final String INOTIFY_64 = "libinotify-java-64.so";

    /** Contains the file descriptors for the read/write ends of a pipe, used for IPC purposes. */
    private final int[] pipes = new int[2];

    /**
     * Constructs a new Inotify instance.
     *
     * @throws InotifyException Thrown if the native inotify object could not be constructed. The
     *     cause of the exception will be provided.
     */
    protected NativeInotify() throws InotifyException {
        try {
            if (!isLibraryLoaded()) {
                loadLibrary(null);
            }
            fileDescriptor = init();
        } catch (final UnsatisfiedLinkError e) {
            throw new InotifyException("unsatisfied link", e);
        } catch (final InsufficientKernelMemoryException e) {
            throw new InotifyException("kernel memory exception", e);
        } catch (final SystemLimitException e) {
            throw new InotifyException("system limit reached", e);
        } catch (final UserInstanceLimitException e) {
            throw new InotifyException("user limit reached", e);
        }
        fdActive = true;
        activeSem = new Semaphore(1, true);
    }

    /**
     * Returns {@code true} if the native library has been loaded, {@code false} if not.
     *
     * <p>This method is {@link ch.uetlib.inotify.ThreadSafe thread-safe}.
     *
     * @return {@code true} if the native library has been loaded, {@code false} if not
     */
    @ThreadSafe
    public static boolean isLibraryLoaded() {
        lock.lock();
        try {
            return nativeLibLoaded;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Loads the native library specified by the filename. Supplying a {@code filename} value of
     * {@code null} will result in the library being loaded using the library paths provided to the
     * virtual machine.
     *
     * <p>This method is {@link ch.uetlib.inotify.ThreadSafe thread-safe}.
     *
     * @param filename The file to load
     * @throws SecurityException Thrown if a security manager is preventing loading of the dyanmic
     *     library
     * @exception UnsatisfiedLinkError Thrown if the library does not exist
     * @see java.lang.SecurityManager#checkLink(String)
     */
    @ThreadSafe
    public static void loadLibrary(@Nullable String filename) throws InotifyException {
        lock.lock();
        try {
            if (filename == null) {
                filename = "libinotify-java-noglobal";
                try {
                    loadFromJar(filename); // $NON-NLS-1$
                } catch (final Exception e) {
                    throw new InotifyException(e.getMessage());
                }
            } else {
                System.loadLibrary(filename);
            }
            nativeLibLoaded = true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Extracts the bundled library into a new private temporary directory and loads it. The
     * directory is owner-only and randomly named, and is removed once the library is loaded.
     */
    private static void loadFromJar(final String fileName) throws Exception {
        final var name = fileName + ".so";
        final var dir = Files.createTempDirectory("inotify_");
        final var library = dir.resolve(name);
        try (final var in = NativeInotify.class.getResourceAsStream(LIB + name)) {
            if (in == null) {
                throw new FileNotFoundException("Bundled library " + LIB + name + " not found");
            }
            Files.copy(in, library);
            System.load(library.toString());
        } finally {
            // A loaded library stays mapped after its file is deleted.
            Files.deleteIfExists(library);
            Files.delete(dir);
        }
    }

    /** Invokes {@link #destroy()}. */
    @Override
    public void close() {
        destroy();
    }

    /**
     * Returns {@code true} if this Inotify instance is active, {@code false} if it has been
     * destroyed.
     *
     * <p>This method is thread-safe and guarantees the returned value is accurate at the time of
     * invocation.
     *
     * @return {@code true} if this Inotify instance is active, {@code false} if it has been
     *     destroyed
     * @see #isInactive()
     * @see #destroy()
     */
    @ThreadSafe
    public final boolean isActive() {
        activeSem.acquireUninterruptibly();
        final var active = fdActive;
        activeSem.release();
        return active;
    }

    /**
     * Returns {@code true} if this Inotify instance has been destroyed, {@code false} if it is
     * active.
     *
     * <p>This method is thread-safe and guarantees the returned value is accurate at the time of
     * invocation.
     *
     * @return {@code true} if this Inotify instance has been destroyed, {@code false} if it is
     *     active
     * @see #isActive()
     * @see #destroy()
     */
    @ThreadSafe
    public final boolean isInactive() {
        activeSem.acquireUninterruptibly();
        final var inactive = !fdActive;
        activeSem.release();
        return inactive;
    }

    /**
     * Returns the descriptor associated with this Inotify instance.
     *
     * @return int
     */
    public final int getFileDescriptor() {
        return fileDescriptor;
    }

    /**
     * Destroys the underlying native instance, thus freeing allocated native resources. This method
     * is a no-op if the {@link ch.uetlib.inotify.NativeInotify NativeInotify} object is not active.
     *
     * @see #isActive()
     * @see #isInactive()
     */
    public void destroy() {
        activeSem.acquireUninterruptibly();
        try {
            if (fdActive) {
                close(fileDescriptor);
                fdActive = false;
            }
        } finally {
            activeSem.release();
        }
    }

    /**
     * Adds a new watch, or modifies an existing watch for the file or directory whose location is
     * specified by the {@code path} argument. The events to be monitored are specified by the
     * bit-mask, {@code constantMask}.
     *
     * @param path Path to watch
     * @param constantMask Mask of events
     * @return int The unique watch descriptor associated with the specified {@code path} argument.
     *     The watch descriptor is later returned from received events, indicating file system
     *     events.
     * @throws InotifyException Thrown if the native inotify object could not be constructed. The
     *     cause of the exception will be provided.
     * @see #removeWatch(int)
     */
    int addWatch(final String path, final int constantMask) throws InotifyException {
        if (isInactive()) {
            throw new UnsupportedOperationException("not active");
        }
        if (path == null) {
            throw new IllegalArgumentException("path cannot be null");
        }

        try {
            return add_watch(fileDescriptor, path, constantMask);
        } catch (final UserWatchLimitException e) {
            throw new InotifyException("user limit reached", e);
        }
    }

    /**
     * Removes an item from the watch list by the specified watch descriptor {@code wd}.
     *
     * @param wd Unique integer descriptor returned by the {@link #addWatch} method.
     * @return {@code true} if the watch was successfully removed, {@code false}
     * @throws InvalidWatchDescriptorException Thrown when the watch descriptor is not valid for
     *     this {@code Inotify} instance
     * @see #addWatch(String, int)
     */
    boolean removeWatch(final int wd) throws InvalidWatchDescriptorException {
        if (isInactive()) {
            throw new UnsupportedOperationException("not active");
        }

        return rm_watch(fileDescriptor, wd) == 0;
    }

    /**
     * Services the native event queue until this this object is destroyed.
     *
     * @see #destroy()
     */
    void read() {
        if (isInactive()) {
            return;
        }
        read(fileDescriptor);
    }

    /**
     * Receives an Inotify event from the native interface.
     *
     * @param e InotifyEvent from the native interface
     */
    abstract void eventHandler(InotifyEvent e);

    /**
     * Invokes the inotify_init native routine, which in turn initializes a native instance.
     *
     * @return int File descriptor associated with a new inotify event queue.
     * @throws InsufficientKernelMemoryException Checked exception thrown to indicate an
     *     insufficient amount of kernel memory is available for a new native inotify instance.
     * @throws SystemLimitException Checked exception thrown to indicate the system limit of total
     *     file descriptors has been reached.
     * @throws UserInstanceLimitException Checked exception thrown to indicate the maximum number of
     *     inotify instances has been reached.
     */
    private native int init()
            throws InsufficientKernelMemoryException,
                    SystemLimitException,
                    UserInstanceLimitException;

    /** Destroys the underlying inotify instance by closing the file descriptor. */
    private native void close(int fd);

    /**
     * Invokes the inotify_add_watch native routine, adding to or modifying an existing watch.
     *
     * @param fd The fd argument is a file descriptor referring to the inotify instance whose watch
     *     list is to be modified.
     * @param path Path to be watched.
     * @param mask Bit-mask argument
     * @return int Returns the unique watch descriptor associated with the path for this Inotify
     *     instance. If path was not previously being watched by this Inotify instance, then the
     *     watch descriptor is newly allocated. If path was already being watched, then the
     *     descriptor for the existing watch is returned.
     * @throws UserWatchLimitException Checked exception thrown to indicate the maximum number of
     *     inotify watches has been reached.
     */
    private native int add_watch(int fd, String path, int mask) throws UserWatchLimitException;

    /**
     * @param fd The fd argument is a file descriptor referring to the Inotify instance whose watch
     *     list is to be modified.
     * @param wd The wd argument is the watch descriptor to be removed.
     * @return int Returns zero on success or -1 if an error occurred.
     */
    private native int rm_watch(int fd, int wd);

    /**
     * Reads the available events from the inotify event queue.
     *
     * @param fd File descriptor referring to the inotify instance whose queue is to be read.
     */
    private native void read(int fd);

    /**
     * Sets the read and write file descriptors of the IPC pipe associated with this {@link
     * ch.uetlib.inotify.NativeInotify NativeInotify} object.
     *
     * @param readEnd File descriptor of pipe read end
     * @param writeEnd File descriptor of pipe write end
     */
    public void setPipes(final int readEnd, final int writeEnd) {
        pipes[0] = readEnd;
        pipes[1] = writeEnd;
    }

    /**
     * Gets the file descriptor of the read end of the IPC pipe.
     *
     * @return IPC pipe read end file descriptor
     */
    public int getPipeRead() {
        return pipes[0];
    }

    /**
     * Gets the file descriptor of the write end of the IPC pipe.
     *
     * @return IPC pipe write end file descriptor
     */
    public int getPipeWrite() {
        return pipes[1];
    }
}
