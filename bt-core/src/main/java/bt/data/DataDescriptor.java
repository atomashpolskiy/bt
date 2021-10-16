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

import bt.metainfo.TorrentFile;

import java.io.Closeable;
import java.util.BitSet;
import java.util.List;
import java.util.Set;

/**
 * Torrent's data descriptor.
 * Provides access to individual chunks and status of torrent's data.
 *
 * @since 1.0
 */
public interface DataDescriptor extends Closeable {

    /**
     * @return List of chunks in the same order as they appear in torrent's metainfo.
     * Hence, index of a chunk in this list can be used
     * as the index of the corresponding piece in data exchange between peers.
     * @since 1.0
     */
    List<ChunkDescriptor> getChunkDescriptors();

    /**
     * @return Status of torrent's data.
     * @since 1.0
     */
    LocalBitfield getBitfield();

    /**
     * Get a list of files that a given piece index intersects
     *
     * @return A list of files that a given piece index intersects
     * @since 1.7
     * @deprecated use {@link #getAllPiecesForFiles(Set)} or {@link #getPiecesWithOnlyFiles(Set)} instead
     */
    @Deprecated
    List<TorrentFile> getFilesForPiece(int pieceIndex);

    /**
     * Get all pieces that contain any part of the set of files
     *
     * @param files the files the pieces are needed for
     * @return All pieces that contain any part of the passed in files
     * @since 1.10
     */
    BitSet getAllPiecesForFiles(Set<TorrentFile> files);

    /**
     * Get the set of pieces that only contain parts of the the set of files
     *
     * @param files the files the pieces are needed for
     * @return The set of pieces that only contain parts of the passed in files
     * @since 1.10
     */
    BitSet getPiecesWithOnlyFiles(Set<TorrentFile> files);

    /**
     * Get the data reader interface, which provides convenient ways
     * to work with torrent's data (e.g. read it in a stream-like fashion).
     *
     * @return Data reader
     * @since 1.8
     */
    DataReader getReader();

    /**
     * Wait for all of the pieces of the torrent to download
     */
    void waitForAllPieces() throws InterruptedException;

    /**
     * Check if the torrent was finished upon initial hashing
     *
     * @return true if the torrent file was complete upon initial hashing
     */
    boolean startedAsSeed();

    /**
     * Get the amount of data left to verify
     *
     * @return the amount of data left to verify
     * @since 1.10
     */
    long getLeft();
}
