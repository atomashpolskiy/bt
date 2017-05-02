package bt.data;

import org.junit.Test;

import static bt.TestUtil.assertExceptionWithMessage;
import static bt.data.ChunkDescriptorTestUtil.mockStorageUnit;
import static org.junit.Assert.assertEquals;

public class ChunkDescriptor_ExceptionsTest {

    @Test
    public void testExceptions_NoFiles() {
        assertExceptionWithMessage(
                it -> new DefaultChunkDescriptor(new StorageUnit[0], 0, 0, 0, new byte[20], null, false),
                "Failed to create chunk descriptor: no files");
    }

    @Test
    public void testExceptions_TooManyBlocks() {
        assertExceptionWithMessage(
                it -> new DefaultChunkDescriptor(new StorageUnit[1], 0, Long.MAX_VALUE, 1, new byte[20], null, false),
                "Integer overflow while constructing chunk: too many blocks");
    }

    @Test
    public void testExceptions_WriteEmptyBlock_NoException() {
        ChunkDescriptor chunk = new DefaultChunkDescriptor(new StorageUnit[]{mockStorageUnit(10)}, 0, 10, 5, new byte[20], null, false);
        chunk.getData().putBytes(new byte[0]);
    }

    @Test
    // TODO: move this to ReadWriteDataRangeTest
    public void testExceptions_Write_NegativeOffset() {
        ChunkDescriptor chunk = new DefaultChunkDescriptor(new StorageUnit[]{mockStorageUnit(1)}, 0, 1, 1, new byte[20], null, false);
        assertExceptionWithMessage(
                it -> {chunk.getData().getSubrange(-1).putBytes(new byte[1]); return null;},
                "Illegal arguments: offset (-1)");
    }

    @Test
    // TODO: move this to ReadWriteDataRangeTest
    public void testExceptions_Read_NegativeOffset() {
        ChunkDescriptor chunk = new DefaultChunkDescriptor(new StorageUnit[]{mockStorageUnit(1)}, 0, 1, 1, new byte[20], null, false);
        assertExceptionWithMessage(
                it -> chunk.getData().getSubrange(-1, 1),
                "Illegal arguments: offset (-1), length (1)");
    }

    @Test
    // TODO: move this to ReadWriteDataRangeTest
    public void testExceptions_Read_NegativeLength() {
        ChunkDescriptor chunk = new DefaultChunkDescriptor(new StorageUnit[]{mockStorageUnit(1)}, 0, 1, 1, new byte[20], null, false);
        assertExceptionWithMessage(
                it -> chunk.getData().getSubrange(0, -1),
                "Illegal arguments: offset (0), length (-1)");
    }

    @Test
    // TODO: move this to ReadWriteDataRangeTest
    public void testExceptions_Read_ZeroLength_Exception() {
        ChunkDescriptor chunk = new DefaultChunkDescriptor(new StorageUnit[]{mockStorageUnit(1)}, 0, 1, 1, new byte[20], null, false);
        assertExceptionWithMessage(
                it -> chunk.getData().getSubrange(0, 0),
                "Illegal arguments: offset (0), length (0)");
    }

    @Test
    // TODO: move this to ReadWriteDataRangeTest
    public void testExceptions_Read_RequestedTooMuchData() {
        ChunkDescriptor chunk = new DefaultChunkDescriptor(new StorageUnit[]{mockStorageUnit(1)}, 0, 1, 1, new byte[20], null, false);
        assertExceptionWithMessage(
                it -> chunk.getData().getSubrange(0, Integer.MAX_VALUE),
                "Insufficient data (offset: 0, requested block length: 2147483647)");
    }
}
