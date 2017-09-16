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

/**
 * Provides basic information about
 * the availability of different pieces in the swarm.
 *
 * @since 1.0
 */
public interface PieceStatistics {

    /**
     * @return Total number of peers that have a given piece.
     * @since 1.0
     */
    int getCount(int pieceIndex);

    /**
     * @return Total number of pieces in the torrent (i.e. max piece index + 1)
     * @since 1.0
     */
    int getPiecesTotal();
}
