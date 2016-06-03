package bt.data;

import bt.data.file.FileSystemDataAccessFactory;
import bt.metainfo.Torrent;
import bt.service.CryptoUtil;
import bt.service.IConfigurationService;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import static bt.data.ChunkDescriptorTestUtil.assertFileHasContents;
import static bt.data.ChunkDescriptorTestUtil.mockTorrent;
import static bt.data.ChunkDescriptorTestUtil.mockTorrentFile;
import static bt.data.ChunkDescriptorTestUtil.sequence;
import static bt.data.ChunkDescriptorTestUtil.writeBytesToFile;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ChunkDescriptor_FileDataAccessTest {

    private static File rootDirectory;

    private static DataAccessFactory dataAccessFactory;
    private static IConfigurationService configurationService;

    @BeforeClass
    public static void setUp() {
        rootDirectory = new File("target/rt");

        dataAccessFactory = new FileSystemDataAccessFactory(rootDirectory);

        configurationService = mock(IConfigurationService.class);
        when(configurationService.getTransferBlockSize()).thenReturn(4L);
    }

    private byte[] SINGLE_FILE = new byte[] {
            1,2,3,4,5,6,7,8,1,2,3,4,5,6,7,8,
            1,2,3,4,1,2,3,4,1,2,3,4,1,2,3,4,
            1,2,1,2,3,1,2,3,4,5,6,7,8,9,1,2,
            1,2,3,4,1,2,3,4,1,2,3,4,1,2,3,4
    };

    private IDataDescriptor createDataDescriptor_SingleFile(String fileName) {

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

        IDataDescriptor descriptor = new DataDescriptor(dataAccessFactory, configurationService, torrent);
        assertEquals(4, descriptor.getChunkDescriptors().size());

        return descriptor;
    }

    @Test
    public void testDescriptors_WriteSingleFile() {

        String fileName = "1-single.bin";
        IDataDescriptor descriptor = createDataDescriptor_SingleFile(fileName);
        List<IChunkDescriptor> chunks = descriptor.getChunkDescriptors();

        chunks.get(0).writeBlock(sequence(8), 0);
        chunks.get(0).writeBlock(sequence(8), 8);
        assertTrue(chunks.get(0).verify());

        chunks.get(1).writeBlock(sequence(4), 0);
        chunks.get(1).writeBlock(sequence(4), 4);
        chunks.get(1).writeBlock(sequence(4), 8);
        chunks.get(1).writeBlock(sequence(4), 12);
        assertTrue(chunks.get(1).verify());

        // reverse order
        chunks.get(2).writeBlock(sequence(11), 5);
        chunks.get(2).writeBlock(sequence(3), 2);
        chunks.get(2).writeBlock(sequence(2), 0);
        assertFalse(chunks.get(2).verify());

        // "random" order
        chunks.get(3).writeBlock(sequence(4), 4);
        chunks.get(3).writeBlock(sequence(4), 0);
        chunks.get(3).writeBlock(sequence(4), 12);
        chunks.get(3).writeBlock(sequence(4), 8);
        assertTrue(chunks.get(3).verify());

        assertFileHasContents(new File(rootDirectory, fileName), SINGLE_FILE);
    }

    @Test
    public void testDescriptors_ReadSingleFile() {

        String fileName = "1-single-read.bin";
        writeBytesToFile(new File(rootDirectory, fileName), SINGLE_FILE);

        IDataDescriptor descriptor = createDataDescriptor_SingleFile(fileName);
        List<IChunkDescriptor> chunks = descriptor.getChunkDescriptors();

        byte[] block;

        // beginning
        block = chunks.get(0).readBlock(0, 8);
        assertArrayEquals(Arrays.copyOfRange(SINGLE_FILE, 0, 8), block);

        // end
        block = chunks.get(0).readBlock(8, 8);
        assertArrayEquals(Arrays.copyOfRange(SINGLE_FILE, 8, 16), block);

        // whole chunk
        block = chunks.get(0).readBlock(0, 16);
        assertArrayEquals(Arrays.copyOfRange(SINGLE_FILE, 0, 16), block);

        // piece
        block = chunks.get(0).readBlock(1, 14);
        assertArrayEquals(Arrays.copyOfRange(SINGLE_FILE, 1, 15), block);
    }

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

    private IDataDescriptor createDataDescriptor_MultiFile(String fileName1, String fileName2, String fileName3,
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

        IDataDescriptor descriptor = new DataDescriptor(dataAccessFactory, configurationService, torrent);
        assertEquals(6, descriptor.getChunkDescriptors().size());

        return descriptor;
    }

    @Test
    public void testDescriptors_WriteMultiFile() {

        String torrentName = "xyz-torrent";
        File torrentDirectory = new File(rootDirectory, torrentName);
        String extension = "-multi.bin";

        String fileName1 = 1 + extension,
               fileName2 = 2 + extension,
               fileName3 = 3 + extension,
               fileName4 = 4 + extension,
               fileName5 = 5 + extension,
               fileName6 = 6 + extension;

        IDataDescriptor descriptor = createDataDescriptor_MultiFile(fileName1, fileName2, fileName3, fileName4,
                fileName5, fileName6, torrentDirectory);
        List<IChunkDescriptor> chunks = descriptor.getChunkDescriptors();

        chunks.get(0).writeBlock(sequence(8), 0);
        chunks.get(0).writeBlock(sequence(8), 8);
        assertTrue(chunks.get(0).verify());

        chunks.get(1).writeBlock(sequence(4), 0);
        chunks.get(1).writeBlock(sequence(4), 4);
        chunks.get(1).writeBlock(sequence(4), 8);
        chunks.get(1).writeBlock(sequence(4), 12);
        assertTrue(chunks.get(1).verify());

        // reverse order
        chunks.get(2).writeBlock(sequence(11), 5);
        chunks.get(2).writeBlock(sequence(3), 2);
        chunks.get(2).writeBlock(sequence(2), 0);
        assertFalse(chunks.get(2).verify());
        chunks.get(2).writeBlock(new byte[]{1,2,1,2,3,1,2,3}, 0);
        assertTrue(chunks.get(2).verify());

        // "random" order
        chunks.get(3).writeBlock(sequence(4), 4);
        chunks.get(3).writeBlock(sequence(4), 0);
        chunks.get(3).writeBlock(sequence(4), 12);
        chunks.get(3).writeBlock(sequence(4), 8);
        assertTrue(chunks.get(3).verify());

        // block size same as chunk size
        chunks.get(4).writeBlock(sequence(16), 0);
        assertTrue(chunks.get(4).verify());

        // 1-byte blocks
        chunks.get(5).writeBlock(sequence(1), 0);
        chunks.get(5).writeBlock(sequence(1), 15);
        chunks.get(5).writeBlock(sequence(14), 1);
        assertFalse(chunks.get(5).verify());
        chunks.get(5).writeBlock(new byte[]{1,1,2,3,4,5,6,7,8,9,1,2,3,4,5,1}, 0);
        assertTrue(chunks.get(5).verify());

        assertFileHasContents(new File(torrentDirectory, fileName1), MULTI_FILE_1);
        assertFileHasContents(new File(torrentDirectory, fileName2), MULTI_FILE_2);
        assertFileHasContents(new File(torrentDirectory, fileName3), MULTI_FILE_3);
        assertFileHasContents(new File(torrentDirectory, fileName4), MULTI_FILE_4);
        assertFileHasContents(new File(torrentDirectory, fileName5), MULTI_FILE_5);
        assertFileHasContents(new File(torrentDirectory, fileName6), MULTI_FILE_6);
    }

    @Test
    public void testDescriptors_ReadMultiFile() {

        String torrentName = "xyz-torrent-read";
        File torrentDirectory = new File(rootDirectory, torrentName);
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

        IDataDescriptor descriptor = createDataDescriptor_MultiFile(fileName1, fileName2, fileName3, fileName4,
                fileName5, fileName6, torrentDirectory);
        List<IChunkDescriptor> chunks = descriptor.getChunkDescriptors();

        byte[] block;

        // beginning
        block = chunks.get(0).readBlock(0, 8);
        assertArrayEquals(Arrays.copyOfRange(MULTI_FILE_1, 0, 8), block);

        // end
        block = chunks.get(0).readBlock(8, 8);
        assertArrayEquals(Arrays.copyOfRange(MULTI_FILE_1, 8, 16), block);

        // whole chunk
        block = chunks.get(0).readBlock(0, 16);
        assertArrayEquals(Arrays.copyOfRange(MULTI_FILE_1, 0, 16), block);

        // piece
        block = chunks.get(0).readBlock(1, 14);
        assertArrayEquals(Arrays.copyOfRange(MULTI_FILE_1, 1, 15), block);

        // end of a file
        block = chunks.get(1).readBlock(0, 9);
        assertArrayEquals(Arrays.copyOfRange(MULTI_FILE_1, 16, 25), block);

        // beginning of a file
        block = chunks.get(1).readBlock(9, 7);
        assertArrayEquals(Arrays.copyOfRange(MULTI_FILE_2, 0, 7), block);

        // whole chunk that consists of 2 files
        block = chunks.get(1).readBlock(0, 16);
        byte[] chunk1 = new byte[16];
        System.arraycopy(MULTI_FILE_1, 16, chunk1, 0, 9);
        System.arraycopy(MULTI_FILE_2, 0, chunk1, 9, 7);
        assertArrayEquals(chunk1, block);

        // piece of a chunk that consists of 2 files
        block = chunks.get(1).readBlock(8, 2);
        byte[] chunk1piece = new byte[2];
        System.arraycopy(MULTI_FILE_1, 24, chunk1piece, 0, 1);
        System.arraycopy(MULTI_FILE_2, 0, chunk1piece, 1, 1);
        assertArrayEquals(chunk1piece, block);

        // 1-byte block
        block = chunks.get(5).readBlock(15, 1);
        assertArrayEquals(Arrays.copyOfRange(MULTI_FILE_6, 0, 1), block);
    }
}
