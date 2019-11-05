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
import java.util.Optional;

/**
 * Represents a peer, accessible on the Internet.
 *
 * @since 1.0
 */
public interface Peer {

    /**
     * @return Peer Internet address.
     * @since 1.0
     */
    InetAddress getInetAddress();

    /**
     * @return true, if the peer's listening port is not known yet
     * @see #getPort()
     * @since 1.9
     */
    boolean isPortUnknown();

    /**
     * @return Peer's listening port or {@link InetPeer#UNKNOWN_PORT}, if it's not known yet
     *         (e.g. when the connection is incoming and the remote side hasn't
     *         yet communicated to us its' listening port via extended handshake)
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
