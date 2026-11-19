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

import ch.uetlib.inotify.enums.Event;
import ch.uetlib.inotify.enums.WatchModifier;
import ch.uetlib.inotify.exceptions.InotifyException;
import ch.uetlib.inotify.exceptions.InvalidWatchDescriptorException;

import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * The {@code Inotify} class is an interface to the inotify API on Linux.
 *
 * <p>This class supports all of the functions available through the inotify API and provides two
 * additional capabilities.
 *
 * <ol>
 *   <li>Users of this class may direct events concerning specific paths to any and all interested
 *       parties.
 *   <li>Contextual naming information is included with events sent to listeners. In effect, for a
 *       watched directory: {@code /path/x}, if an event occurs for file {@code y}, the {@link
 *       InotifyEvent#getContextualName() contextual name} associated with this event will be {@code
 *       /path/x/y}.
 * </ol>
 *
 * <p>This class is not thread-safe. For a thread-safe implementation, consider the {@link
 * MonitorService}.
 *
 * @author Nick Bargnesi
 * @since Version 2
 */
public class Inotify extends ConcurrentReader {

    /** Map of watch descriptors to paths. */
    protected Map<Integer, String> watchPathMap;

    /** Map of paths to watch descriptors. */
    protected Map<String, Integer> pathWatchMap;

    /** Map of watch descriptors to event listeners. */
    protected Map<Integer, Set<InotifyEventListener>> watchListenerMap;

    /**
     * Creates a new inotify instance, throwing an {@link InotifyException} on errors.
     *
     * @throws InotifyException Thrown when an instance could not be created; a cause will be
     *     provided to the thrown object.
     */
    public Inotify() throws InotifyException {
        watchPathMap = new TreeMap<>();
        pathWatchMap = new TreeMap<>();
        watchListenerMap = new TreeMap<>();
    }

    /**
     * Receives an Inotify event, notifies any necessary listeners, and performs any necessary
     * cleanup in the maps maintained by the class.
     *
     * @param e InotifyEvent
     */
    @Override
    void eventHandler(final InotifyEvent e) {
        String path = null;
        if (e.getName() != null) {
            path = watchPathMap.get(e.getSource());
            if (path != null) {
                if (path.charAt(path.length() - 1) == '/') {
                    e.setContextualName(path + e.getName());
                } else {
                    e.setContextualName(path + '/' + e.getName());
                }
            }
        }

        final int wd = e.getSource();
        final var queue = watchListenerMap.get(wd);
        if (queue != null) {
            for (final var l : queue) {
                l.filesystemEventOccurred(e);
            }
        }

        if (e.isIgnored()) {
            if (path == null) {
                path = watchPathMap.get(wd);
            }
            watchPathMap.remove(wd);
            if (path != null) {
                pathWatchMap.remove(path);
            }
            watchListenerMap.remove(wd);
        }
    }

    /**
     * Returns the path being watched for the provided watch descriptor.
     *
     * @param watchDescriptor Watch descriptor identifying returning path
     * @return Path associated with provided watch descriptor, or null if the watch descriptor is
     *     not valid
     */
    public @Nullable String getPath(final int watchDescriptor) {
        return watchPathMap.get(watchDescriptor);
    }

    /**
     * Returns the watch descriptor identifying the provided path.
     *
     * @param path Path associated with returning watch descriptor
     * @return Watch descriptor identifying provided path, or {@code -1} if the path is not being
     *     watched
     */
    public int getWatchDescriptor(final String path) {
        if (path == null) {
            return -1;
        }
        final var wd = pathWatchMap.get(path);
        if (wd == null) {
            return -1;
        }
        return wd;
    }

    /**
     * Adds the provided event listener as a receiver of {@link InotifyEvent events} for the
     * specified watch descriptor.
     *
     * @param watchDescriptor Watch descriptor identifying path listener is interested in
     * @param listener Listener to be notified of events occurred for the watch descriptor provided
     * @throws IllegalArgumentException Thrown if the provided watch descriptor is invalid
     */
    @NonThreadSafe
    public void addListener(final int watchDescriptor, final InotifyEventListener listener) {
        if (listener == null) {
            throw new NullPointerException("listener may not be null");
        }
        if (!watchPathMap.containsKey(watchDescriptor)) {
            throw new IllegalArgumentException("invalid watch descriptor");
        }

        var val =
                watchListenerMap.computeIfAbsent(watchDescriptor, k -> new CopyOnWriteArraySet<>());
        val.add(listener);
    }

