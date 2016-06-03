package bt.data;

import java.io.Closeable;

public interface DataAccess extends Closeable {

    byte[] readBlock(long offset, int length);

    void writeBlock(byte[] block, long offset);

    long size();
}
