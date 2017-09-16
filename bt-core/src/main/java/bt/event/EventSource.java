/*
 * Copyright (c) 2016—2017 Andrei Tomashpolskiy and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package bt.event;

import java.util.function.Consumer;

/**
 * Provides API for hooking into the stream of runtime events.
 *
 * @since 1.5
 */
public interface EventSource {

    /**
     * Fired, when a new peer has been discovered for some torrent.
     *
     * @since 1.5
     */
    EventSource onPeerDiscovered(Consumer<PeerDiscoveredEvent> listener);

    /**
     * Fired, when a new connection with some peer has been established.
     *
     * @since 1.5
     */
    EventSource onPeerConnected(Consumer<PeerConnectedEvent> listener);

    /**
     * Fired, when a connection with some peer has been terminated.
     *
     * @since 1.5
     */
    EventSource onPeerDisconnected(Consumer<PeerDisconnectedEvent> listener);

    /**
     * Fired, when local information about some peer's data has been updated.
     *
     * @since 1.5
     */
    EventSource onPeerBitfieldUpdated(Consumer<PeerBitfieldUpdatedEvent> listener);

    /**
     * Fired, when processing of some torrent has begun.
     *
     * @since 1.5
     */
    EventSource onTorrentStarted(Consumer<TorrentStartedEvent> listener);

    /**
     * Fired, when processing of some torrent has finished.
     *
     * @since 1.5
     */
    EventSource onTorrentStopped(Consumer<TorrentStoppedEvent> listener);
}
