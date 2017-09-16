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

import bt.data.DataDescriptor;

/**
 * Provides an interface for controlling
 * the state of a torrent processing session.
 *
 * @since 1.0
 */
public interface TorrentDescriptor {

    /**
     * @return true if the torrent is currently being processed
     * @since 1.0
     */
    boolean isActive();

    /**
     * Issue a request to begin torrent processing
     *
     * @since 1.0
     */
    void start();

    /**
     * Issue a request to stop torrent processing
     *
     * @since 1.0
     */
    void stop();

    /**
     * Signal that the torrent has been successfully downloaded and verified
     *
     * @since 1.0
     */
    void complete();

    /**
     * @return Torrent data descriptor or null, if it hasn't been created yet
     * @since 1.0
     */
    DataDescriptor getDataDescriptor();
}
