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

import bt.BtException;
import bt.data.digest.Digester;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

public class DefaultChunkVerifier implements ChunkVerifier {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultChunkVerifier.class);

    private final Digester digester;
    private final int numOfHashingThreads;

    public DefaultChunkVerifier(Digester digester, int numOfHashingThreads) {
        this.digester = digester;
        this.numOfHashingThreads = numOfHashingThreads;
    }

    @Override
    public boolean verify(List<ChunkDescriptor> chunks, LocalBitfield bitfield) {
        if (chunks.size() != bitfield.getPiecesTotal()) {
            throw new IllegalArgumentException("Bitfield has different size than the list of chunks. Bitfield size: " +
                    bitfield.getPiecesTotal() + ", number of chunks: " + chunks.size());
        }

        collectParallel(chunks, bitfield);

        return bitfield.getPiecesRemaining() == 0;
    }

    @Override
    public boolean verify(ChunkDescriptor chunk) {
        byte[] expected = chunk.getChecksum();
        byte[] actual = digester.digestForced(chunk.getData());
        return Arrays.equals(expected, actual);
    }

    @Override
    public boolean verifyIfPresent(ChunkDescriptor chunk) {
        return verifyIfPresent(chunk, digester.createCopy());
    }

    private static boolean verifyIfPresent(ChunkDescriptor chunk, Digester localDigester) {
        byte[] expected = chunk.getChecksum();
        byte[] actual = localDigester.digest(chunk.getData());
        return Arrays.equals(expected, actual);
    }

    private void collectParallel(List<ChunkDescriptor> chunks, LocalBitfield bitfield) {
        int n = numOfHashingThreads;

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Verifying torrent data with {} workers", n);
        }

        if(n > 1) {
            ForkJoinPool pool = new ForkJoinPool(n);
            try {
                pool.submit(() -> verifyChunks(chunks, bitfield, true)).get();
            } catch (Exception ex) {
                throw new BtException("Failed to verify torrent data:" +
                        errorToString(ex));
            } finally {
                pool.shutdownNow();
            }
        } else {
            verifyChunks(chunks, bitfield, n < 1);
        }
    }

    /**
     * Returns the integer indices of chunks that are verified
     * @param chunks the chunks to verify
     * @param bitfield the bitfield to mark a chunk as verified
     * @param parallel whether to use a parallel stream
     */
    private void verifyChunks(List<ChunkDescriptor> chunks, LocalBitfield bitfield, boolean parallel) {
        IntStream stream = IntStream.range(0, chunks.size()).unordered();
        if (parallel)
            stream = stream.parallel();
        final Digester localDigester = digester.createCopy();
        stream.filter(i -> this.checkIfChunkVerified(chunks.get(i), localDigester))
                .forEach(bitfield::markLocalPieceVerified);
    }

    private static boolean checkIfChunkVerified(ChunkDescriptor chunk, Digester localDigester) {
        // optimization to speedup the initial verification of torrent's data
        AtomicBoolean containsEmptyFile = new AtomicBoolean(false);
        chunk.getData().visitUnits((u, off, lim) -> {
            // limit of 0 means an empty file,
            // and we don't want to account for those
            if (u.size() == 0 && lim != 0) {
                containsEmptyFile.set(true);
                return false; // no need to continue
            }
            return true;
        });

        // if any of this chunk's storage units is empty,
        // then the chunk is neither complete nor verified
        if (!containsEmptyFile.get()) {
            return verifyIfPresent(chunk, localDigester);
        }
        return false;
    }

    private String errorToString(Throwable e) {
        StringBuilder buf = new StringBuilder();
        buf.append("\n");

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bos);
        e.printStackTrace(out);

        buf.append(bos.toString());
        return buf.toString();
    }
}
