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

package bt.torrent;

import bt.net.Peer;

import java.util.Set;

/**
 * Provides information about a particular torrent session.
 *
 * @since 1.0
 */
public interface TorrentSessionState {

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
     * @return Collection of peers, that this session is connected to
     * @since 1.0
     */
    Set<Peer> getConnectedPeers();
}
