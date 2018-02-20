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

import bt.TestUtil;
import bt.data.digest.Digester;
import bt.data.digest.SHA1Digester;
import bt.metainfo.Torrent;
import bt.protocol.BitOrder;
import bt.service.CryptoUtil;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import static bt.data.ChunkDescriptorTestUtil.assertFileHasContents;
import static bt.data.ChunkDescriptorTestUtil.mockTorrent;
import static bt.data.ChunkDescriptorTestUtil.mockTorrentFile;
import static bt.data.ChunkDescriptorTestUtil.writeBytesToFile;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ChunkDescriptor_FileStorageUnitTest {

    @Rule
    public TestFileSystemStorage storage = new TestFileSystemStorage();

    private ChunkVerifier verifier;
    private IDataDescriptorFactory dataDescriptorFactory;

    @Before
    public void before() {
        int step = 8;
        Digester digester = SHA1Digester.rolling(step);
        int numOfHashingThreads = 4;
        this.verifier = new DefaultChunkVerifier(digester, numOfHashingThreads);
        int transferBlockSize = 4;
        this.dataDescriptorFactory = new DataDescriptorFactory(verifier, transferBlockSize);
    }

    /**************************************************************************************/

    private byte[] SINGLE_FILE = new byte[] {
            1,2,3,4,5,6,7,8,1,2,3,4,5,6,7,8,
            1,2,3,4,1,2,3,4,1,2,3,4,1,2,3,4,
            1,2,1,2,3,1,2,3,4,5,6,7,8,9,1,2,
            1,2,3,4,1,2,3,4,1,2,3,4,1,2,3,4
    };

    private DataDescriptor createDataDescriptor_SingleFile(String fileName) {

        long chunkSize = 16;
        long fileSize = chunkSize * 4;

        Torrent torrent = mockTorrent(fileName, fileSize, chunkSize,
                new byte[][] {
                        CryptoUtil.getSha1Digest(Arrays.copyOfRange(SINGLE_FILE, 0, 16)),
                        CryptoUtil.getSha1Digest(Arrays.copyOfRange(SINGLE_FILE, 16, 32)),
                        CryptoUtil.getSha1Digest(Arrays.copyOfRange(SINGLE_FILE, 32, 48)),
                        CryptoUtil.getSha1Digest(Arrays.copyOfRange(SINGLE_FILE, 48, 64)),
                },
                mockTorrentFile(fileSize, fileName));

        DataDescriptor descriptor = dataDescriptorFactory.createDescriptor(torrent, storage);
        assertEquals(4, descriptor.getChunkDescriptors().size());

        return descriptor;
    }

    @Test
    public void testDescriptors_WriteSingleFile() {

        String fileName = "1-single.bin";
        DataDescriptor descriptor = createDataDescriptor_SingleFile(fileName);
        List<ChunkDescriptor> chunks = descriptor.getChunkDescriptors();

        chunks.get(0).getData().putBytes(TestUtil.sequence(8));
        chunks.get(0).getData().getSubrange(8).putBytes(TestUtil.sequence(8));
        assertTrue(chunks.get(0).isComplete());
        assertTrue(verifier.verify(chunks.get(0)));

        chunks.get(1).getData().putBytes(TestUtil.sequence(4));
        chunks.get(1).getData().getSubrange(4).putBytes(TestUtil.sequence(4));
        chunks.get(1).getData().getSubrange(8).putBytes(TestUtil.sequence(4));
        chunks.get(1).getData().getSubrange(12).putBytes(TestUtil.sequence(4));
        assertTrue(chunks.get(1).isComplete());
        assertTrue(verifier.verify(chunks.get(1)));

        // reverse order
        chunks.get(2).getData().getSubrange(5).putBytes(TestUtil.sequence(11));
        chunks.get(2).getData().getSubrange(2).putBytes(TestUtil.sequence(3));
        chunks.get(2).getData().putBytes(TestUtil.sequence(2));
        assertFalse(chunks.get(2).isComplete());

        // "random" order
        chunks.get(3).getData().getSubrange(4).putBytes(TestUtil.sequence(4));
        chunks.get(3).getData().getSubrange(0).putBytes(TestUtil.sequence(4));
        chunks.get(3).getData().getSubrange(12).putBytes(TestUtil.sequence(4));
        chunks.get(3).getData().getSubrange(8).putBytes(TestUtil.sequence(4));
        assertTrue(chunks.get(3).isComplete());
        assertTrue(verifier.verify(chunks.get(3)));

        assertFileHasContents(new File(storage.getRoot(), fileName), SINGLE_FILE);
    }

    /**************************************************************************************/

    private DataDescriptor createDataDescriptor_SingleEmptyFile(String fileName) {

        long chunkSize = 16;
        long fileSize = 0;

        byte[][] chunkHashes = new byte[0][];
        Torrent torrent = mockTorrent(fileName, fileSize, chunkSize, chunkHashes,
                mockTorrentFile(fileSize, fileName));

        DataDescriptor descriptor = dataDescriptorFactory.createDescriptor(torrent, storage);
        assertEquals(0, descriptor.getChunkDescriptors().size());

        return descriptor;
    }

    @Test
    public void testDescriptors_WriteSingleEmptyFile() {

        String fileName = "1-single-empty.bin";
        DataDescriptor descriptor = createDataDescriptor_SingleEmptyFile(fileName);
        List<ChunkDescriptor> chunks = descriptor.getChunkDescriptors();
        assertTrue(chunks.isEmpty());
        assertEquals(0, descriptor.getBitfield().getPiecesRemaining());
        assertEquals(0, descriptor.getBitfield().getPiecesTotal());
        assertEquals(0, descriptor.getBitfield().getPiecesComplete());
        assertEquals(0, descriptor.getBitfield().getBitmask().size());
        assertEquals(0, descriptor.getBitfield().toByteArray(BitOrder.LITTLE_ENDIAN).length);

        assertFileHasContents(new File(storage.getRoot(), fileName), new byte[0]);
    }

    /**************************************************************************************/

    @Test
    public void testDescriptors_ReadSingleFile() {

        String fileName = "1-single-read.bin";
        writeBytesToFile(new File(storage.getRoot(), fileName), SINGLE_FILE);

        DataDescriptor descriptor = createDataDescriptor_SingleFile(fileName);
        List<ChunkDescriptor> chunks = descriptor.getChunkDescriptors();

        byte[] block;

        // beginning
        block = chunks.get(0).getData().getSubrange(0, 8).getBytes();
        assertArrayEquals(Arrays.copyOfRange(SINGLE_FILE, 0, 8), block);

        // end
        block = chunks.get(0).getData().getSubrange(8, 8).getBytes();
        assertArrayEquals(Arrays.copyOfRange(SINGLE_FILE, 8, 16), block);

        // whole chunk
        block = chunks.get(0).getData().getSubrange(0, 16).getBytes();
        assertArrayEquals(Arrays.copyOfRange(SINGLE_FILE, 0, 16), block);

        // piece
        block = chunks.get(0).getData().getSubrange(1, 14).getBytes();
        assertArrayEquals(Arrays.copyOfRange(SINGLE_FILE, 1, 15), block);
    }

    /**************************************************************************************/

    private byte[] MULTI_FILE_1 = new byte[] {
            1,2,3,4,5,6,7,8,1,2,3,4,5,6,7,8,
            1,2,3,4,1,2,3,4,1
    };
    private byte[] MULTI_FILE_2 = new byte[] {
                              2,3,4,1,2,3,4,
            1,2,1,2,3,1,2,3,4,5,6
    };
    private byte[] MULTI_FILE_3 = new byte[] {
                                  7,8,9,1,2
    };
    private byte[] MULTI_FILE_4 = new byte[] {
            1,2,3,4,1,2,3,4,1,2,3,4,1,2,3,4,
            1,2,3,4,5,6,7,8,9,1,2,3,4,5,6
    };
    private byte[] MULTI_FILE_5 = new byte[] {
                                          7,
            1,1,2,3,4,5,6,7,8,9,1,2,3,4,5
    };
    private byte[] MULTI_FILE_6 = new byte[] {
                                          1
    };

    private DataDescriptor createDataDescriptor_MultiFile(String fileName1, String fileName2, String fileName3,
                                                          String fileName4, String fileName5, String fileName6,
                                                          File parentDirectory) {

        String torrentName = parentDirectory.getName();

        long chunkSize = 16;
        long torrentSize = chunkSize * 6;
        long fileSize1 = 25,
             fileSize2 = 18,
             fileSize3 = 5,
             fileSize4 = 31,
             fileSize5 = 16,
             fileSize6 = 1;

        // sanity check
        assertEquals(torrentSize, fileSize1 + fileSize2 + fileSize3 + fileSize4 + fileSize5 + fileSize6);

        byte[] chunk0Hash = CryptoUtil.getSha1Digest(Arrays.copyOfRange(MULTI_FILE_1, 0, 16));

        byte[] chunk1 = new byte[(int) chunkSize];
        System.arraycopy(MULTI_FILE_1, 16, chunk1, 0, 9);
        System.arraycopy(MULTI_FILE_2, 0, chunk1, 9, 7);
        byte[] chunk1Hash = CryptoUtil.getSha1Digest(chunk1);

        byte[] chunk2 = new byte[(int) chunkSize];
        System.arraycopy(MULTI_FILE_2, 7, chunk2, 0, 11);
        System.arraycopy(MULTI_FILE_3, 0, chunk2, 11, 5);
        byte[] chunk2Hash = CryptoUtil.getSha1Digest(chunk2);

        byte[] chunk3 = new byte[(int) chunkSize];
        System.arraycopy(MULTI_FILE_4, 0, chunk3, 0, 16);
        byte[] chunk3Hash = CryptoUtil.getSha1Digest(chunk3);

        byte[] chunk4 = new byte[(int) chunkSize];
        System.arraycopy(MULTI_FILE_4, 16, chunk4, 0, 15);
        System.arraycopy(MULTI_FILE_5, 0, chunk4, 15, 1);
        byte[] chunk4Hash = CryptoUtil.getSha1Digest(chunk4);

        byte[] chunk5 = new byte[(int) chunkSize];
        System.arraycopy(MULTI_FILE_5, 1, chunk5, 0, 15);
        System.arraycopy(MULTI_FILE_6, 0, chunk5, 15, 1);
        byte[] chunk5Hash = CryptoUtil.getSha1Digest(chunk5);

        Torrent torrent = mockTorrent(torrentName, torrentSize, chunkSize,
                new byte[][] {chunk0Hash, chunk1Hash, chunk2Hash, chunk3Hash, chunk4Hash, chunk5Hash},
                mockTorrentFile(fileSize1, fileName1), mockTorrentFile(fileSize2, fileName2),
                mockTorrentFile(fileSize3, fileName3), mockTorrentFile(fileSize4, fileName4),
                mockTorrentFile(fileSize5, fileName5), mockTorrentFile(fileSize6, fileName6));

        DataDescriptor descriptor = dataDescriptorFactory.createDescriptor(torrent, storage);
        assertEquals(6, descriptor.getChunkDescriptors().size());

        return descriptor;
    }

    @Test
    public void testDescriptors_WriteMultiFile() {

        String torrentName = "xyz-torrent";
        File torrentDirectory = new File(storage.getRoot(), torrentName);
        String extension = "-multi.bin";

        String fileName1 = 1 + extension,
               fileName2 = 2 + extension,
               fileName3 = 3 + extension,
               fileName4 = 4 + extension,
               fileName5 = 5 + extension,
               fileName6 = 6 + extension;

        DataDescriptor descriptor = createDataDescriptor_MultiFile(fileName1, fileName2, fileName3, fileName4,
                fileName5, fileName6, torrentDirectory);
        List<ChunkDescriptor> chunks = descriptor.getChunkDescriptors();

        chunks.get(0).getData().putBytes(TestUtil.sequence(8));
        chunks.get(0).getData().getSubrange(8).putBytes(TestUtil.sequence(8));
        assertTrue(chunks.get(0).isComplete());
        assertTrue(verifier.verify(chunks.get(0)));

        chunks.get(1).getData().putBytes(TestUtil.sequence(4));
        chunks.get(1).getData().getSubrange(4).putBytes(TestUtil.sequence(4));
        chunks.get(1).getData().getSubrange(8).putBytes(TestUtil.sequence(4));
        chunks.get(1).getData().getSubrange(12).putBytes(TestUtil.sequence(4));
        assertTrue(chunks.get(1).isComplete());
        assertTrue(verifier.verify(chunks.get(1)));

        // reverse order
        chunks.get(2).getData().getSubrange(5).putBytes(TestUtil.sequence(11));
        chunks.get(2).getData().getSubrange(2).putBytes(TestUtil.sequence(3));
        chunks.get(2).getData().putBytes(TestUtil.sequence(2));
        assertFalse(chunks.get(2).isComplete());
        chunks.get(2).getData().putBytes(new byte[]{1,2,1,2,3,1,2,3});
        assertTrue(chunks.get(2).isComplete());
        assertTrue(verifier.verify(chunks.get(2)));

        // "random" order
        chunks.get(3).getData().getSubrange(4).putBytes(TestUtil.sequence(4));
        chunks.get(3).getData().putBytes(TestUtil.sequence(4));
        chunks.get(3).getData().getSubrange(12).putBytes(TestUtil.sequence(4));
        chunks.get(3).getData().getSubrange(8).putBytes(TestUtil.sequence(4));
        assertTrue(chunks.get(3).isComplete());
        assertTrue(verifier.verify(chunks.get(3)));

        // block size same as chunk size
        chunks.get(4).getData().putBytes(TestUtil.sequence(16));
        assertTrue(chunks.get(4).isComplete());
        assertTrue(verifier.verify(chunks.get(4)));

        // 1-byte blocks
        chunks.get(5).getData().putBytes(TestUtil.sequence(1));
        chunks.get(5).getData().getSubrange(15).putBytes(TestUtil.sequence(1));
        chunks.get(5).getData().getSubrange(1).putBytes(TestUtil.sequence(14));
        assertFalse(chunks.get(5).isComplete());
        chunks.get(5).getData().putBytes(new byte[]{1,1,2,3,4,5,6,7,8,9,1,2,3,4,5,1});
        assertTrue(chunks.get(5).isComplete());
        assertTrue(verifier.verify(chunks.get(5)));

        assertFileHasContents(new File(torrentDirectory, fileName1), MULTI_FILE_1);
        assertFileHasContents(new File(torrentDirectory, fileName2), MULTI_FILE_2);
        assertFileHasContents(new File(torrentDirectory, fileName3), MULTI_FILE_3);
        assertFileHasContents(new File(torrentDirectory, fileName4), MULTI_FILE_4);
        assertFileHasContents(new File(torrentDirectory, fileName5), MULTI_FILE_5);
        assertFileHasContents(new File(torrentDirectory, fileName6), MULTI_FILE_6);
    }

    /**************************************************************************************/

    private DataDescriptor createDataDescriptor_MultiEmptyFile(String fileName1, String fileName2, String fileName3,
                                                               String fileName4, String fileName5, String fileName6,
                                                               File parentDirectory) {

        String torrentName = parentDirectory.getName();

        long chunkSize = 16;
        long torrentSize = 0;
        long fileSize1 = 0,
             fileSize2 = 0,
             fileSize3 = 0,
             fileSize4 = 0,
             fileSize5 = 0,
             fileSize6 = 0;

        // sanity check
        assertEquals(torrentSize, fileSize1 + fileSize2 + fileSize3 + fileSize4 + fileSize5 + fileSize6);

        byte[][] chunkHashes = new byte[0][];

        Torrent torrent = mockTorrent(torrentName, torrentSize, chunkSize, chunkHashes,
                mockTorrentFile(fileSize1, fileName1), mockTorrentFile(fileSize2, fileName2),
                mockTorrentFile(fileSize3, fileName3), mockTorrentFile(fileSize4, fileName4),
                mockTorrentFile(fileSize5, fileName5), mockTorrentFile(fileSize6, fileName6));

        DataDescriptor descriptor = dataDescriptorFactory.createDescriptor(torrent, storage);
        assertEquals(0, descriptor.getChunkDescriptors().size());

        return descriptor;
    }

    @Test
    public void testDescriptors_WriteMultiEmptyFile() {

        String torrentName = "xyz-torrent";
        File torrentDirectory = new File(storage.getRoot(), torrentName);
        String extension = "-multi.bin";

        String fileName1 = 1 + extension,
               fileName2 = 2 + extension,
               fileName3 = 3 + extension,
               fileName4 = 4 + extension,
               fileName5 = 5 + extension,
               fileName6 = 6 + extension;

        DataDescriptor descriptor = createDataDescriptor_MultiEmptyFile(fileName1, fileName2, fileName3, fileName4,
                fileName5, fileName6, torrentDirectory);
        assertTrue(descriptor.getChunkDescriptors().isEmpty());
        assertEquals(0, descriptor.getBitfield().getPiecesRemaining());
        assertEquals(0, descriptor.getBitfield().getPiecesTotal());
        assertEquals(0, descriptor.getBitfield().getPiecesComplete());
        assertEquals(0, descriptor.getBitfield().getBitmask().size());
        assertEquals(0, descriptor.getBitfield().toByteArray(BitOrder.LITTLE_ENDIAN).length);

        assertFileHasContents(new File(torrentDirectory, fileName1), new byte[0]);
        assertFileHasContents(new File(torrentDirectory, fileName2), new byte[0]);
        assertFileHasContents(new File(torrentDirectory, fileName3), new byte[0]);
        assertFileHasContents(new File(torrentDirectory, fileName4), new byte[0]);
        assertFileHasContents(new File(torrentDirectory, fileName5), new byte[0]);
        assertFileHasContents(new File(torrentDirectory, fileName6), new byte[0]);
    }

    /**************************************************************************************/

    @Test
    public void testDescriptors_ReadMultiFile() {

        String torrentName = "xyz-torrent-read";
        File torrentDirectory = new File(storage.getRoot(), torrentName);
        String extension = "-multi.bin";

        String fileName1 = 1 + extension,
               fileName2 = 2 + extension,
               fileName3 = 3 + extension,
               fileName4 = 4 + extension,
               fileName5 = 5 + extension,
               fileName6 = 6 + extension;

        writeBytesToFile(new File(torrentDirectory, fileName1), MULTI_FILE_1);
        writeBytesToFile(new File(torrentDirectory, fileName2), MULTI_FILE_2);
        writeBytesToFile(new File(torrentDirectory, fileName3), MULTI_FILE_3);
        writeBytesToFile(new File(torrentDirectory, fileName4), MULTI_FILE_4);
        writeBytesToFile(new File(torrentDirectory, fileName5), MULTI_FILE_5);
        writeBytesToFile(new File(torrentDirectory, fileName6), MULTI_FILE_6);

        DataDescriptor descriptor = createDataDescriptor_MultiFile(fileName1, fileName2, fileName3, fileName4,
                fileName5, fileName6, torrentDirectory);
        List<ChunkDescriptor> chunks = descriptor.getChunkDescriptors();

        byte[] block;

        // beginning
        block = chunks.get(0).getData().getSubrange(0, 8).getBytes();
        assertArrayEquals(Arrays.copyOfRange(MULTI_FILE_1, 0, 8), block);

        // end
        block = chunks.get(0).getData().getSubrange(8, 8).getBytes();
        assertArrayEquals(Arrays.copyOfRange(MULTI_FILE_1, 8, 16), block);

        // whole chunk
        block = chunks.get(0).getData().getSubrange(0, 16).getBytes();
        assertArrayEquals(Arrays.copyOfRange(MULTI_FILE_1, 0, 16), block);

        // piece
        block = chunks.get(0).getData().getSubrange(1, 14).getBytes();
        assertArrayEquals(Arrays.copyOfRange(MULTI_FILE_1, 1, 15), block);

        // end of a file
        block = chunks.get(1).getData().getSubrange(0, 9).getBytes();
        assertArrayEquals(Arrays.copyOfRange(MULTI_FILE_1, 16, 25), block);

        // beginning of a file
        block = chunks.get(1).getData().getSubrange(9, 7).getBytes();
        assertArrayEquals(Arrays.copyOfRange(MULTI_FILE_2, 0, 7), block);

        // whole chunk that consists of 2 files
        block = chunks.get(1).getData().getSubrange(0, 16).getBytes();
        byte[] chunk1 = new byte[16];
        System.arraycopy(MULTI_FILE_1, 16, chunk1, 0, 9);
        System.arraycopy(MULTI_FILE_2, 0, chunk1, 9, 7);
        assertArrayEquals(chunk1, block);

        // piece of a chunk that consists of 2 files
        block = chunks.get(1).getData().getSubrange(8, 2).getBytes();
        byte[] chunk1piece = new byte[2];
        System.arraycopy(MULTI_FILE_1, 24, chunk1piece, 0, 1);
        System.arraycopy(MULTI_FILE_2, 0, chunk1piece, 1, 1);
        assertArrayEquals(chunk1piece, block);

        // 1-byte block
        block = chunks.get(5).getData().getSubrange(15, 1).getBytes();
        assertArrayEquals(Arrays.copyOfRange(MULTI_FILE_6, 0, 1), block);
    }
}
