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

package bt.protocol;

import bt.net.Peer;

/**
 * Instances of this class contain all necessary information
 * for a message handler to decode a peer's message,
 * and also act as a means to carry message
 * throughout the decoding chain.
 *
 * @since 1.0
 */
public class DecodingContext {

    private Peer peer;
    private Message message;

    /**
     * Create a decoding context for a particular peer.
     *
     * @since 1.0
     */
    public DecodingContext(Peer peer) {
        this.peer = peer;
    }

    /**
     * @since 1.0
     */
    public Peer getPeer() {
        return peer;
    }

    /**
     * @since 1.0
     */
    public Message getMessage() {
        return message;
    }

    /**
     * @since 1.0
     */
    public void setMessage(Message message) {
        this.message = message;
    }
}
