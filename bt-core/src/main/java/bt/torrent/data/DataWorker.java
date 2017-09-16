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

package bt.torrent.data;

import bt.net.Peer;

import java.util.concurrent.CompletableFuture;

/**
 * Data worker is responsible for processing blocks and block requests, received from peers.
 *
 * @since 1.0
 */
public interface DataWorker {

    /**
     * Add a read block request.
     *
     * @param peer Requestor
     * @param pieceIndex Index of the requested piece (0-based)
     * @param offset Offset in piece to start reading from (0-based)
     * @param length Amount of bytes to read
     * @return Future; rejected requests are returned immediately (see {@link BlockRead#isRejected()})
     * @since 1.0
     */
    CompletableFuture<BlockRead> addBlockRequest(Peer peer, int pieceIndex, int offset, int length);

    /**
     * Add a write block request.
     *
     * @param peer Peer, that the data has been received from
     * @param pieceIndex Index of the piece to write to (0-based)
     * @param offset Offset in piece to start writing to (0-based)
     * @param block Data
     * @return Future; rejected requests are returned immediately (see {@link BlockWrite#isRejected()})
     * @since 1.0
     */
    CompletableFuture<BlockWrite> addBlock(Peer peer, int pieceIndex, int offset, byte[] block);
}
