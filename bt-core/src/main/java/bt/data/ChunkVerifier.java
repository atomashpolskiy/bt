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

import java.util.List;

/**
 * Implements data verification strategy.
 *
 * @since 1.2
 */
public interface ChunkVerifier {

    /**
     * Conducts verification of the provided list of chunks and updates bitfield with the results.
     *
     * @param chunks List of chunks
     * @param bitfield Bitfield
     * @return true if all chunks have been verified successfully (meaning that all data is present and correct)
     * @since 1.2
     */
    boolean verify(List<ChunkDescriptor> chunks, Bitfield bitfield);

    /**
     * Conducts verification of the provided chunk.
     *
     * @param chunk Chunk
     * @return true if the chunk has been verified successfully (meaning that all data is present and correct)
     * @since 1.2
     */
    boolean verify(ChunkDescriptor chunk);
}
