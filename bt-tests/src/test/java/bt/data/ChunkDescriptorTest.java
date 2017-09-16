/*
 * Copyright (c) 2016—2017 Andrei Tomashpolskiy and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package bt.data;

import org.junit.Test;

import static bt.data.ChunkDescriptorTestUtil.buildChunk;
import static bt.data.ChunkDescriptorTestUtil.mockStorageUnits;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ChunkDescriptorTest {

    @Test
    public void testChunk_Lifecycle_SingleFile_NoOverlap() {

        long blockSize = 4,
             fileSize = blockSize * 4;

        ChunkDescriptor chunkDescriptor = buildChunk(mockStorageUnits(fileSize), blockSize);

        assertTrue(chunkDescriptor.isEmpty());

        chunkDescriptor.getData().putBytes(new byte[4]);
        assertFalse(chunkDescriptor.isEmpty() || chunkDescriptor.isComplete());

        chunkDescriptor.getData().getSubrange(blockSize).putBytes(new byte[4]);
        assertFalse(chunkDescriptor.isEmpty() || chunkDescriptor.isComplete());

        chunkDescriptor.getData().getSubrange(blockSize * 2).putBytes(new byte[4]);
        assertFalse(chunkDescriptor.isEmpty() || chunkDescriptor.isComplete());

        chunkDescriptor.getData().getSubrange(blockSize * 3).putBytes(new byte[4]);
        assertTrue(chunkDescriptor.isComplete());

        assertHasBlockStatuses(chunkDescriptor, new byte[]{1,1,1,1});
    }

    @Test
    public void testChunk_Lifecycle_PartialLastBlock_NoOverlap() {

        long blockSize = 4,
             fileSize = blockSize * 2 + 3;

        ChunkDescriptor chunkDescriptor = buildChunk(mockStorageUnits(fileSize), blockSize);

        assertTrue(chunkDescriptor.isEmpty());

        chunkDescriptor.getData().putBytes(new byte[4]);
        assertFalse(chunkDescriptor.isEmpty() || chunkDescriptor.isComplete());

        chunkDescriptor.getData().getSubrange(blockSize).putBytes(new byte[4]);
        assertFalse(chunkDescriptor.isEmpty() || chunkDescriptor.isComplete());

        chunkDescriptor.getData().getSubrange(blockSize * 2).putBytes(new byte[3]);
        assertTrue(chunkDescriptor.isComplete());

        assertHasBlockStatuses(chunkDescriptor, new byte[]{1,1,1});
    }

    @Test
    public void testChunk_Lifecycle_PartialLastBlock_Incomplete() {

        long blockSize = 4,
             fileSize = blockSize + 3;

        ChunkDescriptor chunkDescriptor = buildChunk(mockStorageUnits(fileSize), blockSize);

        assertTrue(chunkDescriptor.isEmpty());

        chunkDescriptor.getData().putBytes(new byte[4]);
        assertFalse(chunkDescriptor.isEmpty() || chunkDescriptor.isComplete());

        chunkDescriptor.getData().getSubrange(blockSize + 1).putBytes(new byte[2]);
        assertFalse(chunkDescriptor.isEmpty() || chunkDescriptor.isComplete());

        assertHasBlockStatuses(chunkDescriptor, new byte[]{1,0});
    }

    @Test
    public void testChunk_Lifecycle_PartialLastBlock_Overlaps() {

        long blockSize = 4,
             fileSize = blockSize * 2 + 3;

        ChunkDescriptor chunkDescriptor = buildChunk(mockStorageUnits(fileSize), blockSize);

        assertTrue(chunkDescriptor.isEmpty());

        chunkDescriptor.getData().putBytes(new byte[4]);
        assertFalse(chunkDescriptor.isEmpty() || chunkDescriptor.isComplete());

        chunkDescriptor.getData().getSubrange(blockSize).putBytes(new byte[7]);
        assertTrue(chunkDescriptor.isComplete());

        assertHasBlockStatuses(chunkDescriptor, new byte[]{1,1,1});
    }

    @Test
    public void testChunk_Lifecycle_MultiFile_NoOverlap() {

        long blockSize = 4,
             fileSize1 = blockSize * 2 - 3,
             fileSize2 = blockSize * 2 + 3;

        ChunkDescriptor chunkDescriptor = buildChunk(mockStorageUnits(fileSize1, fileSize2), blockSize);

        assertTrue(chunkDescriptor.isEmpty());

        chunkDescriptor.getData().putBytes(new byte[4]);
        assertFalse(chunkDescriptor.isEmpty() || chunkDescriptor.isComplete());

        chunkDescriptor.getData().getSubrange(blockSize).putBytes(new byte[4]);
        assertFalse(chunkDescriptor.isEmpty() || chunkDescriptor.isComplete());

        chunkDescriptor.getData().getSubrange(blockSize * 2).putBytes(new byte[4]);
        assertFalse(chunkDescriptor.isEmpty() || chunkDescriptor.isComplete());

        chunkDescriptor.getData().getSubrange(blockSize * 3).putBytes(new byte[4]);
        assertTrue(chunkDescriptor.isComplete());

        assertHasBlockStatuses(chunkDescriptor, new byte[]{1,1,1,1});
    }

    @Test
    public void testChunk_Lifecycle_SingleFile_Overlaps() {

        long blockSize = 4,
             fileSize = blockSize * 4;

        ChunkDescriptor chunkDescriptor = buildChunk(mockStorageUnits(fileSize), blockSize);

        assertTrue(chunkDescriptor.isEmpty());

        chunkDescriptor.getData().getSubrange(1).putBytes(new byte[4]);
        assertTrue(chunkDescriptor.isEmpty());

        chunkDescriptor.getData().getSubrange(1).putBytes(new byte[7]);
        assertFalse(chunkDescriptor.isEmpty() || chunkDescriptor.isComplete());
        assertHasBlockStatuses(chunkDescriptor, new byte[]{0,1,0,0});

        chunkDescriptor.getData().getSubrange(blockSize * 2).putBytes(new byte[6]);
        assertFalse(chunkDescriptor.isEmpty() || chunkDescriptor.isComplete());
        assertHasBlockStatuses(chunkDescriptor, new byte[]{0,1,1,0});

        chunkDescriptor.getData().getSubrange(blockSize * 3 + 1).putBytes(new byte[1]);
        assertFalse(chunkDescriptor.isEmpty() || chunkDescriptor.isComplete());
        assertHasBlockStatuses(chunkDescriptor, new byte[]{0,1,1,0});

        chunkDescriptor.getData().getSubrange(blockSize - 1).putBytes(new byte[1]);
        assertFalse(chunkDescriptor.isEmpty() || chunkDescriptor.isComplete());
        assertHasBlockStatuses(chunkDescriptor, new byte[]{0,1,1,0});

        chunkDescriptor.getData().putBytes(new byte[5]);
        assertFalse(chunkDescriptor.isEmpty() || chunkDescriptor.isComplete());
        assertHasBlockStatuses(chunkDescriptor, new byte[]{1,1,1,0});

        chunkDescriptor.getData().getSubrange(blockSize * 3 - 1).putBytes(new byte[5]);
        assertTrue(chunkDescriptor.isComplete());
        assertHasBlockStatuses(chunkDescriptor, new byte[]{1,1,1,1});
    }

    @Test
    public void testChunk_Lifecycle_MultiFile_Overlaps() {

        long blockSize = 4,
             fileSize1 = blockSize - 1,
             fileSize2 = blockSize + 1,
             fileSize3 = blockSize - 2,
             fileSize4 = blockSize + 2;

        ChunkDescriptor chunkDescriptor = buildChunk(mockStorageUnits(fileSize1, fileSize2, fileSize3, fileSize4), blockSize);

        assertTrue(chunkDescriptor.isEmpty());

        chunkDescriptor.getData().getSubrange(1).putBytes(new byte[4]);
        assertTrue(chunkDescriptor.isEmpty());

        chunkDescriptor.getData().getSubrange(1).putBytes(new byte[7]);
        assertFalse(chunkDescriptor.isEmpty() || chunkDescriptor.isComplete());
        assertHasBlockStatuses(chunkDescriptor, new byte[]{0,1,0,0});

        chunkDescriptor.getData().getSubrange(blockSize * 2).putBytes(new byte[6]);
        assertFalse(chunkDescriptor.isEmpty() || chunkDescriptor.isComplete());
        assertHasBlockStatuses(chunkDescriptor, new byte[]{0,1,1,0});

        chunkDescriptor.getData().getSubrange(blockSize * 3 + 1).putBytes(new byte[1]);
        assertFalse(chunkDescriptor.isEmpty() || chunkDescriptor.isComplete());
        assertHasBlockStatuses(chunkDescriptor, new byte[]{0,1,1,0});

        chunkDescriptor.getData().getSubrange(blockSize - 1).putBytes(new byte[1]);
        assertFalse(chunkDescriptor.isEmpty() || chunkDescriptor.isComplete());
        assertHasBlockStatuses(chunkDescriptor, new byte[]{0,1,1,0});

        chunkDescriptor.getData().putBytes(new byte[5]);
        assertFalse(chunkDescriptor.isEmpty() || chunkDescriptor.isComplete());
        assertHasBlockStatuses(chunkDescriptor, new byte[]{1,1,1,0});

        chunkDescriptor.getData().getSubrange(blockSize * 3 - 1).putBytes(new byte[5]);
        assertTrue(chunkDescriptor.isComplete());
        assertHasBlockStatuses(chunkDescriptor, new byte[]{1,1,1,1});
    }

    private static void assertHasBlockStatuses(ChunkDescriptor chunkDescriptor, byte[] blockStatuses) {
        for (int i = 0; i < blockStatuses.length; i++) {
            int status = blockStatuses[i];
            switch (status) {
                case 1: {
                    assertTrue("Expected block at position " + i + " to be verified", chunkDescriptor.isPresent(i));
                    break;
                }
                case 0: {
                    assertFalse("Expected block at position " + i + " to be unverified", chunkDescriptor.isPresent(i));
                    break;
                }
                default: {
                    throw new IllegalArgumentException("Invalid block status at position " + i + ": " + status);
                }
            }
        }
    }
}
