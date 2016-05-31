package bt.data;

public interface IChunkDescriptor {

    DataStatus getStatus();

    byte[] readBlock(int offset, int length);

    void writeBlock(byte[] block, int offset);

    boolean verify();
}
