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

import bt.BtException;
import bt.metainfo.TorrentId;
import bt.net.PeerId;

/**
 * Standard handshake message.
 * This is the very first message that peers must send,
 * when initializing a new peer connection.
 * <p>
 * Handshake message includes:
 * - a constant header, specified in standard BitTorrent protocol
 * - torrent ID
 * - peer ID
 * - 8 reserved bytes, that are used by extensions, e.g. BEP-10: Extension Protocol
 *
 * @since 1.0
 */
public final class Handshake implements Message {

    // Note: these are listed in little endian, so they are equal to 63-the reserved number listed in the BEPs
    private static final int EXTENDED_FLAG_BIT_INDEX = 43;
    private static final int DHT_FLAG_BIT_INDEX = 63;

    private static final int UPPER_RESERVED_BOUND = 8 * 8 - 1;

    private final byte[] reserved;
    private final TorrentId torrentId;
    private final PeerId peerId;

    /**
     * @since 1.0
     */
    public Handshake(byte[] reserved, TorrentId torrentId, PeerId peerId) throws InvalidMessageException {
        this.reserved = reserved;
        this.torrentId = torrentId;
        this.peerId = peerId;
    }

    /**
     * Check if a reserved bit is set.
     *
     * @param bitIndex Index of a bit to check (0..63 inclusive)
     * @return true if this bit is set to 1
     * @since 1.0
     */
    public boolean isReservedBitSet(int bitIndex) {
        return Protocols.getBit(reserved, BitOrder.LITTLE_ENDIAN, bitIndex) == 1;
    }

    /**
     * Set a reserved bit.
     *
     * @param bitIndex Index of a bit to set (0..63 inclusive)
     * @since 1.0
     */
    public void setReservedBit(int bitIndex) {
        if (bitIndex < 0 || bitIndex > UPPER_RESERVED_BOUND) {
            throw new BtException("Illegal bit index: " + bitIndex +
                    ". Expected index in range [0.." + UPPER_RESERVED_BOUND + "]");
        }
        Protocols.setBit(reserved, BitOrder.LITTLE_ENDIAN, bitIndex);
        // check range
    }

    /**
     * Check whether the extension protocol is supported (BEP-0010)
     *
     * @return whether the extension protocol is supported (BEP-0010)
     */
    public boolean supportsExtensionProtocol() {
        return isReservedBitSet(EXTENDED_FLAG_BIT_INDEX);
    }

    /**
     * Mark the extension protocol as supported
     */
    public void setSupportsExtensionProtocol() {
        setReservedBit(EXTENDED_FLAG_BIT_INDEX);
    }

    /**
     * Check if the DHT extension is supported
     *
     * @return whether DHT extension is supported
     */
    public boolean isDHTSupported() {
        return isReservedBitSet(DHT_FLAG_BIT_INDEX);
    }

    /**
     * Mark the DHT extension as supported
     */
    public void setDHTSupported() {
        setReservedBit(DHT_FLAG_BIT_INDEX);
    }

    /**
     * @since 1.0
     */
    public byte[] getReserved() {
        return reserved;
    }

    /**
     * @since 1.0
     */
    public TorrentId getTorrentId() {
        return torrentId;
    }

    /**
     * @since 1.0
     */
    public PeerId getPeerId() {
        return peerId;
    }

    @Override
    public String toString() {
        return "[" + this.getClass().getSimpleName() + "]";
    }

    @Override
    public Integer getMessageId() {
        throw new UnsupportedOperationException();
    }
}
