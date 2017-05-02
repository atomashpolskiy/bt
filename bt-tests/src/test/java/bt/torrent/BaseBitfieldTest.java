package bt.torrent;

import bt.data.DataRange;
import bt.data.DataStatus;
import bt.data.ChunkDescriptor;
import org.junit.BeforeClass;

import java.util.Arrays;
import java.util.function.Supplier;

public abstract class BaseBitfieldTest {

    protected static long blockSize = 4;

    protected static ChunkDescriptor emptyChunk, completeChunk;

    @BeforeClass
    public static void setUp() {
        emptyChunk = mockChunk(new byte[]{0,0,0,0}, null);
        completeChunk = mockChunk(new byte[]{1,1,1,1}, null);
    }

    protected static ChunkDescriptor mockChunk(byte[] bitfield, Supplier<Boolean> verifier) {

        byte[] _bitfield = Arrays.copyOf(bitfield, bitfield.length);

        return new ChunkDescriptor() {
            @Override
            public DataStatus getStatus() {
                return (verifier == null) ? statusForBitfield(_bitfield)
                        : (verifier.get()? DataStatus.VERIFIED : statusForBitfield(_bitfield));
            }

            @Override
            public int getBlockCount() {
                return _bitfield.length;
            }

            @Override
            public long getBlockSize() {
                return blockSize;
            }

            @Override
            public boolean isBlockPresent(int blockIndex) {
                return _bitfield[blockIndex] == 1;
            }

            @Override
            public DataRange getData() {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean verify() {
                return (verifier == null) ? false : verifier.get();
            }
        };
    }

    private static DataStatus statusForBitfield(byte[] bitfield) {

        if (bitfield.length == 0) {
            throw new RuntimeException("Empty bitfield");
        }

        byte first = bitfield[0];
        for (byte b : bitfield) {
            if (b != first) {
                return DataStatus.INCOMPLETE;
            }
        }
        return first == 0? DataStatus.EMPTY : DataStatus.VERIFIED;
    }
}
