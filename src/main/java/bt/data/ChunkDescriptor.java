package bt.data;

import bt.BtException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Contains up to {@code Integer.MAX_VALUE} blocks of data of arbitrary size,
 * that can span over up to {@code Integer.MAX_VALUE} files.
 */
public class ChunkDescriptor implements IChunkDescriptor {

    private DataStatus status;
    private DataAccess[] files;

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
     * Bitmask of blocks in this chunk: 1 for complete, 0 for incomplete
     */
    private byte[] bitfield;

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
     * @param files Collection of the torrent files that this chunk overlaps with
     * @param offset Offset from the beginning of the first file in {@code files}; inclusive
     * @param limit Offset from the beginning of the last file in {@code files}; exclusive
     * @param checksum Chunk's hash
     * @param blockSize Size of the read-write transfer block
     */
    public ChunkDescriptor(DataAccess[] files, long offset, long limit,
                           byte[] checksum, long blockSize) {

        if (files.length == 0) {
            throw new BtException("Failed to create chunk descriptor: no files");
        }

        status = DataStatus.EMPTY;

        this.files = files;
        this.offsetInFirstChunkFile = offset;
        this.limitInLastChunkFile = limit;
        this.blockSize = blockSize;
        this.checksum = checksum;

        // using fair lock for now
        readWriteLock = new ReentrantReadWriteLock(true);

        if (files.length == 1) {
            size = limit - offset;
        } else {
            size = files[0].size() - offset;
            for (int i = 1; i < files.length - 1; i++) {
                size += files[i].size();
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
        bitfield = new byte[(int) blockCount];

        fileOffsets = new long[files.length];
        // first "virtual" address (first file begins with offset 0)
        fileOffsets[0] = 0;
        if (files.length > 1) {
            // it's possible that chunk does not have access to the entire first file
            fileOffsets[1] = files[0].size() - offset;
        }
        for (int i = 2; i < files.length; i++) {
            fileOffsets[i] = fileOffsets[i - 1] + files[i - 1].size();
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
    public long getBlockSize() {
        return blockSize;
    }

    @Override
    public byte[] getBitfield() {
        return bitfield;
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

        readWriteLock.readLock().lock();
        try {
            range.visitFiles(new RangeFileVisitor() {

                int offsetInBlock = 0;

                @Override
                public void visitFile(DataAccess file, long off, long lim) {

                    long len = lim - off;
                    if (len > Integer.MAX_VALUE) {
                        throw new BtException("Too much data requested");
                    }

                    byte[] bs = file.readBlock(off, (int) len);
                    if (((long) offsetInBlock) + bs.length > Integer.MAX_VALUE) {
                        // overflow -- isn't supposed to happen unless the algorithm in range is incorrect
                        throw new BtException("Integer overflow while constructing block");
                    }

                    System.arraycopy(bs, 0, block, offsetInBlock, bs.length);
                    offsetInBlock += bs.length;
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
            range.visitFiles(new RangeFileVisitor() {

                int offsetInBlock = 0;
                int limitInBlock;

                @Override
                public void visitFile(DataAccess file, long off, long lim) {

                    long fileSize = lim - off;
                    if (fileSize > Integer.MAX_VALUE) {
                        throw new BtException("Unexpected file size -- insufficient data in block");
                    }

                    limitInBlock = offsetInBlock + (int) fileSize;
                    file.writeBlock(Arrays.copyOfRange(block, offsetInBlock, limitInBlock), off);
                    offsetInBlock = limitInBlock;
                }
            });

            // update bitfield with the info about the new blocks;
            // if only a part of some block is written,
            // then don't count it
            boolean shouldCheckIfComplete = false;
            if (offset + block.length == size) {
                bitfield[bitfield.length - 1] = 1;
                shouldCheckIfComplete = true;
            }
            if (block.length >= blockSize) {
                int numberOfBlocks = (int) Math.floor(((double) block.length) / blockSize);
                if (numberOfBlocks > 0) {
                    int firstBlockIndex = (int) Math.ceil(((double) offset) / blockSize);
                    int lastBlockIndex = (int) Math.floor(((double)(offset + block.length)) / blockSize) - 1;
                    if (lastBlockIndex >= firstBlockIndex) {
                        Arrays.fill(bitfield, firstBlockIndex, lastBlockIndex + 1, (byte) 1);
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

        if (status == DataStatus.VERIFIED) {
            return true;
        } else if (status != DataStatus.COMPLETE) {
            return false;
        }
        // TODO: Implement me

        Range allData = getRange(0, size);
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            // not going to happen
            throw new BtException("Unexpected error", e);
        }

        // block everybody when checking data integrity
        readWriteLock.writeLock().lock();
        try {
            allData.visitFiles((file, off, lim) -> {

                int step = 2 << 12;
                long remaining = lim - off;
                if (remaining > Integer.MAX_VALUE) {
                    throw new BtException("Too much data -- can't read to buffer");
                }
                do {
                    digest.update(file.readBlock(off, Math.min(step, (int) remaining)));
                    remaining -= step;
                    off += step;
                } while (remaining > 0);
            });

            boolean verified = Arrays.equals(checksum, digest.digest());
            if (verified) {
                status = DataStatus.VERIFIED;
            }
            return verified;

        } finally {
            readWriteLock.writeLock().unlock();
        }
    }

    private boolean isComplete() {

        // check if all chunk's blocks are present
        for (byte b : bitfield) {
            if (b == 0) {
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
        for (int i = 0; i < files.length; i++) {

            if (offset < fileOffsets[i]) {
                firstRequestedFileIndex = i - 1;
            } else if (i == files.length - 1) {
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
                remaining -= (files[lastRequestedFileIndex].size() - offsetInFirstRequestedFile);
            } else {
                remaining -= files[lastRequestedFileIndex].size();
            }
        } while (remaining > 0 && ++lastRequestedFileIndex < files.length);

        if (lastRequestedFileIndex >= files.length) {
            // data in this chunk is insufficient to fulfill the block request
            throw new BtException("Insufficient data (offset: " + offset + ", requested block length: " + length + ")");
        }
        // if remaining is negative now, then we need to
        // strip off some data from the last file
        limitInLastRequestedFile = files[lastRequestedFileIndex].size() + remaining;

        if (lastRequestedFileIndex == files.length - 1) {
            if (limitInLastRequestedFile > limitInLastChunkFile) {
                // data in this chunk is insufficient to fulfill the block request
                throw new BtException("Insufficient data (offset: " + offset + ", requested block length: " + length + ")");
            }
        }

        if (limitInLastRequestedFile > Integer.MAX_VALUE) {
            // overflow -- isn't supposed to happen unless the algorithm above is incorrect
            throw new BtException("Too much data requested");
        }

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

                DataAccess file = files[i];
                off = (i == firstRequestedFileIndex) ? offsetInFirstRequestedFile : 0;
                lim = (i == lastRequestedFileIndex) ? limitInLastRequestedFile : file.size();

                visitor.visitFile(file, off, lim);
            }
        }
    }

    private interface RangeFileVisitor {
        /**
         * Visit (a part of) a file in a range of files
         * @param file A file
         * @param off Offset that designates the beginning of this chunk's part in the file, inclusive;
         *            visitor must not access the file before this index
         * @param lim Limit that designates the end of this chunk's part in the file, exclusive;
         *            visitor must not access the file at or past this index
         *            (i.e. the limit does not belong to this chunk)
         */
        void visitFile(DataAccess file, long off, long lim);
    }
}
