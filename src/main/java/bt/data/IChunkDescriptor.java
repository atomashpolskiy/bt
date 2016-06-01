package bt.data;

public interface IChunkDescriptor {

    DataStatus getStatus();

    /**
     * Reads a block of data
     * @param offset Offset from the beginning of this chunk
     * @param length Requested block length
     */
    byte[] readBlock(long offset, int length);

    /**
     * Writes a block of data
     * @param block A block to write
     * @param offset Offset from the beginning of this chunk
     */
    void writeBlock(byte[] block, long offset);

    /**
     * Checks integrity of this chunk (if it's status is {@code DataStatus.COMPLETE}
     * @return true if this chunk is complete and succesfully verified
     */
    boolean verify();
}
