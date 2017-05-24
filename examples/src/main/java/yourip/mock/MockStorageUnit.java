package yourip.mock;

import bt.data.StorageUnit;

import java.io.IOException;
import java.nio.ByteBuffer;

public class MockStorageUnit implements StorageUnit {

    @Override
    public void readBlock(ByteBuffer buffer, long offset) {
        // do nothing
    }

    @Override
    public byte[] readBlock(long offset, int length) {
        return new byte[0];
    }

    @Override
    public void writeBlock(ByteBuffer buffer, long offset) {
        // do nothing
    }

    @Override
    public void writeBlock(byte[] block, long offset) {
        // do nothing
    }

    @Override
    public long capacity() {
        return 1;
    }

    @Override
    public long size() {
        return 1;
    }

    @Override
    public void close() throws IOException {
        // do nothing
    }
}
