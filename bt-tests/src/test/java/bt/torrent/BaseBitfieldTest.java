package bt.torrent;

import bt.data.ChunkDescriptor;
import bt.data.DataRange;
import org.junit.BeforeClass;

import java.util.Arrays;

public abstract class BaseBitfieldTest {

    protected static long blockSize = 4;

    protected static ChunkDescriptor emptyChunk, completeChunk;

    @BeforeClass
    public static void setUp() {
        emptyChunk = mockChunk(new byte[]{0,0,0,0});
        completeChunk = mockChunk(new byte[]{1,1,1,1});
    }

    protected static ChunkDescriptor mockChunk(byte[] bitfield) {

        byte[] _bitfield = Arrays.copyOf(bitfield, bitfield.length);

        return new ChunkDescriptor() {

            @Override
            public byte[] getChecksum() {
                return new byte[0];
            }

            @Override
            public int blockCount() {
                return _bitfield.length;
            }

            @Override
            public long length() {
                return blockSize * blockCount();
            }

            @Override
            public long blockSize() {
                return blockSize;
            }

            @Override
            public long lastBlockSize() {
                return length() % blockSize();
            }

            @Override
            public boolean isPresent(int blockIndex) {
                return _bitfield[blockIndex] == 1;
            }

            @Override
            public boolean isComplete() {
                for (byte b : _bitfield) {
                    if (b != 1) {
                        return false;
                    }
                }
                return true;
            }

            @Override
            public boolean isEmpty() {
                for (byte b : _bitfield) {
                    if (b == 1) {
                        return false;
                    }
                }
                return true;
            }

            @Override
            public DataRange getData() {
                throw new UnsupportedOperationException();
            }
        };
    }
}
