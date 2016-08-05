package bt.data;

import java.io.Closeable;
import java.nio.ByteBuffer;

public interface DataAccess extends Closeable {

    void readBlock(ByteBuffer buffer, long offset);

    byte[] readBlock(long offset, int length);

    void writeBlock(ByteBuffer buffer, long offset);

    void writeBlock(byte[] block, long offset);

    long size();
}
