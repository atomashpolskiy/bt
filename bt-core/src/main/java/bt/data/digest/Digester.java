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

package bt.data.digest;

import bt.data.DataRange;
import bt.data.range.Range;

/**
 * Calculates hash of some binary data.
 * Implementations may use different hashing algorithms.
 *
 * @since 1.2
 */
public interface Digester {

    /**
     * Calculates a digest of a data range.
     * <p>
     * Important: If some of the data can't be read immediately
     * (e.g. due to idiosyncrasies of underlying storage),
     * then this method may return incorrect result.
     *
     * @return Digest (depends on the algorithm being used)
     * @since 1.2
     */
    byte[] digest(DataRange data);

    /**
     * Calculates a digest of a data range. Throws {@link IllegalStateException} if any {@link bt.data.StorageUnit} does
     * not return enough data.
     *
     * @return Digest (depends on the algorithm being used)
     * @throws IllegalStateException if the underlying StorageUnits in the DataRange return less than
     *                               {@link bt.data.StorageUnit#size()} from
     *                               {@link bt.data.StorageUnit#readBlockFully(java.nio.ByteBuffer, long)}. This can happen
     *                               because the storage in the underlying storage units is lazily allocated.
     * @since 1.9
     */
    byte[] digestForced(DataRange data);

    /**
     * Calculates Digest of a binary range.
     *
     * @return Digest (depends on the algorithm being used)
     * @since 1.3
     */
    byte[] digest(Range<?> data);

    /**
     * Return the length of this digest
     *
     * @return the length of this digest
     * @since 1.10
     */
    int length();

    /**
     * Clone the digester so any thread local variables will be cleaned up when the clone is GC'd
     *
     * @return the copy of the digester
     */
    Digester createCopy();
}
