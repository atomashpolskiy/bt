/*
 * Copyright (c) 2016—2019 Andrei Tomashpolskiy and individual contributors.
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

import java.nio.channels.ReadableByteChannel;

/**
 * Provides convenient ways to work with torrent's data.
 *
 * @since 1.8
 */
public interface DataReader {

    /**
     * Create a sequential view of torrent's data in the form of a {@link ReadableByteChannel}.
     *
     * The returned channel's {@code read(ByteBuffer)} method has the following behavior:
     *
     * <ul>
     * <li>blocks, until at least one byte of data has been read</li>
     * <li>returns -1, when all data has been processed.</li>
     * </ul>
     *
     * Code snippet:
     *
     * <pre>
     * ByteBuffer data = ByteBuffer.allocate(TORRENT_SIZE);
     * ByteBuffer buffer = ByteBuffer.allocate(8192);
     * ReadableByteChannel ch = reader.createChannel();
     *
     * while (ch.read(buffer) &gt;= 0) {
     *     buffer.flip();
     *     data.put(buffer);
     *     buffer.clear();
     * }
     * </pre>
     *
     * @return Channel-like sequential view of torrent's data.
     *         The returned channel will block on read() calls,
     *         if the next portion of data is not yet available.
     * @since 1.8
     */
    ReadableByteChannel createChannel();
}
