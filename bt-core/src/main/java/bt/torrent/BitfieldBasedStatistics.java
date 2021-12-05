/*
 * Copyright (c) 2016—2021 Andrei Tomashpolskiy and individual contributors.
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

package bt.torrent;

import bt.data.Bitfield;
import bt.data.PeerBitfield;
import bt.net.ConnectionKey;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicIntegerArray;

/**
 * Acts as a storage for peers' bitfields and provides aggregate piece statistics.
 * This class is thread-safe.
 *
 * @since 1.0
 */
public class BitfieldBasedStatistics implements PieceStatistics {

    private final Bitfield localBitfield;
    private final ConcurrentMap<ConnectionKey, PeerBitfield> peerBitfields;
    private final AtomicIntegerArray pieceTotals;

    /**
     * Create statistics, based on the local peer's bitfield.
     *
     * @since 1.0
     */
    public BitfieldBasedStatistics(Bitfield localBitfield) {
        this.localBitfield = localBitfield;
        this.peerBitfields = new ConcurrentHashMap<>();
        this.pieceTotals = new AtomicIntegerArray(localBitfield.getPiecesTotal());
    }

    /**
     * Add peer's bitfield.
     * For each piece, that the peer has, total count will be incremented by 1.
     *
     * @since 1.0
     */
    public void addBitfield(ConnectionKey connectionKey, PeerBitfield bitfield) {
        validateBitfieldLength(bitfield);
        peerBitfields.put(connectionKey, bitfield);
        bitfield.forEachVerifiedPiece(pieceTotals::incrementAndGet);
    }

    /**
     * Remove peer's bitfield.
     * For each piece, that the peer has, total count will be decremented by 1.
     *
     * @since 1.0
     */
    public void removeBitfield(ConnectionKey connectionKey) {
        PeerBitfield bitfield = peerBitfields.remove(connectionKey);
        if (bitfield == null) {
            return;
        }

        bitfield.forEachVerifiedPiece(pieceTotals::decrementAndGet);
    }

    private void validateBitfieldLength(Bitfield bitfield) {
        if (bitfield.getPiecesTotal() != pieceTotals.length()) {
            throw new IllegalArgumentException("Bitfield has invalid length (" + bitfield.getPiecesTotal() +
                    "). Expected number of pieces: " + pieceTotals.length());
        }
    }

    /**
     * Update peer's bitfield by indicating that the peer has a given piece.
     * Total count of the specified piece will be incremented by 1.
     *
     * @since 1.0
     */
    public void addPiece(ConnectionKey connectionKey, Integer pieceIndex) {
        PeerBitfield bitfield = peerBitfields.computeIfAbsent(connectionKey, key -> new PeerBitfield(localBitfield.getPiecesTotal()));
        markPieceVerified(bitfield, pieceIndex);
    }

    private void markPieceVerified(PeerBitfield bitfield, Integer pieceIndex) {
        if (bitfield.markPeerPieceVerified(pieceIndex)) {
            pieceTotals.getAndIncrement(pieceIndex);
        }
    }

    /**
     * Get peer's bitfield, if present.
     *
     * @since 1.0
     */
    public Optional<PeerBitfield> getPeerBitfield(ConnectionKey connectionKey) {
        return Optional.ofNullable(peerBitfields.get(connectionKey));
    }

    @Override
    public int getCount(int pieceIndex) {
        // in Java 9+, this can be changed to getOpaque()
        return pieceTotals.get(pieceIndex);
    }

    @Override
    public int getPiecesTotal() {
        return pieceTotals.length();
    }
}
