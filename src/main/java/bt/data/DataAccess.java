package bt.data;

public interface DataAccess {

    byte[] readBlock(int offset, int length);

    void writeBlock(byte[] block, int offset);

    long size();
}
