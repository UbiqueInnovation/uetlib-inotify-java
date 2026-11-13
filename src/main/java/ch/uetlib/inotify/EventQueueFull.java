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
 * <p>File: EventQueueFull.java Project: inotify-java Package: ch.uetlib.inotify Author: Nick
 * Bargnesi
 */
package ch.uetlib.inotify;

import java.util.EventObject;

/**
 * EventQueueFull is an event notifying listeners that an event queue is full.
 *
 * @author Nick Bargnesi
 */
public class EventQueueFull extends EventObject {

    /**
     * Creates a new event queue full from the specific file descriptor.
     *
     * @param fileDescriptor The file descriptor associated with an inotify event queue.
     */
    public EventQueueFull(final int fileDescriptor) {
        super(fileDescriptor);
    }
}
