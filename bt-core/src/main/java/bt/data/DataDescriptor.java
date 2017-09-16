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

import java.io.Closeable;
import java.util.List;

/**
 * Torrent's data descriptor.
 * Provides access to individual chunks and status of torrent's data.
 *
 * @since 1.0
 */
public interface DataDescriptor extends Closeable {

    /**
     * @return List of chunks in the same order as they appear in torrent's metainfo.
     *         Hence, index of a chunk in this list can be used
     *         as the index of the corresponding piece in data exchange between peers.
     * @since 1.0
     */
    List<ChunkDescriptor> getChunkDescriptors();

    /**
     * @return Status of torrent's data.
     * @since 1.0
     */
    Bitfield getBitfield();
}
