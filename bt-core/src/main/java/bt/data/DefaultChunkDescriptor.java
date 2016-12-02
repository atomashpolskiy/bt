package bt.data;

import bt.BtException;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Contains up to {@code Integer.MAX_VALUE} blocks of data of arbitrary size,
 * that can span over up to {@code Integer.MAX_VALUE} files.
 *
 * @since 1.0
 */
class DefaultChunkDescriptor implements ChunkDescriptor {

    private volatile DataStatus status;
    private StorageUnit[] units;

    private long offsetInFirstChunkFile;
    private long limitInLastChunkFile;
    private long size;
    private long blockSize;

    private ReentrantReadWriteLock readWriteLock;

    /**
     * Hash of this chunk's contents; used to verify integrity of chunk's data
     */
    private byte[] checksum;

    /**
     * List of statuses of this chunk's blocks: true for complete-and-verified blocks
     */
    private boolean[] bitfield;

    /**
     * This is a "map" of this chunk's "virtual addresses" (offsets) into the chunk's files.
     * Size of this map is equal to the number of chunk's files.
     * The number x at some position n is the "virtual" offset that designates the beginning
     * of the n-th file. So, when requested to read or write a block beginning with offset x,
     * the chunk will request the n-th file for a block with offset 0 (i.e. the beginning of the file).
     *
     * Obviously, the number at position 0 is always 0
     * and translates into the offset in the first file in the chunk (this offset is set upon creation of the chunk).
     *
     * Also, it's guaranted that the address at position n+1 is always greater than address at position n.
     */
    private long[] fileOffsets;

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
     *
     * @since 1.0
     */
    public DefaultChunkDescriptor(StorageUnit[] units, long offset, long limit,
                                  byte[] checksum, long blockSize, boolean shouldVerify) {

        if (units.length == 0) {
            throw new BtException("Failed to create chunk descriptor: no files");
        }

        this.units = units;
        this.offsetInFirstChunkFile = offset;
        this.limitInLastChunkFile = limit;
        this.blockSize = blockSize;
        this.checksum = checksum;

        // using fair lock for now
        readWriteLock = new ReentrantReadWriteLock(true);

        if (units.length == 1) {
            size = limit - offset;
        } else {
            size = units[0].capacity() - offset;
            for (int i = 1; i < units.length - 1; i++) {
                size += units[i].capacity();
            }
            size += limit;
        }

        if (size < blockSize) {
            throw new BtException("Illegal arguments -- chunk size is smaller than block size (size: " +
                    size + ", block size: " + blockSize + ")");
        }

        long blockCount = (long) Math.ceil(((double) size) / blockSize);
        if (blockCount > Integer.MAX_VALUE) {
            throw new BtException("Integer overflow while constructing chunk: too many blocks");
        }
        bitfield = new boolean[(int) blockCount];

        fileOffsets = new long[units.length];
        // first "virtual" address (first file begins with offset 0)
        fileOffsets[0] = 0;
        if (units.length > 1) {
            // it's possible that chunk does not have access to the entire first file
            fileOffsets[1] = units[0].capacity() - offset;
        }
        for (int i = 2; i < units.length; i++) {
            fileOffsets[i] = fileOffsets[i - 1] + units[i - 1].capacity();
        }

        status = DataStatus.EMPTY;
        if (shouldVerify) {
            doVerify(false);
        }
    }

    @Override
    public DataStatus getStatus() {
        return status;
    }

    @Override
    public long getSize() {
        return size;
    }

    @Override
    public int getBlockCount() {
        return bitfield.length;
    }

    @Override
    public long getBlockSize() {
        return blockSize;
    }

    @Override
    public boolean isBlockVerified(int blockIndex) {
        if (blockIndex < 0 || blockIndex >= bitfield.length) {
            throw new IllegalArgumentException("Invalid block index: " + blockIndex + "." +
                    " Expected 0.." + (bitfield.length - 1));
        }
        return bitfield[blockIndex];
    }

