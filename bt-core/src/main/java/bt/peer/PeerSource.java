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

package bt.peer;

import bt.net.Peer;

import java.util.Collection;

/**
 * @since 1.0
 */
public interface PeerSource {

    /**
     * Ask to update the list of peers (usually from an external source).
     * Implementations may choose to refuse to perform an update under certain conditions,
     * e.g. when insufficient time has passed since the last update.
     *
     * @return true if the list of peers has been updated; false otherwise
     * @since 1.0
     */
    boolean update();

    /**
     * Get the list of peers.
     * Implementations should return empty list before {@link #update} has been called for the first time.
     *
     * @return List of peers
     * @since 1.0
     */
    Collection<Peer> getPeers();
}
