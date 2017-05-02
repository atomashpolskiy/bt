package bt.data;

import bt.BtException;
import bt.data.digest.Digester;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Contains up to {@code Integer.MAX_VALUE} blocks of data of arbitrary size,
 * that can span over up to {@code Integer.MAX_VALUE} files.
 *
 * @since 1.0
 */
class DefaultChunkDescriptor implements ChunkDescriptor {

    private volatile DataStatus status;
    private List<StorageUnit> units;
    private DataRange data;

    private long blockSize;
    private int blockCount;

    private ReentrantReadWriteLock readWriteLock;

    /**
     * Hash of this chunk's contents; used to verify integrity of chunk's data
     */
    private byte[] checksum;
    private Digester digester;

    /**
     * List of statuses of this chunk's blocks: 1 for complete-and-verified blocks
     */
    private BitSet bitfield;

    /**
     * Chunk is a part of the torrent's files collection,
     * possibly overlapping several files (in case of multi-file torrent)
     * or being just a piece of one single-file torrent.
     *
     * @param units Collection of the torrent files (presented as storage units), that this chunk overlaps with
     * @param offset Offset from the beginning of the first file in {@code files}; inclusive
     * @param limit Offset from the beginning of the last file in {@code files}; exclusive
     * @param checksum Chunk's hash
     * @param blockSize Size of the read-write transfer block
     * @param shouldVerify If chunk should verify itself upon initialization
     */
    public DefaultChunkDescriptor(StorageUnit[] units,
                                  long offset,
                                  long limit,
                                  long blockSize,
                                  byte[] checksum,
                                  Digester digester,
                                  boolean shouldVerify) {

        if (units.length == 0) {
            throw new BtException("Failed to create chunk descriptor: no files");
        }

        // using fair lock for now
        this.readWriteLock = new ReentrantReadWriteLock(true);

        this.units = Collections.unmodifiableList(Arrays.asList(units));
        this.data = new BoundDataRange(new ReadWriteDataRange(this.units, offset, limit, readWriteLock));
        this.blockSize = blockSize;
        this.checksum = checksum;
        this.digester = digester;

        long blockCount = (long) Math.ceil(((double) data.length()) / blockSize);
        if (blockCount > Integer.MAX_VALUE) {
            throw new BtException("Integer overflow while constructing chunk: too many blocks");
        }
        this.blockCount = (int) blockCount;
        this.bitfield = new BitSet(this.blockCount);

        this.status = DataStatus.EMPTY;
        if (shouldVerify) {
            doVerify(false);
        }
    }

    @Override
    public DataStatus getStatus() {
        return status;
    }

    @Override
    public int getBlockCount() {
        return blockCount;
    }

    @Override
    public long getBlockSize() {
        return blockSize;
    }

    @Override
    public boolean isBlockPresent(int blockIndex) {
        if (blockIndex < 0 || blockIndex >= blockCount) {
            throw new IllegalArgumentException("Invalid block index: " + blockIndex + "." +
                    " Expected 0.." + (blockCount - 1));
        }
        return bitfield.get(blockIndex);
    }

    @Override
    public DataRange getData() {
        return data;
    }

    @Override
    public boolean verify() {
        return doVerify(true);
    }

    // TODO: we need to also fill bitfield here
    private boolean doVerify(boolean completeOnly) {
        if (status == DataStatus.VERIFIED) {
            return true;
        } else if (completeOnly && status != DataStatus.COMPLETE) {
            return false;
        }

        // if any of this chunk's storage units doesn't exist,
        // then it's neither complete nor verified
        for (StorageUnit unit : units) {
            if (unit.size() == 0) {
                return false;
            }
        }

        // do not block readers when checking data integrity
        readWriteLock.readLock().lock();
        try {
            if (status == DataStatus.VERIFIED) {
                return true;
            }

            boolean verified = Arrays.equals(checksum, digester.digest(data));
            if (verified) {
                status = DataStatus.VERIFIED;
            }
            // TODO: reset the piece if verification is unsuccessful?
            return verified;

        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    private boolean isComplete() {
        return bitfield.cardinality() == blockCount;
    }

    private class BoundDataRange implements DataRange {
        private final DataRange delegate;
        private final long offset;

        BoundDataRange(DataRange delegate) {
            this(delegate, 0);
        }

        private BoundDataRange(DataRange delegate, long offset) {
            this.delegate = delegate;
            this.offset = offset;
        }

        @Override
        public long length() {
            return delegate.length();
        }

        @Override
        public DataRange getSubrange(long offset, long length) {
            return new BoundDataRange(delegate.getSubrange(offset, length), offset);
        }

        @Override
        public DataRange getSubrange(long offset) {
            return new BoundDataRange(delegate.getSubrange(offset), offset);
        }

        @Override
        public byte[] getBytes() {
            return delegate.getBytes();
        }

        /**
         * Blocks current thread if there are concurrent read operations in progress.
         * Block all concurrent read operations.
         *
         * This method implements a rather simple strategy to track which chunk's blocks are saved:
         * only those blocks are considered saved that fit fully in the {@code block} passed
         * as an argument to this method.
         * E.g. if the array being passed to this method is 6 bytes long, and this chunk is split into 4-byte blocks,
         * and the {@code offset} is exactly the first index of some chunk's block,
         * then this array spans over 2 4-byte blocks, but from these 2 blocks the last one is not fully
         * represented in it (i.e. 2 trailing bytes are trimmed). In such case only the first block will be
         * considered saved (i.e. the corresponding index in the bitfield will be set to 1).
         *
         * @param block Can be of a different size than this chunk's "standard" block
         */
        // TODO: implement partial tracking of the blocks that do not fully fit in the array passed as an argument
        @Override
        public void putBytes(byte[] block) {
            if (status == DataStatus.VERIFIED) {
                throw new IllegalStateException("Failed to write data -- chunk is complete and verified");
            }

            if (block.length == 0) {
                return;
            }

            readWriteLock.writeLock().lock();
            try {
                delegate.putBytes(block);

                // update bitfield with the info about the new blocks;
                // if only a part of some block is written,
                // then don't count it
                boolean shouldCheckIfComplete = false;
                // handle the case when the last block is smaller than the others
                // mark it as complete only when all of the block's data is present
                long lastBlockSize = data.length() % blockSize;
                long lastBlockOffset = data.length() - lastBlockSize;
                if (offset <= lastBlockOffset && offset + block.length >= data.length()) {
                    bitfield.set(blockCount - 1);
                    shouldCheckIfComplete = true;
                }
                if (block.length >= blockSize) {
                    int numberOfBlocks = (int) Math.floor(((double) block.length) / blockSize);
                    if (numberOfBlocks > 0) {
                        int firstBlockIndex = (int) Math.ceil(((double) offset) / blockSize);
                        int lastBlockIndex = (int) Math.floor(((double) (offset + block.length)) / blockSize) - 1;
                        if (lastBlockIndex >= firstBlockIndex) {
                            bitfield.set(firstBlockIndex, lastBlockIndex + 1);
                            shouldCheckIfComplete = true;
                        }
                    }
                }
                if (shouldCheckIfComplete) {
                    status = isComplete() ? DataStatus.COMPLETE : DataStatus.INCOMPLETE;
                }
            } finally {
                readWriteLock.writeLock().unlock();
            }
        }

        @Override
        public void visitUnits(DataRangeVisitor visitor) {
            delegate.visitUnits(visitor);
        }
    }
}
