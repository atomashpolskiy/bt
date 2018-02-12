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

package bt.data;

import bt.BtException;
import bt.data.digest.Digester;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class DefaultChunkVerifier implements ChunkVerifier {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultChunkVerifier.class);

    private Digester digester;
    private int numOfHashingThreads;

    public DefaultChunkVerifier(Digester digester, int numOfHashingThreads) {
        this.digester = digester;
        this.numOfHashingThreads = numOfHashingThreads;
    }

    @Override
    public boolean verify(List<ChunkDescriptor> chunks, Bitfield bitfield) {
        if (chunks.size() != bitfield.getPiecesTotal()) {
            throw new IllegalArgumentException("Bitfield has different size than the list of chunks. Bitfield size: " +
                    bitfield.getPiecesTotal() + ", number of chunks: " + chunks.size());
        }

        ChunkDescriptor[] arr = chunks.toArray(new ChunkDescriptor[chunks.size()]);
        if (numOfHashingThreads > 1) {
            collectParallel(arr, bitfield);
        } else {
            createWorker(arr, 0, arr.length, bitfield).run();
        }
        // try to purge all data that was loaded by the verifiers
        System.gc();

        return bitfield.getPiecesRemaining() == 0;
    }

    @Override
    public boolean verify(ChunkDescriptor chunk) {
        byte[] expected = chunk.getChecksum();
        byte[] actual = digester.digest(chunk.getData());
        return Arrays.equals(expected, actual);
    }

    private List<ChunkDescriptor> collectParallel(ChunkDescriptor[] chunks, Bitfield bitfield) {
        int n = numOfHashingThreads;
        ExecutorService workers = Executors.newFixedThreadPool(n);

        List<Future<?>> futures = new ArrayList<>();

        int batchSize = chunks.length / n;
        int i, limit = 0;
        while ((i = limit) < chunks.length) {
            if (futures.size() == n - 1) {
                // assign the remaining bits to the last worker
                limit = chunks.length;
            } else {
                limit = i + batchSize;
            }
            futures.add(workers.submit(createWorker(chunks, i, Math.min(chunks.length, limit), bitfield)));
        }

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Verifying torrent data with {} workers", futures.size());
        }

        Set<Throwable> errors = ConcurrentHashMap.newKeySet();
        futures.forEach(f -> {
            try {
                f.get();
            } catch (Exception e) {
                LOGGER.error("Unexpected error during verification of torrent data", e);
                errors.add(e);
            }
        });

        workers.shutdown();
        while (!workers.isTerminated()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new BtException("Unexpectedly interrupted");
            }
        }

        if (!errors.isEmpty()) {
            throw new BtException("Failed to verify torrent data:" +
                    errors.stream().map(this::errorToString).reduce(String::concat).get());
        }

        return Arrays.asList(chunks);
    }

    private Runnable createWorker(ChunkDescriptor[] chunks,
                                  int from,
                                  int to,
                                  Bitfield bitfield) {
        return () -> {
            int i = from;
            while (i < to) {
                // optimization to speedup the initial verification of torrent's data
                int[] emptyUnits = new int[]{0};
                chunks[i].getData().visitUnits((u, off, lim) -> {
                    // limit of 0 means an empty file,
                    // and we don't want to account for those
                    if (u.size() == 0 && lim != 0) {
                        emptyUnits[0]++;
                    }
                    return true;
                });

                // if any of this chunk's storage units is empty,
                // then the chunk is neither complete nor verified
                if (emptyUnits[0] == 0) {
                    boolean verified = verify(chunks[i]);
                    if (verified) {
                        bitfield.markVerified(i);
                    }
                }
                i++;
            }
        };
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
