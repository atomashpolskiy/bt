package bt.data;

import org.junit.Test;

import static bt.data.ChunkDescriptorTestUtil.mockStorageUnit;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ChunkDescriptorTest {

    @Test
    public void testChunk_Lifecycle_SingleFile_NoOverlap() {

        long blockSize = 4,
             fileSize = blockSize * 4;

        ChunkDescriptor chunkDescriptor = new DefaultChunkDescriptor(
                new StorageUnit[]{mockStorageUnit(fileSize)}, 0, fileSize, blockSize, new byte[20], null, false);

        assertEquals(DataStatus.EMPTY, chunkDescriptor.getStatus());

        chunkDescriptor.getData().putBytes(new byte[4]);
        assertEquals(DataStatus.INCOMPLETE, chunkDescriptor.getStatus());

        chunkDescriptor.getData().getSubrange(blockSize).putBytes(new byte[4]);
        assertEquals(DataStatus.INCOMPLETE, chunkDescriptor.getStatus());

        chunkDescriptor.getData().getSubrange(blockSize * 2).putBytes(new byte[4]);
        assertEquals(DataStatus.INCOMPLETE, chunkDescriptor.getStatus());

        chunkDescriptor.getData().getSubrange(blockSize * 3).putBytes(new byte[4]);
        assertEquals(DataStatus.COMPLETE, chunkDescriptor.getStatus());

        assertHasBlockStatuses(chunkDescriptor, new byte[]{1,1,1,1});
    }

    @Test
    public void testChunk_Lifecycle_PartialLastBlock_NoOverlap() {

        long blockSize = 4,
             fileSize = blockSize * 2 + 3;

        ChunkDescriptor chunkDescriptor = new DefaultChunkDescriptor(
                new StorageUnit[]{mockStorageUnit(fileSize)}, 0, fileSize, blockSize, new byte[20], null, false);

        assertEquals(DataStatus.EMPTY, chunkDescriptor.getStatus());

        chunkDescriptor.getData().putBytes(new byte[4]);
        assertEquals(DataStatus.INCOMPLETE, chunkDescriptor.getStatus());

        chunkDescriptor.getData().getSubrange(blockSize).putBytes(new byte[4]);
        assertEquals(DataStatus.INCOMPLETE, chunkDescriptor.getStatus());

        chunkDescriptor.getData().getSubrange(blockSize * 2).putBytes(new byte[3]);
        assertEquals(DataStatus.COMPLETE, chunkDescriptor.getStatus());

        assertHasBlockStatuses(chunkDescriptor, new byte[]{1,1,1});
    }

    @Test
    public void testChunk_Lifecycle_PartialLastBlock_Incomplete() {

        long blockSize = 4,
             fileSize = blockSize + 3;

        ChunkDescriptor chunkDescriptor = new DefaultChunkDescriptor(
                new StorageUnit[]{mockStorageUnit(fileSize)}, 0, fileSize, blockSize, new byte[20], null, false);

        assertEquals(DataStatus.EMPTY, chunkDescriptor.getStatus());

        chunkDescriptor.getData().putBytes(new byte[4]);
        assertEquals(DataStatus.INCOMPLETE, chunkDescriptor.getStatus());

        chunkDescriptor.getData().getSubrange(blockSize + 1).putBytes(new byte[2]);
        assertEquals(DataStatus.INCOMPLETE, chunkDescriptor.getStatus());

        assertHasBlockStatuses(chunkDescriptor, new byte[]{1,0});
    }

    @Test
    public void testChunk_Lifecycle_PartialLastBlock_Overlaps() {

        long blockSize = 4,
             fileSize = blockSize * 2 + 3;

        ChunkDescriptor chunkDescriptor = new DefaultChunkDescriptor(
                new StorageUnit[]{mockStorageUnit(fileSize)}, 0, fileSize, blockSize, new byte[20], null, false);

        assertEquals(DataStatus.EMPTY, chunkDescriptor.getStatus());

        chunkDescriptor.getData().putBytes(new byte[4]);
        assertEquals(DataStatus.INCOMPLETE, chunkDescriptor.getStatus());

        chunkDescriptor.getData().getSubrange(blockSize).putBytes(new byte[7]);
        assertEquals(DataStatus.COMPLETE, chunkDescriptor.getStatus());

        assertHasBlockStatuses(chunkDescriptor, new byte[]{1,1,1});
    }

    @Test
    public void testChunk_Lifecycle_MultiFile_NoOverlap() {

        long blockSize = 4,
             fileSize1 = blockSize * 2 - 3,
             fileSize2 = blockSize * 2 + 3;

        ChunkDescriptor chunkDescriptor = new DefaultChunkDescriptor(
                new StorageUnit[]{mockStorageUnit(fileSize1), mockStorageUnit(fileSize2)},
                0, fileSize2, blockSize, new byte[20], null, false);

        assertEquals(DataStatus.EMPTY, chunkDescriptor.getStatus());

        chunkDescriptor.getData().putBytes(new byte[4]);
        assertEquals(DataStatus.INCOMPLETE, chunkDescriptor.getStatus());

        chunkDescriptor.getData().getSubrange(blockSize).putBytes(new byte[4]);
        assertEquals(DataStatus.INCOMPLETE, chunkDescriptor.getStatus());

        chunkDescriptor.getData().getSubrange(blockSize * 2).putBytes(new byte[4]);
        assertEquals(DataStatus.INCOMPLETE, chunkDescriptor.getStatus());

        chunkDescriptor.getData().getSubrange(blockSize * 3).putBytes(new byte[4]);
        assertEquals(DataStatus.COMPLETE, chunkDescriptor.getStatus());

        assertHasBlockStatuses(chunkDescriptor, new byte[]{1,1,1,1});
    }

    @Test
    public void testChunk_Lifecycle_SingleFile_Overlaps() {

        long blockSize = 4,
             fileSize = blockSize * 4;

        ChunkDescriptor chunkDescriptor = new DefaultChunkDescriptor(
                new StorageUnit[]{mockStorageUnit(fileSize)}, 0, fileSize, blockSize, new byte[20], null, false);

        assertEquals(DataStatus.EMPTY, chunkDescriptor.getStatus());

        chunkDescriptor.getData().getSubrange(1).putBytes(new byte[4]);
        assertEquals(DataStatus.EMPTY, chunkDescriptor.getStatus());

        chunkDescriptor.getData().getSubrange(1).putBytes(new byte[7]);
        assertEquals(DataStatus.INCOMPLETE, chunkDescriptor.getStatus());
        assertHasBlockStatuses(chunkDescriptor, new byte[]{0,1,0,0});

        chunkDescriptor.getData().getSubrange(blockSize * 2).putBytes(new byte[6]);
        assertEquals(DataStatus.INCOMPLETE, chunkDescriptor.getStatus());
        assertHasBlockStatuses(chunkDescriptor, new byte[]{0,1,1,0});

        chunkDescriptor.getData().getSubrange(blockSize * 3 + 1).putBytes(new byte[1]);
        assertEquals(DataStatus.INCOMPLETE, chunkDescriptor.getStatus());
        assertHasBlockStatuses(chunkDescriptor, new byte[]{0,1,1,0});

        chunkDescriptor.getData().getSubrange(blockSize - 1).putBytes(new byte[1]);
        assertEquals(DataStatus.INCOMPLETE, chunkDescriptor.getStatus());
        assertHasBlockStatuses(chunkDescriptor, new byte[]{0,1,1,0});

        chunkDescriptor.getData().putBytes(new byte[5]);
        assertEquals(DataStatus.INCOMPLETE, chunkDescriptor.getStatus());
        assertHasBlockStatuses(chunkDescriptor, new byte[]{1,1,1,0});

        chunkDescriptor.getData().getSubrange(blockSize * 3 - 1).putBytes(new byte[5]);
        assertEquals(DataStatus.COMPLETE, chunkDescriptor.getStatus());
        assertHasBlockStatuses(chunkDescriptor, new byte[]{1,1,1,1});
    }

    @Test
    public void testChunk_Lifecycle_MultiFile_Overlaps() {

        long blockSize = 4,
             fileSize1 = blockSize - 1,
             fileSize2 = blockSize + 1,
             fileSize3 = blockSize - 2,
             fileSize4 = blockSize + 2;

        ChunkDescriptor chunkDescriptor = new DefaultChunkDescriptor(
                new StorageUnit[]{mockStorageUnit(fileSize1), mockStorageUnit(fileSize2),
                        mockStorageUnit(fileSize3), mockStorageUnit(fileSize4)},
                0, fileSize4, blockSize, new byte[20], null, false);

        assertEquals(DataStatus.EMPTY, chunkDescriptor.getStatus());

        chunkDescriptor.getData().getSubrange(1).putBytes(new byte[4]);
        assertEquals(DataStatus.EMPTY, chunkDescriptor.getStatus());

        chunkDescriptor.getData().getSubrange(1).putBytes(new byte[7]);
        assertEquals(DataStatus.INCOMPLETE, chunkDescriptor.getStatus());
        assertHasBlockStatuses(chunkDescriptor, new byte[]{0,1,0,0});

        chunkDescriptor.getData().getSubrange(blockSize * 2).putBytes(new byte[6]);
        assertEquals(DataStatus.INCOMPLETE, chunkDescriptor.getStatus());
        assertHasBlockStatuses(chunkDescriptor, new byte[]{0,1,1,0});

        chunkDescriptor.getData().getSubrange(blockSize * 3 + 1).putBytes(new byte[1]);
        assertEquals(DataStatus.INCOMPLETE, chunkDescriptor.getStatus());
        assertHasBlockStatuses(chunkDescriptor, new byte[]{0,1,1,0});

        chunkDescriptor.getData().getSubrange(blockSize - 1).putBytes(new byte[1]);
        assertEquals(DataStatus.INCOMPLETE, chunkDescriptor.getStatus());
        assertHasBlockStatuses(chunkDescriptor, new byte[]{0,1,1,0});

        chunkDescriptor.getData().putBytes(new byte[5]);
        assertEquals(DataStatus.INCOMPLETE, chunkDescriptor.getStatus());
        assertHasBlockStatuses(chunkDescriptor, new byte[]{1,1,1,0});

        chunkDescriptor.getData().getSubrange(blockSize * 3 - 1).putBytes(new byte[5]);
        assertEquals(DataStatus.COMPLETE, chunkDescriptor.getStatus());
        assertHasBlockStatuses(chunkDescriptor, new byte[]{1,1,1,1});
    }

    private static void assertHasBlockStatuses(ChunkDescriptor chunkDescriptor, byte[] blockStatuses) {
        for (int i = 0; i < blockStatuses.length; i++) {
            int status = blockStatuses[i];
            switch (status) {
                case 1: {
                    assertTrue("Expected block at position " + i + " to be verified", chunkDescriptor.isBlockPresent(i));
                    break;
                }
                case 0: {
                    assertFalse("Expected block at position " + i + " to be unverified", chunkDescriptor.isBlockPresent(i));
                    break;
                }
                default: {
                    throw new IllegalArgumentException("Invalid block status at position " + i + ": " + status);
                }
            }
        }
    }
}
