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

/**
 * @since 1.0
 */
class DefaultChunkDescriptor implements ChunkDescriptor {

    private final DataRange data;
    private final BlockSet blockSet;

    /**
     * Hash of this chunk's contents; used to verify integrity of chunk's data
     */
    private final byte[] checksum;

    /**
     * @param data Subrange of the torrent data, that this chunk represents
     * @param blockSet Data represented as a set of blocks
     * @param checksum Chunk's hash
     */
    public DefaultChunkDescriptor(DataRange data,
                                  BlockSet blockSet,
                                  byte[] checksum) {
        this.data = data;
        this.blockSet = blockSet;
        this.checksum = checksum;
    }

    @Override
    public byte[] getChecksum() {
        return checksum;
    }

    @Override
    public DataRange getData() {
        return data;
    }

    @Override
    public int blockCount() {
        return blockSet.blockCount();
    }

    @Override
    public long length() {
        return blockSet.length();
    }

    @Override
    public long blockSize() {
        return blockSet.blockSize();
    }

    @Override
    public long lastBlockSize() {
        return blockSet.lastBlockSize();
    }

    @Override
    public boolean isPresent(int blockIndex) {
        return blockSet.isPresent(blockIndex);
    }

    @Override
    public boolean isComplete() {
        return blockSet.isComplete();
    }

    @Override
    public boolean isEmpty() {
        return blockSet.isEmpty();
    }
}
