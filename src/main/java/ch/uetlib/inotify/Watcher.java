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
 * <p>File: Watcher.java Project: inotify-java Package: ch.uetlib.inotify Author: Nick Bargnesi
 */
package ch.uetlib.inotify;

import static java.util.Objects.requireNonNull;

import ch.uetlib.inotify.enums.Event;
import ch.uetlib.inotify.exceptions.InotifyException;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Semaphore;

/**
 * The {@code Watcher} class is a simple manner in which directories and files can be watched. It is
 * only associated with a single path and event type.
 *
 * <p>This class provides a number of ways to be notified of the event:
 *
 * <dl>
 *   <dt>{@link ch.uetlib.inotify.InotifyEventListener Listener}
 *   <dd>A listener (or set of listeners) will be notified of events via {@link
 *       ch.uetlib.inotify.InotifyEventListener#filesystemEventOccurred the notification method}.
 *   <dt>{@link java.lang.Runnable Runnable}
 *   <dd>Invoking the {@link java.lang.Runnable#run() run method} of a {@link java.lang.Runnable
 *       Runnable}.
 *   <dt>{@link java.util.concurrent.CyclicBarrier CyclicBarrier}
 *   <dd>Invoking await on a {@link java.util.concurrent.CyclicBarrier CyclicBarrier}.
 *   <dt>{@link java.util.concurrent.CountDownLatch}
 *   <dd>Invoking await on a {@code java.util.concurent.CountDownLatch CountDownLatch}. The one-shot
 *       nature of the latch is inherited by the {@code Watcher} class. Successive events will not
 *       cause a <em>count down</em> of the provided latch.
 *   <dt>{@link java.util.concurrent.Semaphore Semaphore}
 *   <dd>Acquiring a permit provided by the {@code Watcher} with a {@link
 *       java.util.concurrent.Semaphore semaphore}.
 * </dl>
 *
 * <p>Each notification mechanism supports {@link ch.uetlib.inotify.InotifyEventListener listeners}
 * as well. However, no guarantee is made that listeners will be notified of events in a
 * deterministic manner when using a non-listener-based model. It is reasonable to assume a listener
 * may miss generated events, due to the execution of the object's constructor, followed by a
 * successive {@link #addListener(InotifyEventListener)} invocation.
 *
 * @author Nick Bargnesi
 * @since Version 2
 */
public class Watcher extends ConcurrentReader {

    private String pathName;
    private Event eventType;

    /** Barrier when operating in barrier mode. */
    protected @Nullable CyclicBarrier barrier;

    /** Latch when operating in latch mode. */
    protected @Nullable CountDownLatch latch;

    /** Semaphore when operating in semaphore mode. */
    protected @Nullable Semaphore semaphore;

    /** List of listeners to notify of filesystem events. */
    protected @Nullable List<InotifyEventListener> listeners;

    /** Enumeration definition defining mode definitions. */
    protected static enum Mode {
        /** Listener mode enumeration. */
        LISTENER,

        /** Runnable mode enumeration. */
        RUNNABLE,

        /** Barrier mode enumeration. */
        BARRIER,

        /** Latch mode enumeration. */
        LATCH,

        /** Semaphore mode enumeration. */
        SEMAPHORE;
    }

    /** The watcher's mode. */
    protected Mode mode;

