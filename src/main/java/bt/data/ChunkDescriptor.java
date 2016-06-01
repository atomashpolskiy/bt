package bt.data;

import bt.BtException;

public class ChunkDescriptor implements IChunkDescriptor {

    private DataStatus status;
    private DataAccess[] files;

    private long offsetInFirstChunkFile;
    private long limitInLastChunkFile;
    private long blockSize;

    private byte[] checksum;
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
    public DataStatus getStatus() {
        return status;
    }

    @Override
    public byte[] readBlock(long offset, int length) {

        // parameters for read requests are arbitrary

        if (offset < 0 || length < 0) {
            throw new BtException("Illegal arguments: offset (" + offset + "), length (" + length + ")");
        }

        if (length == 0) {
            return new byte[]{};
        }

        int firstRequestedFileIndex,
            lastRequestedFileIndex;

        long offsetInFirstRequestedFile,
             limitInLastRequestedFile;

        // determine the file that the requested block begins in
        firstRequestedFileIndex = -1;
        for (int i = 0; i < files.length; i++) {

            if (i == files.length - 1) {
                // reached the last file
                firstRequestedFileIndex = i;

            } else if (offset < fileOffsets[i]) {
                firstRequestedFileIndex = i - 1;
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
            if (lastRequestedFileIndex >= files.length) {
                // data in this chunk is insufficient to fulfill the block request
                throw new BtException("Insufficient data (offset: " + offset + ", requested block length: " + length + ")");
            }
            // determine which files overlap with the requested block
            if (firstRequestedFileIndex == lastRequestedFileIndex) {
                length -= (files[lastRequestedFileIndex].size() - offsetInFirstRequestedFile);
            } else {
                length -= files[lastRequestedFileIndex].size();
            }
            lastRequestedFileIndex++;
        } while (length > 0);
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

        /* access firstRequestedFile[offsetInFirstRequestedFile]...lastRequestedFile[limitInLastRequestedFile] */

        byte[] block = new byte[length];
        byte[] bs;
        long off;
        int lim;
        int dest = 0;

        for (int i = firstRequestedFileIndex; i <= lastRequestedFileIndex; i++) {

            // collect data from the files
            DataAccess file = files[i];
            off = (i == firstRequestedFileIndex) ? offsetInFirstRequestedFile : 0;

            if (i == lastRequestedFileIndex) {
                lim = (int) limitInLastRequestedFile;

            } else {

                long fileSize = file.size();
                if (fileSize > Integer.MAX_VALUE) {
                    throw new BtException("Too much data requested");
                }
                lim = (int) fileSize;
            }

            bs = file.readBlock(off, lim);
            if (((long) dest) + bs.length > Integer.MAX_VALUE) {
                // overflow -- isn't supposed to happen unless the algorithm above is incorrect
                throw new BtException("Integer overflow while constructing block");
            }
            System.arraycopy(bs, 0, block, dest, bs.length);
            dest += bs.length;
        }

        return block;
    }

    @Override
    public void writeBlock(byte[] block, long offset) {
        // TODO: Implement me
    }

    @Override
    public boolean verify() {
        // TODO: Implement me
        return false;
    }

    @Override
    public String toString() {

        // TODO: remove me or move to a different method
        StringBuilder buf = new StringBuilder();
        buf.append("[" + offsetInFirstChunkFile + "..." + limitInLastChunkFile + "]: " + bitfield.length + " blocks ~" + (blockSize / (2 << 9)) + " KiB each\n");
        for (int i = 0; i < files.length; i++) {
            buf.append("\tFile #" + (i + 1) + ": " + files[i].toString() + "\n");
        }
        return buf.toString();
    }
}
