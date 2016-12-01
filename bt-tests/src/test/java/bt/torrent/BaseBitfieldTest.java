package bt.torrent;

import bt.data.DataStatus;
import bt.data.IChunkDescriptor;
import org.junit.BeforeClass;

import java.util.Arrays;
import java.util.function.Supplier;

public abstract class BaseBitfieldTest {

    protected static long blockSize = 4;
    protected static long chunkSize = blockSize * 4;

    protected static IChunkDescriptor emptyChunk, completeChunk;

    @BeforeClass
    public static void setUp() {
        emptyChunk = mockChunk(chunkSize, new byte[]{0,0,0,0}, null);
        completeChunk = mockChunk(chunkSize, new byte[]{1,1,1,1}, null);
    }

    protected static IChunkDescriptor mockChunk(long chunkSize, byte[] bitfield,
                                              Supplier<Boolean> verifier) {

        byte[] _bitfield = Arrays.copyOf(bitfield, bitfield.length);

        return new IChunkDescriptor() {
            @Override
            public DataStatus getStatus() {
                return (verifier == null) ? statusForBitfield(_bitfield)
                        : (verifier.get()? DataStatus.VERIFIED : statusForBitfield(_bitfield));
            }

            @Override
            public long getSize() {
                return chunkSize;
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
            public boolean isBlockVerified(int blockIndex) {
                return _bitfield[blockIndex] == 1;
            }

            @Override
            public byte[] readBlock(long offset, int length) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void writeBlock(byte[] block, long offset) {
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
