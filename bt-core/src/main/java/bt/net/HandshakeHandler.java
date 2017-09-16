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

import bt.protocol.Handshake;

/**
 * Extension point for additional processing of incoming and outgoing handshakes.
 *
 * @since 1.0
 */
public interface HandshakeHandler {

    /**
     * Process an incoming handshake, received from a remote peer.
     * Implementations are free to send messages via the provided connection
     * and may choose to close it if some of their expectations about the handshake are not met.
     *
     * Attempt to read from the provided connection will trigger an {@link UnsupportedOperationException}.
     *
     * @since 1.1
     */
    void processIncomingHandshake(PeerConnection connection, Handshake peerHandshake);

    /**
     * Make amendments to an outgoing handshake, that will be sent to a remote peer.
     *
     * @since 1.0
     */
    void processOutgoingHandshake(Handshake handshake);
}
