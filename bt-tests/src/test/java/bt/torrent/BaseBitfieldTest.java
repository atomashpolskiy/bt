package bt.torrent;

import bt.data.DataStatus;
import bt.data.IChunkDescriptor;
import org.junit.BeforeClass;

import java.util.Arrays;
import java.util.function.Supplier;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

        IChunkDescriptor chunk = mock(IChunkDescriptor.class);
        when(chunk.getSize()).thenReturn(chunkSize);
        when(chunk.getBitfield()).thenReturn(_bitfield);
        when(chunk.getStatus()).then(it ->
                verifier == null? statusForBitfield(_bitfield)
                        : (verifier.get()? DataStatus.VERIFIED : statusForBitfield(_bitfield)));

        when(chunk.verify()).then(it -> verifier == null? false : verifier.get());

        return chunk;
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