    /**
     * Creates a watcher for the supplied path <em>p</em> and event type <em>ev</em>.
     *
     * @param path Path to watch
     * @param ev Event to watch for
     * @throws InotifyException Thrown when the watcher could not be created; a cause will be
     *     provided to the thrown object.
     * @see ch.uetlib.inotify.enums.Event
     */
    public Watcher(final String path, final Event ev) throws InotifyException {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("invalid path");
        }
        mode = Mode.LISTENER;
        init(path, ev);
    }

    /**
     * Creates a watcher for the supplied path <em>p</em>, event type <em>ev</em>, and listener
     * <em>l</em>
     *
     * @param path Path to watch
     * @param ev Event to watch for
     * @param l InotifyEventListener to receive events
     * @throws InotifyException Thrown when the watcher could not be created; a cause will be
     *     provided to the thrown object.
     * @see ch.uetlib.inotify.enums.Event
     */
    public Watcher(final String path, final Event ev, final InotifyEventListener l)
            throws InotifyException {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("invalid path");
        }
        mode = Mode.LISTENER;
        addListener(l);
        init(path, ev);
    }

    /**
     * Creates a watcher for the supplied path <em>p</em> and event type <em>ev</em>. The provided
     * {@link java.lang.Runnable runnable} will be invoked when filesystem events occur.
     *
     * @param path Path to watch
     * @param ev Event to watch for
     * @param r Runnable to invoke when filesystem events occur
     * @throws InotifyException Thrown when the watcher could not be created; a cause will be
     *     provided to the thrown object.
     */
    public Watcher(final String path, final Event ev, final Runnable r) throws InotifyException {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("invalid path");
        }
        if (r == null) {
            throw new IllegalArgumentException("invalid runnable");
        }
        mode = Mode.RUNNABLE;
        barrier = new CyclicBarrier(1, r);
        init(path, ev);
    }

    /**
     * Creates a watcher for the supplied path <em>p</em> and event type <em>ev</em>. The provided
     * {@link java.util.concurrent.CyclicBarrier barrier} will be waited on when filesystem events
     * occur.
     *
     * @param path Path to watch
     * @param ev Event to watch for
     * @param b CyclicBarrier providing a manner for interested parties to reach a common barrier
     *     point with the watcher and filesystem events as they occur.
     * @throws InotifyException Thrown when the watcher could not be created; a cause will be
     *     provided to the thrown object.
     */
    public Watcher(final String path, final Event ev, final CyclicBarrier b)
            throws InotifyException {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("invalid path");
        }
        if (b == null) {
            throw new IllegalArgumentException("invalid barrier");
        }
        mode = Mode.BARRIER;
        barrier = b;
        init(path, ev);
    }

    /**
     * Creates a watcher for the supplied path <em>p</em> and event type <em>ev</em>. The provided
     * {@link java.util.concurrent.CountDownLatch latch} will be counted down when filesystem events
     * occurred.
     *
     * @param path Path to watch
     * @param ev Event to watch for
     * @param c CountDownLatch providing a one-shot synchronization aid to watcher users
     * @throws InotifyException Thrown when the watcher could not be created; a cause will be
     *     provided to the thrown object.
     */
    public Watcher(final String path, final Event ev, final CountDownLatch c)
            throws InotifyException {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("invalid path");
        }
        if (c == null) {
            throw new IllegalArgumentException("invalid latch");
        }
        mode = Mode.LATCH;
        latch = c;
        init(path, ev);
    }

    /**
     * Creates a watcher for the supplied path <em>p</em> and event type <em>ev</em>. A permit will
     * be obtained from the {@link java.util.concurrent.Semaphore semaphore} when filesystem events
     * occur.
     *
     * @param path Path to watch
     * @param ev Event to watch for
     * @param s Semaphore to release permits on, when filesystem events occur
     * @throws InotifyException Thrown when the watcher could not be created; a cause will be
     *     provided to the thrown object.
     */
    public Watcher(final String path, final Event ev, final Semaphore s) throws InotifyException {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("invalid path");
        }
        if (s == null) {
            throw new IllegalArgumentException("invalid semaphore");
        }
        mode = Mode.SEMAPHORE;
        semaphore = s;
        init(path, ev);
    }

    private void init(final String path, final Event ev) throws InotifyException {
        this.pathName = path;
        this.eventType = ev;
        addWatch(pathName, ev.value());
    }

    /**
     * Adds the specified {@link ch.uetlib.inotify.InotifyEventListener listener} to be notified of
     * events.
     *
     * <p>This method is not thread-safe. No guarantee is provided for the resulting set of
     * listeners when this method is invoked in a non-thread-safe manner.
     *
     * @param l InotifyEventListener - null listeners are ignored
     */
    @NonThreadSafe
    public void addListener(final InotifyEventListener l) {
        if (l != null) {
            if (listeners == null) {
                listeners = new ArrayList<>();
            }
            listeners.add(l);
        }
    }

    /**
     * Removes the first occurrence of the specified {@link ch.uetlib.inotify.InotifyEventListener
     * listener} from the list of event listeners.
     *
     * @param l InotifyEventListener - null listeners are ignored
     * @see java.util.List#remove(Object)
     */
    public void removeListener(final InotifyEventListener l) {
        if (l != null) {
            if (listeners != null) {
                listeners.remove(l);
            }
        }
    }

    /**
     * Removes all {@link ch.uetlib.inotify.InotifyEventListener listeners} from the list of event
     * listeners.
     */
    public void removeAll() {
        if (listeners != null) {
            listeners.clear();
        }
    }

    /**
     * Returns the path being watched.
     *
     * @return String
     */
    public String getPath() {
        return pathName;
    }

    /**
     * Returns the event type being watched.
     *
     * @return Event
     */
    public Event getEvent() {
        return eventType;
    }

    /**
     * Receives an Inotify event.
     *
     * @param e InotifyEvent
     */
    @Override
    void eventHandler(final InotifyEvent e) {
        if (e.getName() != null) {
            if (pathName.charAt(pathName.length() - 1) == '/') {
                e.setContextualName(pathName + e.getName());
            } else {
                e.setContextualName(pathName + '/' + e.getName());
            }
        }

        switch (mode) {
            case LISTENER -> {
                // A watcher constructed without a listener has none until addListener is called.
                final var current = listeners;
                if (current != null) {
                    for (final var l : current) {
                        l.filesystemEventOccurred(e);
                    }
                }
                return;
            }
            case RUNNABLE, BARRIER -> {
                try {
                    requireNonNull(barrier, "barrier").await();
                } catch (final InterruptedException ie) {
                    destroy();
                    final var uoe = new UnsupportedOperationException("watcher destroyed");
                    uoe.initCause(ie);
                    throw uoe;
                } catch (final BrokenBarrierException bbe) {
                    destroy();
                    final var uoe = new UnsupportedOperationException("watcher destroyed");
                    uoe.initCause(bbe);
                    throw uoe;
                }
            }
            case LATCH -> requireNonNull(latch, "latch").countDown();
            case SEMAPHORE -> requireNonNull(semaphore, "semaphore").release();
        }

        final var current = listeners;
        if (mode != Mode.LISTENER && current != null) {
            for (final var l : current) {
                l.filesystemEventOccurred(e);
            }
        }
    }

    /**
     * Returns a string representation of the watcher.
     *
     * @return a string representation of the watcher
     */
    @Override
    public String toString() {
        final var s = new StringBuilder();
        s.append(getClass().getSimpleName());

        s.append(" [mode=");
        switch (mode) {
            case BARRIER -> s.append(Mode.BARRIER);
            case LATCH -> s.append(Mode.LATCH);
            case LISTENER -> s.append(Mode.LISTENER);
            case RUNNABLE -> s.append(Mode.RUNNABLE);
            case SEMAPHORE -> s.append(Mode.SEMAPHORE);
        }

        s.append(", fd=");
        s.append(fileDescriptor);

        s.append(", pathName=");
        s.append(pathName);

        s.append(", eventType=");
        s.append(eventType);

        if (listeners != null) {
            s.append(", listeners=");
            s.append(listeners.size());
        }

        s.append("]");

        return s.toString();
    }

    /**
     * Returns the number of listeners.
     *
     * @return Number of listeners
     */
    int numberOfListeners() {
        if (listeners != null) {
            return listeners.size();
        }
        return 0;
    }
}
