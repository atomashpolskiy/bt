package bt.data;

public interface DataAccess {

    byte[] readBlock(long offset, int length);

    void writeBlock(byte[] block, long offset);

    long size();
}
