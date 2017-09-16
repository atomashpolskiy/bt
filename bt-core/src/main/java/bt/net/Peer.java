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

package bt.net;

import bt.peer.PeerOptions;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Optional;

/**
 * Represents a peer, accessible on the Internet.
 *
 * @since 1.0
 */
public interface Peer {

    /**
     * @since 1.2
     */
    InetSocketAddress getInetSocketAddress();

    /**
     * @return Peer Internet address.
     * @since 1.0
     */
    InetAddress getInetAddress();

    /**
     * @return Peer port.
     * @since 1.0
     */
    int getPort();

    /**
     * @return Optional peer ID
     * @since 1.0
     */
    Optional<PeerId> getPeerId();

    /**
     * @return Peer options and preferences
     * @since 1.2
     */
    PeerOptions getOptions();
}
