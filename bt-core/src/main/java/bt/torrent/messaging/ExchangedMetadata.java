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

package bt.torrent.messaging;

import bt.data.BlockSet;
import bt.data.digest.SHA1Digester;
import bt.data.range.BlockRange;
import bt.data.range.ByteRange;
import bt.data.range.Range;
import bt.data.range.Ranges;

/**
 * BEP-9 torrent metadata, thread-safe
 */
class ExchangedMetadata {

    private volatile Range<BlockRange<ByteRange>> metadata;
    private volatile BlockSet metadataBlocks;

    private volatile byte[] digest;
    private final Object digestLock;

    ExchangedMetadata(byte[] data, int blockSize) {
        BlockRange<ByteRange> range = Ranges.blockRange(new ByteRange(data), blockSize);
        this.metadata = Ranges.synchronizedRange(range);
        this.metadataBlocks = Ranges.synchronizedBlockSet(range.getBlockSet());

        this.digestLock = new Object();
    }

    ExchangedMetadata(int totalSize, int blockSize) {
        this(new byte[totalSize], blockSize);
    }

    public boolean isBlockPresent(int blockIndex) {
        return metadataBlocks.isPresent(blockIndex);
    }

    public void setBlock(int blockIndex, byte[] block) {
        validateBlockIndex(blockIndex);
        metadata.getSubrange(blockIndex * metadataBlocks.blockSize()).putBytes(block);
    }

    public int getBlockCount() {
        return metadataBlocks.blockCount();
    }

    public boolean isComplete() {
        return metadataBlocks.isComplete();
    }

    /**
     * @throws IllegalStateException if metadata is not complete
     * @see #isComplete()
     */
    public byte[] getSha1Digest() {
        if (!metadataBlocks.isComplete()) {
            throw new IllegalStateException("Metadata is not complete");
        }

        if (digest == null) {
            synchronized (digestLock) {
                if (digest == null) {
                    digest = SHA1Digester.rolling(1000000).digest(metadata);
                }
            }
        }
        return digest;
    }

    /**
     * @throws IllegalStateException if metadata is not complete
     * @see #isComplete()
     */
    public byte[] getBytes() {
        if (!metadataBlocks.isComplete()) {
            throw new IllegalStateException("Metadata is not complete");
        }

        return metadata.getBytes();
    }

    public byte[] getBlock(int blockIndex) {
        validateBlockIndex(blockIndex);

        int blockLength;
        // last piece may be smaller than the rest
        if (blockIndex == metadataBlocks.blockCount() - 1) {
            blockLength = (int) metadataBlocks.lastBlockSize();
        } else {
            blockLength = (int) metadataBlocks.blockSize();
        }

        return metadata.getSubrange(blockIndex * metadataBlocks.blockSize(), blockLength).getBytes();
    }

    public int length() {
        return (int) metadata.length();
    }

    private void validateBlockIndex(int blockIndex) {
        int blockCount = metadataBlocks.blockCount();
        if (blockIndex < 0 || blockIndex >= blockCount) {
            throw new IllegalArgumentException("Invalid block index: " + blockIndex + "; expected 0.." + blockCount);
        }
    }
}
