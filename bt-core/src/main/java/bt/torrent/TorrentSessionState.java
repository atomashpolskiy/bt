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

import bt.net.ConnectionKey;
import bt.processor.ProcessingContext;
import bt.torrent.fileselector.FilePrioritySelector;

import java.util.Set;

/**
 * Provides information about a particular torrent session.
 *
 * @since 1.0
 */
public interface TorrentSessionState {
    /**
     * The bytes returned when the data transfer is unknown
     */
    long UNKNOWN = -1;

    /**
     * @return Total number of pieces in the torrent
     * @since 1.0
     */
    int getPiecesTotal();

    /**
     * @return Number of pieces that the local client already has
     * @since 1.7
     */
    int getPiecesComplete();

    /**
     * @return Number of pieces that the local client does not have yet
     * @since 1.7
     */
    int getPiecesIncomplete();

    /**
     * @return Number of pieces, that the local client will download
     * @since 1.0
     */
    int getPiecesRemaining();

    /**
     * @return Number of pieces that will be skipped
     * @since 1.7
     */
    int getPiecesSkipped();

    /**
     * @return Number of pieces that will NOT be skipped
     * @since 1.7
     */
    int getPiecesNotSkipped();

    /**
     * @return Amount of data downloaded via this session (in bytes)
     * @since 1.0
     */
    long getDownloaded();

    /**
     * @return Amount of data uploaded via this session (in bytes)
     * @since 1.0
     */
    long getUploaded();

    /**
     * Get the number of bytes left to verify, or {@link #UNKNOWN} if unknown (torrent not yet fetched)
     *
     * @return the number of bytes left to verify
     * @since 1.10
     */
    long getLeft();

    /**
     * Check if the torrent was finished upon initial hashing
     *
     * @return true if the torrent file was complete upon initial hashing
     * @since 1.10
     */
    boolean startedAsSeed();

    /**
     * @return Collection of peers, that this session is connected to
     * @since 1.9
     */
    Set<ConnectionKey> getConnectedPeers();

    /**
     * Update the priority of downloading specified files
     *
     * @param c                The processing context of the torrent
     * @param prioritySelector the files to update the priority for
     * @return whether the update was successful
     */
    boolean updateFileDownloadPriority(ProcessingContext c, FilePrioritySelector prioritySelector);
}
