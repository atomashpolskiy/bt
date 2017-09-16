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

import java.util.Optional;

/**
 * Read block command.
 *
 * If {@link #isRejected()} returns true,
 * this means that the request was not accepted by the data worker.
 * If {@link #getError()} is not empty,
 * this means that an exception happened during the request processing.
 * Subsequently, {@link #getBlock()} will return {@link Optional#empty()} in both cases.
 *
 * @since 1.0
 */
public class BlockRead {

    /**
     * @since 1.0
     */
    static BlockRead complete(Peer peer, int pieceIndex, int offset, byte[] block) {
        return new BlockRead(peer, null, false, pieceIndex, offset, block);
    }

    /**
     * @since 1.0
     */
    static BlockRead rejected(Peer peer, int pieceIndex, int offset) {
        return new BlockRead(peer, null, true, pieceIndex, offset, null);
    }

    /**
     * @since 1.0
     */
    static BlockRead exceptional(Peer peer, Throwable error, int pieceIndex, int offset) {
        return new BlockRead(peer, error, false, pieceIndex, offset, null);
    }

    private Peer peer;
    private int pieceIndex;
    private int offset;
    private Optional<byte[]> block;

    private boolean rejected;
    private Optional<Throwable> error;

    private BlockRead(Peer peer, Throwable error, boolean rejected, int pieceIndex, int offset, byte[] block) {
        this.peer = peer;
        this.error = Optional.ofNullable(error);
        this.rejected = rejected;
        this.pieceIndex = pieceIndex;
        this.offset = offset;
        this.block = Optional.ofNullable(block);
    }

    /**
     * @return Requesting peer
     * @since 1.0
     */
    public Peer getPeer() {
        return peer;
    }

    /**
     * @return true if the request was not accepted by the data worker
     * @since 1.0
     */
    public boolean isRejected() {
        return rejected;
    }

    /**
     * @return Index of the piece being requested
     * @since 1.0
     */
    public int getPieceIndex() {
        return pieceIndex;
    }

    /**
     * @return Offset in a piece to read the block from
     * @since 1.0
     */
    public int getOffset() {
        return offset;
    }

    /**
     * @return Block of data or {@link Optional#empty()},
     *         if {@link #isRejected()} returns true or if {@link #getError()} is not empty
     * @since 1.0
     */
    public Optional<byte[]> getBlock() {
        return block;
    }

    /**
     * @return {@link Optional#empty()} if processing of the request completed normally,
     *         or exception otherwise.
     * @since 1.0
     */
    public Optional<Throwable> getError() {
        return error;
    }
}