    /**
     * Blocks current thread if there are concurrent write operations in progress.
     * Blocks all concurrent write operations.
     */
    @Override
    public byte[] readBlock(long offset, int length) {

        if (length == 0) {
            return new byte[]{};
        }

        Range range = getRange(offset, length);

        byte[] block = new byte[length];
        ByteBuffer buffer = ByteBuffer.wrap(block);

        readWriteLock.readLock().lock();
        try {
            range.visitFiles(new RangeFileVisitor() {

                int offsetInBlock = 0;

                @Override
                public void visitFile(StorageUnit unit, long off, long lim) {

                    long len = lim - off;
                    if (len > Integer.MAX_VALUE) {
                        throw new BtException("Too much data requested");
                    }

                    if (((long) offsetInBlock) + len > Integer.MAX_VALUE) {
                        // overflow -- isn't supposed to happen unless the algorithm in range is incorrect
                        throw new BtException("Integer overflow while constructing block");
                    }

                    buffer.position(offsetInBlock);
                    buffer.limit(offsetInBlock + (int)len);
                    unit.readBlock(buffer, off);
                    offsetInBlock += len;
                }
            });
        } finally {
            readWriteLock.readLock().unlock();
        }

        return block;
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
    public void writeBlock(byte[] block, long offset) {

        if (block.length == 0) {
            return;
        }

        Range range = getRange(offset, block.length);

        readWriteLock.writeLock().lock();
        try {
            ByteBuffer buffer = ByteBuffer.wrap(block).asReadOnlyBuffer();
            range.visitFiles(new RangeFileVisitor() {

                int offsetInBlock = 0;
                int limitInBlock;

                @Override
                public void visitFile(StorageUnit unit, long off, long lim) {

                    long fileSize = lim - off;
                    if (fileSize > Integer.MAX_VALUE) {
                        throw new BtException("Unexpected file size -- insufficient data in block");
                    }

                    limitInBlock = offsetInBlock + (int) fileSize;
                    buffer.position(offsetInBlock);
                    buffer.limit(limitInBlock);
                    unit.writeBlock(buffer, off);
                    offsetInBlock = limitInBlock;
                }
            });

            // update bitfield with the info about the new blocks;
            // if only a part of some block is written,
            // then don't count it
            boolean shouldCheckIfComplete = false;
            if (offset + block.length == size) {
                bitfield[bitfield.length - 1] = true;
                shouldCheckIfComplete = true;
            }
            if (block.length >= blockSize) {
                int numberOfBlocks = (int) Math.floor(((double) block.length) / blockSize);
                if (numberOfBlocks > 0) {
                    int firstBlockIndex = (int) Math.ceil(((double) offset) / blockSize);
                    int lastBlockIndex = (int) Math.floor(((double)(offset + block.length)) / blockSize) - 1;
                    if (lastBlockIndex >= firstBlockIndex) {
                        Arrays.fill(bitfield, firstBlockIndex, lastBlockIndex + 1, true);
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
    public boolean verify() {
        return doVerify(true);
    }

    private boolean doVerify(boolean completeOnly) {

        if (status == DataStatus.VERIFIED) {
            return true;
        } else if (completeOnly && status != DataStatus.COMPLETE) {
            return false;
        }

        Range allData = getRange(0, size);
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            // not going to happen
            throw new BtException("Unexpected error", e);
        }

        // do not block readers when checking data integrity
        readWriteLock.readLock().lock();
        try {
            if (status == DataStatus.VERIFIED) {
                return true;
            }

            allData.visitFiles((unit, off, lim) -> {

                int step = 2 << 12;
                long remaining = lim - off;
                if (remaining > Integer.MAX_VALUE) {
                    throw new BtException("Too much data -- can't read to buffer");
                }
                do {
                    digest.update(unit.readBlock(off, Math.min(step, (int) remaining)));
                    remaining -= step;
                    off += step;
                } while (remaining > 0);
            });

            boolean verified = Arrays.equals(checksum, digest.digest());
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

        // check if all chunk's blocks are present
        for (boolean b : bitfield) {
            if (!b) {
                return false;
            }
        }
        return true;
    }

    private Range getRange(long offset, long length) {

        if (offset < 0 || length < 0) {
            throw new BtException("Illegal arguments: offset (" + offset + "), length (" + length + ")");
        }

        int firstRequestedFileIndex,
            lastRequestedFileIndex;

        long offsetInFirstRequestedFile,
             limitInLastRequestedFile;

        // determine the file that the requested block begins in
        firstRequestedFileIndex = -1;
        for (int i = 0; i < units.length; i++) {

            if (offset < fileOffsets[i]) {
                firstRequestedFileIndex = i - 1;
                break;
            } else if (i == units.length - 1) {
                // reached the last file
                firstRequestedFileIndex = i;
            }
        }

        offsetInFirstRequestedFile = offset - fileOffsets[firstRequestedFileIndex];
        if (firstRequestedFileIndex == 0) {
            // if the first requested file is the first file in chunk,
            // then we need to begin from this chunk's offset in that file
            // (in case this chunk has access only to a portion of the file)
            offsetInFirstRequestedFile += offsetInFirstChunkFile;
        }

        lastRequestedFileIndex = firstRequestedFileIndex;
        long remaining = length;
        do {
            // determine which files overlap with the requested block
            if (firstRequestedFileIndex == lastRequestedFileIndex) {
                remaining -= (units[lastRequestedFileIndex].capacity() - offsetInFirstRequestedFile);
            } else {
                remaining -= units[lastRequestedFileIndex].capacity();
            }
        } while (remaining > 0 && ++lastRequestedFileIndex < units.length);

        if (lastRequestedFileIndex >= units.length) {
            // data in this chunk is insufficient to fulfill the block request
            throw new BtException("Insufficient data (offset: " + offset + ", requested block length: " + length + ")");
        }
        // if remaining is negative now, then we need to
        // strip off some data from the last file
        limitInLastRequestedFile = units[lastRequestedFileIndex].capacity() + remaining;

        if (lastRequestedFileIndex == units.length - 1) {
            if (limitInLastRequestedFile > limitInLastChunkFile) {
                // data in this chunk is insufficient to fulfill the block request
                throw new BtException("Insufficient data (offset: " + offset + ", requested block length: " + length + ")");
            }
        }

        // TODO: check if this is still needed and remove if not
//        if (limitInLastRequestedFile > Integer.MAX_VALUE) {
//            // overflow -- isn't supposed to happen unless the algorithm above is incorrect
//            throw new BtException("Too much data requested");
//        }

        return new Range(firstRequestedFileIndex, offsetInFirstRequestedFile,
                lastRequestedFileIndex, limitInLastRequestedFile);
    }

    private class Range {

        private int firstRequestedFileIndex;
        private int lastRequestedFileIndex;

        private long offsetInFirstRequestedFile;
        private long limitInLastRequestedFile;

        Range(int firstRequestedFileIndex, long offsetInFirstRequestedFile,
                              int lastRequestedFileIndex, long limitInLastRequestedFile) {

            this.firstRequestedFileIndex = firstRequestedFileIndex;
            this.offsetInFirstRequestedFile = offsetInFirstRequestedFile;
            this.lastRequestedFileIndex = lastRequestedFileIndex;
            this.limitInLastRequestedFile = limitInLastRequestedFile;
        }

        void visitFiles(RangeFileVisitor visitor) {

            long off, lim;

            for (int i = firstRequestedFileIndex; i <= lastRequestedFileIndex; i++) {

                StorageUnit file = units[i];
                off = (i == firstRequestedFileIndex) ? offsetInFirstRequestedFile : 0;
                lim = (i == lastRequestedFileIndex) ? limitInLastRequestedFile : file.capacity();

                visitor.visitFile(file, off, lim);
            }
        }
    }

    private interface RangeFileVisitor {
        /**
         * Visit (a part of) a file in a range of files
         * @param unit A storage unit
         * @param off Offset that designates the beginning of this chunk's part in the file, inclusive;
         *            visitor must not access the file before this index
         * @param lim Limit that designates the end of this chunk's part in the file, exclusive;
         *            visitor must not access the file at or past this index
         *            (i.e. the limit does not belong to this chunk)
         */
        void visitFile(StorageUnit unit, long off, long lim);
    }
}
