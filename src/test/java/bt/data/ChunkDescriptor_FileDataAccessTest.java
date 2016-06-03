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

import static bt.data.ChunkDescriptorTestUtil.mockTorrent;
import static bt.data.ChunkDescriptorTestUtil.mockTorrentFile;
import static bt.data.ChunkDescriptorTestUtil.readBytesFromFile;
import static bt.data.ChunkDescriptorTestUtil.sequence;
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

    @Test
    public void testDescriptors_WriteSingleFile() {

        String fileName = "1-single.bin";

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
        List<IChunkDescriptor> chunks = descriptor.getChunkDescriptors();

        assertEquals(4, chunks.size());

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

        byte[] file = readBytesFromFile(new File(rootDirectory, fileName), (int) fileSize);
        assertArrayEquals(SINGLE_FILE, file);
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
        List<IChunkDescriptor> chunks = descriptor.getChunkDescriptors();

        assertEquals(6, chunks.size());

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

        byte[] file1 = readBytesFromFile(new File(torrentDirectory, fileName1), (int) fileSize1);
        assertArrayEquals(MULTI_FILE_1, file1);

        byte[] file2 = readBytesFromFile(new File(torrentDirectory, fileName2), (int) fileSize2);
        assertArrayEquals(MULTI_FILE_2, file2);

        byte[] file3 = readBytesFromFile(new File(torrentDirectory, fileName3), (int) fileSize3);
        assertArrayEquals(MULTI_FILE_3, file3);

        byte[] file4 = readBytesFromFile(new File(torrentDirectory, fileName4), (int) fileSize4);
        assertArrayEquals(MULTI_FILE_4, file4);

        byte[] file5 = readBytesFromFile(new File(torrentDirectory, fileName5), (int) fileSize5);
        assertArrayEquals(MULTI_FILE_5, file5);

        byte[] file6 = readBytesFromFile(new File(torrentDirectory, fileName6), (int) fileSize6);
        assertArrayEquals(MULTI_FILE_6, file6);
    }
}
