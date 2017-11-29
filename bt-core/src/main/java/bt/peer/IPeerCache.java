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

import java.net.InetSocketAddress;

/**
 * @since 1.6
 */
public interface IPeerCache {

    /**
     * Create a new entry or update an existing one for the given peer.
     *
     * @return New or existing entry
     * @since 1.6
     */
    Peer store(Peer peer);

    /**
     * Get peer for an internet address
     * or create and return a new one with default options, if it does not exist.
     *
     * @since 1.2
     */
    Peer getPeerForAddress(InetSocketAddress address);
}
