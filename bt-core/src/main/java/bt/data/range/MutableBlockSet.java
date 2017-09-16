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

package bt.data.range;

import bt.data.BlockSet;

import java.util.BitSet;

class MutableBlockSet implements BlockSet {

    private final long length;
    private final long blockSize;
    private final int blockCount;
    private final long lastBlockSize;
    private final long lastBlockOffset;

    /**
     * Block availability bitmask: 1 for present blocks
     */
    private final BitSet bitmask;

    MutableBlockSet(long length, long blockSize) {
        // intentionally allow length to be greater than block size
        if (length < 0 || blockSize < 0) {
            throw new IllegalArgumentException("Illegal arguments: length (" + length + "), block size (" + blockSize + ")");
        }

        long blockCount = (long) Math.ceil(((double) length) / blockSize);
        if (blockCount > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Too many blocks: length (" + length +
                    "), block size (" + blockSize + "), total blocks (" + blockCount + ")");
        }

        this.length = length;
        this.blockSize = blockSize;
        this.blockCount = (int) blockCount;
        this.bitmask = new BitSet((int) blockCount);

        // handle the case when the last block is smaller than the others
        long lastBlockSize = length % blockSize;
        if (lastBlockSize > 0) {
            this.lastBlockSize = lastBlockSize;
            this.lastBlockOffset = length - lastBlockSize;
        } else {
            this.lastBlockSize = blockSize;
            this.lastBlockOffset = length - blockSize;
        }
    }

    @Override
    public int blockCount() {
        return blockCount;
    }

    @Override
    public long length() {
        return length;
    }

    @Override
    public long blockSize() {
        return blockSize;
    }

    @Override
    public long lastBlockSize() {
        return lastBlockSize;
    }

    @Override
    public boolean isPresent(int blockIndex) {
        if (blockIndex < 0 || blockIndex >= blockCount) {
            throw new IllegalArgumentException("Invalid block index: " + blockIndex + ". Expected 0.." + (blockCount - 1));
        }
        return bitmask.get(blockIndex);
    }

    @Override
    public boolean isComplete() {
        return bitmask.cardinality() == blockCount;
    }

    @Override
    public boolean isEmpty() {
        return bitmask.isEmpty();
    }

    /*
    // TODO rewrite description
     * This method implements a simple strategy to track which blocks have been written:
     * only those blocks are considered present that fit fully in the {@code block} passed
     * as an argument to this method.
     * E.g. if the array being passed to this method is 6 bytes long, and this chunk is split into 4-byte blocks,
     * and the {@code offset} is exactly the first index of some chunk's block,
     * then this array spans over 2 4-byte blocks, but from these 2 blocks the last one is not fully
     * represented in it (i.e. 2 trailing bytes are trimmed). In such case only the first block will be
     * considered saved (i.e. the corresponding index in the bitmask will be set to 1).
     */
    protected void markAvailable(long offset, long length) {
        // update bitmask with the info about the new blocks;
        // if only a part of some block is written,
        // then don't count it

        // handle the case when the last block is smaller than the others
        // mark it as complete only when all of the block's data is present

        if (offset <= lastBlockOffset && offset + length >= length()) {
            bitmask.set(blockCount - 1);
        }
        if (length >= blockSize) {
            int numberOfBlocks = (int) Math.floor(((double) length) / blockSize);
            if (numberOfBlocks > 0) {
                int firstBlockIndex = (int) Math.ceil(((double) offset) / blockSize);
                int lastBlockIndex = (int) Math.floor(((double) (offset + length)) / blockSize) - 1;
                if (lastBlockIndex >= firstBlockIndex) {
                    bitmask.set(firstBlockIndex, lastBlockIndex + 1);
                }
            }
        }
    }
}