    /**
     * Removes the provided event listener from receiving {@link InotifyEvent events} for the
     * specified watch descriptor.
     *
     * @param watchDescriptor Watch descriptor identifying path listener was interested in
     * @param listener Listener to be removed
     * @return {@code true} if this set contained the listener
     * @throws NullPointerException Thrown if the provided listener is null
     * @throws IllegalArgumentException Thrown if the provided watch descriptor is invalid
     */
    @NonThreadSafe
    public boolean removeListener(final int watchDescriptor, final InotifyEventListener listener) {
        if (listener == null) {
            throw new NullPointerException("listener may not be null");
        }
        if (!watchListenerMap.containsKey(watchDescriptor)) {
            throw new IllegalArgumentException("invalid watch descriptor");
        }

        final var val = watchListenerMap.get(watchDescriptor);
        if (val == null) {
            return false;
        }

        final var ret = val.remove(listener);
        if (val.isEmpty()) {
            watchListenerMap.remove(watchDescriptor);
        }
        return ret;
    }

    /**
     * Adds a watch for the specified path for the provided events.
     *
     * @param path Path to be watched
     * @param events Events to watch for
     * @return Watch descriptor uniquely identifying this watched path
     * @throws InotifyException Thrown if the native inotify object could not be constructed. The
     *     cause of the exception will be provided.
     */
    @NonThreadSafe
    public int addWatch(final String path, final Event... events) throws InotifyException {
        if (path == null) {
            throw new NullPointerException("path may not be null");
        }

        var wm_mask = 0;
        final var ev_mask = Event.eventsToMask(events);
        if (pathWatchMap.containsKey(path)) {
            wm_mask |= WatchModifier.Add.value();
        }

        return add_watch(path, wm_mask, ev_mask);
    }

    /**
     * Adds a watch for the specified path for the provided events and watch modifiers.
     *
     * @param path Path to be watched
     * @param watchModifiers Watch modifiers to augment addition of watch
     * @param events Events to watch for
     * @return Watch descriptor uniquely identifying this watched path
     * @throws InotifyException Thrown if the native inotify object could not be constructed. The
     *     cause of the exception will be provided.
     */
    @NonThreadSafe
    int addWatch(final String path, final WatchModifier[] watchModifiers, final Event[] events)
            throws InotifyException {
        if (path == null) {
            throw new NullPointerException("path may not be null");
        }

        final var wm_mask = WatchModifier.watchModifiersToMask(watchModifiers);
        final var ev_mask = Event.eventsToMask(events);

        return add_watch(path, wm_mask, ev_mask);
    }

    /**
     * Adds a watch for the specified path using the provided watch modifier and event masks.
     *
     * @param path Path to be watched
     * @param watchModifiersMask Watch modifier mask to augment addition of watch
     * @param eventMask Events to watch for
     * @return Watch descriptor uniqely identifying this watched path
     * @throws InotifyException Thrown if the native inotify object could not be constructed. The
     *     cause of the exception will be provided.
     */
    @NonThreadSafe
    int addWatch(final String path, final int watchModifiersMask, final int eventMask)
            throws InotifyException {
        if (path == null) {
            throw new NullPointerException("path may not be null");
        }

        return add_watch(path, watchModifiersMask, eventMask);
    }

    /**
     * Private delegate method for adding watches.
     *
     * @param path Path to watch
     * @param wm_mask Watch modifier mask
     * @param ev_mask Event mask
     * @return watch descriptor
     * @throws InotifyException Thrown if the native inotify object could not be constructed. The
     *     cause of the exception will be provided.
     */
    private int add_watch(final String path, final int wm_mask, final int ev_mask)
            throws InotifyException {
        final var wd = addWatch(path, wm_mask | ev_mask);
        pathWatchMap.put(path, wd);
        watchPathMap.put(wd, path);
        return wd;
    }

    /**
     * Removes the watch associated with the provided watch descriptor.
     *
     * @param watchDescriptor Unique descriptor returned from {@link #addWatch(String, Event...)}
     * @return {@code true} if a watch was removed, {@code false} otherwise
     * @throws InvalidWatchDescriptorException Thrown indicating an error in the watch removal has
     *     occurred
     */
    @Override
    @NonThreadSafe
    boolean removeWatch(final int watchDescriptor) throws InvalidWatchDescriptorException {
        return remove_watch(watchDescriptor);
    }

    /**
     * Private delegate method for removing watches.
     *
     * @return {@code true} if a watch was removed, {@code false} otherwise
     */
    private boolean remove_watch(final int wd) throws InvalidWatchDescriptorException {
        final var path = watchPathMap.get(wd);
        watchPathMap.remove(wd);
        pathWatchMap.remove(path);
        watchListenerMap.remove(wd);
        return super.removeWatch(wd);
    }

    /**
     * Returns a String representation of this Inotify object.
     *
     * @return String representation of this Inotify object
     */
    @Override
    public String toString() {
        return getClass().getSimpleName()
                + " [fd="
                + fileDescriptor
                + ", watchPathMap="
                + watchPathMap
                + ", pathWatchMap="
                + pathWatchMap
                + ", watchListenerMap="
                + watchListenerMap
                + "]";
    }
}
