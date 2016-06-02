package bt.data;

import bt.BtException;

import java.util.Arrays;

public class ChunkDescriptor implements IChunkDescriptor {

    private DataStatus status;
    private DataAccess[] files;

    private long offsetInFirstChunkFile;
    private long limitInLastChunkFile;
    private long blockSize;

    /**
     * Hash of this chunk's contents
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

        long size;
        if (files.length == 1) {
            size = limit - offset;
        } else {
            size = files[0].size() - offset;
            for (int i = 1; i < files.length - 1; i++) {
                size += files[i].size();
            }
            size += limit;
        }

        int blockCount = (int) Math.ceil(size / blockSize);
        bitfield = new byte[blockCount];

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
    public boolean verify() {
        // TODO: Implement me
        return false;
    }

    @Override
    public DataStatus getStatus() {
        return status;
    }

    @Override
    public byte[] readBlock(long offset, int length) {

        if (length == 0) {
            return new byte[]{};
        }

        byte[] block = new byte[length];

        Range range = getRange(offset, length);
        range.visitFiles(new RangeFileVisitor() {

            int offsetInBlock = 0;

            @Override
            public void visitFile(DataAccess file, long off, long lim) {

                if (lim > Integer.MAX_VALUE) {
                    throw new BtException("Too much data requested");
                }

                byte[] bs = file.readBlock(off, (int) lim);
                if (((long) offsetInBlock) + bs.length > Integer.MAX_VALUE) {
                    // overflow -- isn't supposed to happen unless the algorithm in range is incorrect
                    throw new BtException("Integer overflow while constructing block");
                }

                System.arraycopy(bs, 0, block, offsetInBlock, bs.length);
                offsetInBlock += bs.length;
            }
        });

        return block;
    }

    @Override
    public void writeBlock(byte[] block, long offset) {

        if (block.length == 0) {
            return;
        }

        Range range = getRange(offset, block.length);
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
    }

    private Range getRange(long offset, int length) {

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
        do {
            // determine which files overlap with the requested block
            if (firstRequestedFileIndex == lastRequestedFileIndex) {
                length -= (files[lastRequestedFileIndex].size() - offsetInFirstRequestedFile);
            } else {
                length -= files[lastRequestedFileIndex].size();
            }
        } while (length > 0 && ++lastRequestedFileIndex < files.length);

        if (lastRequestedFileIndex >= files.length) {
            // data in this chunk is insufficient to fulfill the block request
            throw new BtException("Insufficient data (offset: " + offset + ", requested block length: " + length + ")");
        }
        // if length is negative now, then we need to
        // strip off some data from the last file
        limitInLastRequestedFile = files[lastRequestedFileIndex].size() + length;

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

                if (i == lastRequestedFileIndex) {
                    lim = (int) limitInLastRequestedFile;

                } else {

                    long fileSize = file.size();
                    if (fileSize > Integer.MAX_VALUE) {
                        throw new BtException("Unexpected file size -- insufficient data in block");
                    }
                    lim = (int) fileSize;
                }

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

    @Override
    public String toString() {

        // TODO: remove me or move to a different method
        StringBuilder buf = new StringBuilder();
        buf.append("[" + offsetInFirstChunkFile + "..." + limitInLastChunkFile + "]: " + bitfield.length +
                " blocks ~" + (blockSize / (2 << 9)) + " KiB each\n");
        for (int i = 0; i < files.length; i++) {
            buf.append("\tFile #" + (i + 1) + ": " + files[i].toString() + "\n");
        }
        return buf.toString();
    }
}
