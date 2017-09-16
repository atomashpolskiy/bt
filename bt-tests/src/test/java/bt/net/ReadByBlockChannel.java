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

package bt.net;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

class ReadByBlockChannel implements ReadableByteChannel {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReadByBlockChannel.class);

    private final LinkedList<byte[]> data;
    private boolean closed;
    private int readCount;
    private int currentBlockIndex;

    /**
     * @param dataList Collection of data blocks that this channel will be reading
     *                 (in the same order as they should be read)
     */
    ReadByBlockChannel(List<byte[]> dataList) {
        this.data = new LinkedList<>(dataList);
    }

    /**
     * Reads a block of data into the provided buffer. If there is insufficient space for a whole block,
     * then it will be split into two blocks, the first part will be put into the buffer,
     * and the second part will be used on subsequent read(s).
     *
     * @return Number of bytes read or -1 in the end of stream has been reached (no data blocks left)
     */
    @Override
    public int read(ByteBuffer dst) throws IOException {
        StringBuilder s = new StringBuilder("Read #" + readCount);
        readCount++;

        int read = 0;
        if (!data.isEmpty()) {
            int remaining = dst.remaining();
            s.append("; remaining space in buffer: " + remaining + " (pos: " + dst.position() + ", lim: " + dst.limit() + ")");
            if (dst.hasRemaining()) {
                byte[] block = data.removeFirst();
                s.append("; current block: #" + currentBlockIndex + "; remaining bytes in block: " + block.length);
                if (block.length <= remaining) {
                    dst.put(block);
                    read = block.length;
                    currentBlockIndex++;
                } else {
                    // split the block into two
                    byte[] head = Arrays.copyOfRange(block, 0, remaining);
                    byte[] tail = Arrays.copyOfRange(block, remaining, block.length);
                    s.append(String.format("; split block into [%s:%s) and [%s:%s)", 0, remaining, remaining, block.length));
                    dst.put(head);
                    read = head.length;
                    data.addFirst(tail);
                }
            }
        } else {
            s.append("; no data left, end of stream");
            read = -1; // EOF
        }

        s.append("; bytes read: " + read);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(s.toString());
        }
        return read;
    }

    @Override
    public boolean isOpen() {
        return !closed;
    }

    @Override
    public void close() throws IOException {
        closed = true;
    }
}
