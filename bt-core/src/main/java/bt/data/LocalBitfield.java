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

package bt.data;

import bt.metainfo.TorrentFile;
import bt.protocol.Protocols;

import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * This class represents a bitfield for a local torrent
 */
public abstract class LocalBitfield extends Bitfield {
    /**
     * Bitmask indicating pieces that should be skipped.
     * If the n-th bit is set, then the n-th piece should be skipped.
     */
    private final AtomicReference<BitSet> skipped = new AtomicReference<>();

    private final CountDownLatch latch;

    // first list is the number of pieces in the torrent. Second list is a list of the files that are referenced
    // by that piece
    private final Optional<List<List<CompletableTorrentFile>>> countdownFiles;

    public LocalBitfield(int piecesTotal,
                         List<List<CompletableTorrentFile>> countdownFiles) {
        super(piecesTotal);
        this.latch = new CountDownLatch(piecesTotal);
        this.countdownFiles = Optional.ofNullable(countdownFiles);
    }

    public void markLocalPieceVerified(int pieceIndex) {
        if (checkAndMarkVerified(pieceIndex)) {
            countdownFiles.ifPresent(cdfList ->
                    cdfList.get(pieceIndex).forEach(
                            cdf -> {
                                if (cdf.countdown()) {
                                    fileFinishedCallback(cdf.getTorrentFile());
                                }
                            })
            );
            latch.countDown();
        }
    }

    public void waitForAllPieces() throws InterruptedException {
        latch.await();
    }

    protected abstract void fileFinishedCallback(TorrentFile tf);

    /**
     * @return Bitmask of skipped pieces.
     * If the n-th bit is set, then the n-th piece
     * should be skipped.
     * @since 1.8
     */
    public BitSet getSkippedBitmask() {
        final BitSet skippedPieces = skipped.get();
        if (skippedPieces == null) {
            return new BitSet(0);
        }

        return Protocols.copyOf(skippedPieces);
    }

    /**
     * Mark pieces to download/skip with the specified {@link BitSet}. If the bit is set, this chunk is downloaded
     * If the bit is unset, the piece is not downloaded
     *
     * @param piecesToSkip the pieces to skip
     * @since 1.10
     */
    public void setSkippedPieces(BitSet piecesToSkip) {
        if (piecesToSkip.isEmpty())
            skipped.set(null);
        else
            skipped.set(Protocols.copyOf(piecesToSkip));
    }

    /**
     * @return Number of pieces that should be skipped
     * @since 1.7
     */
    public int getPiecesSkipped() {
        final BitSet skippedPieces = skipped.get();
        if (skippedPieces == null) {
            return 0;
        }

        return skippedPieces.cardinality();
    }

    /**
     * @return Number of pieces that should NOT be skipped
     * @since 1.7
     */
    public int getPiecesNotSkipped() {
        final BitSet skippedPieces = skipped.get();
        if (skippedPieces == null) {
            return piecesTotal;
        }

        return piecesTotal - skippedPieces.cardinality();
    }

    /**
     * @return Number of pieces that have not been verified and should NOT be skipped.
     * @since 1.0
     */
    public int getPiecesRemaining() {
        final BitSet skippedPieces = skipped.get();
        if (skippedPieces == null) {
            return getPiecesTotal() - getPiecesComplete();
        } else {
            BitSet bitmask = getBitmask();
            bitmask.or(skippedPieces);
            return getPiecesTotal() - bitmask.cardinality();
        }
    }

    /**
     * Mark a piece as not skipped.
     *
     * @since 1.7
     */
    void unskip(int pieceIndex) {
        validatePieceIndex(pieceIndex);

        skipped.getAndUpdate(skippedPieces -> {
            if (skippedPieces == null)
                return null; // piece was already not skipped
            BitSet copy = Protocols.copyOf(skippedPieces);
            copy.clear(pieceIndex);
            return copy;
        });
    }

    public void removeVerifiedPiecesFromBitset(BitSet bitSet) {
        lock.readLock().lock();
        try {
            bitSet.andNot(bitmask);
        } finally {
            lock.readLock().unlock();
        }
    }
}
