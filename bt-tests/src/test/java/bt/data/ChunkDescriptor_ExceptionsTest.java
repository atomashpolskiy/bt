package bt.data;

import org.junit.Test;

import static bt.TestUtil.assertExceptionWithMessage;
import static bt.data.ChunkDescriptorTestUtil.mockStorageUnit;

public class ChunkDescriptor_ExceptionsTest {

    @Test
    public void testExceptions_NoFiles() {
        assertExceptionWithMessage(
                it -> new DefaultChunkDescriptor(new StorageUnit[0], 0, 0, 0, new byte[20]),
                "Failed to create chunk descriptor: no files");
    }

    @Test
    public void testExceptions_TooManyBlocks() {
        assertExceptionWithMessage(
                it -> new DefaultChunkDescriptor(
                        new StorageUnit[]{ChunkDescriptorTestUtil.mockStorageUnit(Long.MAX_VALUE)},
                        0, Long.MAX_VALUE, 1, new byte[20]),
                "Integer overflow while constructing chunk: too many blocks");
    }

    @Test
    public void testExceptions_WriteEmptyBlock_NoException() {
        ChunkDescriptor chunk = new DefaultChunkDescriptor(new StorageUnit[]{mockStorageUnit(10)}, 0, 10, 5, new byte[20]);
        chunk.getData().putBytes(new byte[0]);
    }
}
