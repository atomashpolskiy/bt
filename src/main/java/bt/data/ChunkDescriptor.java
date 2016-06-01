package bt.data;

import bt.BtException;

import java.util.List;

public class ChunkDescriptor implements IChunkDescriptor {

    private DataStatus status;
    private List<DataAccess> files;
    private long offset;
    private long limit;
    private long blockSize;
    private byte[] checksum;
    private byte[] bitfield;

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
    public ChunkDescriptor(List<DataAccess> files, long offset, long limit,
                           byte[] checksum, long blockSize) {

        if (files.isEmpty()) {
            throw new BtException("Failed to create chunk descriptor: no files");
        }

        status = DataStatus.EMPTY;
        this.files = files;
        this.offset = offset;
        this.limit = limit;
        this.blockSize = blockSize;
        this.checksum = checksum;

        long size;
        if (files.size() == 1) {
            size = limit - offset;
        } else {
            size = files.get(0).size() - offset;
            for (int i = 1; i < files.size() - 1; i++) {
                size += files.get(i).size();
            }
            size += limit;
        }

        int blockCount = (int) Math.ceil(size / blockSize);
        bitfield = new byte[blockCount];
    }

    @Override
    public DataStatus getStatus() {
        return status;
    }

    @Override
    public byte[] readBlock(int offset, int length) {
        // TODO: Implement me
        return new byte[0];
    }

    @Override
    public void writeBlock(byte[] block, int offset) {
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
        buf.append("[" + offset + "..." + limit + "]: " + bitfield.length + " blocks ~" + (blockSize / (2 << 9)) + " KiB each\n");
        for (int i = 0; i < files.size(); i++) {
            buf.append("\tFile #" + (i + 1) + ": " + files.get(i).toString() + "\n");
        }
        return buf.toString();
    }
}
