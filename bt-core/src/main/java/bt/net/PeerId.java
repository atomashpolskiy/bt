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

import bt.BtException;

import java.util.Arrays;
import java.util.Objects;

/**
 * Convenient wrapper, that encapsulates a binary peer ID.
 *
 * @since 1.0
 */
public class PeerId {

    private static final int PEER_ID_LENGTH = 20;

    /**
     * @return Standrad peer ID length in BitTorrent.
     * @since 1.0
     */
    public static int length() {
        return PEER_ID_LENGTH;
    }

    /**
     * Deserialize a binary peer ID representation.
     *
     * @since 1.0
     */
    public static PeerId fromBytes(byte[] bytes) {
        return new PeerId(bytes);
    }

    private final byte[] peerId;

    private PeerId(byte[] peerId) {
        Objects.requireNonNull(peerId);
        if (peerId.length != PEER_ID_LENGTH) {
            throw new BtException("Illegal peer ID length: " + peerId.length);
        }
        this.peerId = peerId;
    }

    /**
     * @return Binary peer ID representation.
     */
    public byte[] getBytes() {
        return peerId;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(peerId);
    }

    @Override
    public boolean equals(Object obj) {

        if (obj == null || !PeerId.class.isAssignableFrom(obj.getClass())) {
            return false;
        }
        return (obj == this) || Arrays.equals(peerId, ((PeerId) obj).getBytes());
    }

    @Override
    public String toString() {
        return Arrays.toString(peerId);
    }
}
