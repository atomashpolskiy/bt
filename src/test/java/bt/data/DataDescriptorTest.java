package bt.data;

import bt.data.file.FileSystemDataAccessFactory;
import bt.metainfo.Torrent;
import bt.metainfo.TorrentFile;
import bt.service.CryptoUtil;
import bt.service.IConfigurationService;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DataDescriptorTest {

    private static File rootDirectory;

    private static DataAccessFactory dataAccessFactory;
    private static IConfigurationService configurationService;

    @BeforeClass
    public static void setUp() {
        rootDirectory = new File("target/rt");

        dataAccessFactory = new FileSystemDataAccessFactory(rootDirectory);

        configurationService = mock(IConfigurationService.class);
        when(configurationService.getTransferBlockSize()).thenReturn((long) (2 << 13));
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
                new byte[][] {new byte[20],new byte[20],new byte[20],new byte[20]},
                mockTorrentFile(fileSize, fileName));

        IDataDescriptor descriptor = new DataDescriptor(dataAccessFactory, configurationService, torrent);
        List<IChunkDescriptor> chunks = descriptor.getChunkDescriptors();

        assertEquals(4, chunks.size());

        chunks.get(0).writeBlock(sequence(8), 0);
        chunks.get(0).writeBlock(sequence(8), 8);

        chunks.get(1).writeBlock(sequence(4), 0);
        chunks.get(1).writeBlock(sequence(4), 4);
        chunks.get(1).writeBlock(sequence(4), 8);
        chunks.get(1).writeBlock(sequence(4), 12);

        // reverse order
        chunks.get(2).writeBlock(sequence(11), 5);
        chunks.get(2).writeBlock(sequence(3), 2);
        chunks.get(2).writeBlock(sequence(2), 0);

        // "random" order
        chunks.get(3).writeBlock(sequence(4), 4);
        chunks.get(3).writeBlock(sequence(4), 0);
        chunks.get(3).writeBlock(sequence(4), 12);
        chunks.get(3).writeBlock(sequence(4), 8);

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

        Torrent torrent = mockTorrent(torrentName, torrentSize, chunkSize,
                new byte[][] {new byte[20],new byte[20],new byte[20],new byte[20],new byte[20],new byte[20]},
                mockTorrentFile(fileSize1, fileName1), mockTorrentFile(fileSize2, fileName2),
                mockTorrentFile(fileSize3, fileName3), mockTorrentFile(fileSize4, fileName4),
                mockTorrentFile(fileSize5, fileName5), mockTorrentFile(fileSize6, fileName6));

        IDataDescriptor descriptor = new DataDescriptor(dataAccessFactory, configurationService, torrent);
        List<IChunkDescriptor> chunks = descriptor.getChunkDescriptors();

        assertEquals(6, chunks.size());

        chunks.get(0).writeBlock(sequence(8), 0);
        chunks.get(0).writeBlock(sequence(8), 8);

        chunks.get(1).writeBlock(sequence(4), 0);
        chunks.get(1).writeBlock(sequence(4), 4);
        chunks.get(1).writeBlock(sequence(4), 8);
        chunks.get(1).writeBlock(sequence(4), 12);

        // reverse order
        chunks.get(2).writeBlock(sequence(11), 5);
        chunks.get(2).writeBlock(sequence(3), 2);
        chunks.get(2).writeBlock(sequence(2), 0);

        // "random" order
        chunks.get(3).writeBlock(sequence(4), 4);
        chunks.get(3).writeBlock(sequence(4), 0);
        chunks.get(3).writeBlock(sequence(4), 12);
        chunks.get(3).writeBlock(sequence(4), 8);

        // block size same as chunk size
        chunks.get(4).writeBlock(sequence(16), 0);

        // 1-byte blocks
        chunks.get(5).writeBlock(sequence(1), 0);
        chunks.get(5).writeBlock(sequence(1), 15);
        chunks.get(5).writeBlock(sequence(14), 1);

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


    private Torrent mockTorrent(String name, long size, long chunkSize, byte[][] chunkHashes, TorrentFile... files) {

        Torrent torrent = mock(Torrent.class);

        when(torrent.getName()).thenReturn(name);
        when(torrent.getChunkHashes()).thenReturn(Arrays.asList(chunkHashes));
        when(torrent.getChunkSize()).thenReturn(chunkSize);
        when(torrent.getSize()).thenReturn(size);
        when(torrent.getFiles()).thenReturn(Arrays.asList(files));

        try {
            when(torrent.getTrackerUrl()).thenReturn(URI.create("http://tracker.org/ann").toURL());
        } catch (MalformedURLException e) {
            // not going to happen
        }

        return torrent;
    }

    private TorrentFile mockTorrentFile(long size, String... pathElements) {

        TorrentFile file = mock(TorrentFile.class);

        when(file.getSize()).thenReturn(size);
        when(file.getPathElements()).thenReturn(Arrays.asList(pathElements));

        return file;
    }

    private byte[] hash(int dataSize) {
        return CryptoUtil.getSha1Digest(sequence(dataSize));
    }

    /**
     * Creates a periodical list of numbers from 1 to 9, starting with {@code start}
     * and looping if {@code size} is greater than 9.
     * @param size Length of the resulting array
     * @param start Number between 1 and 9 inclusive; sequence starts with this number
     * @return Array of numbers
     */
    private byte[] sequence(int size, int start) {

        if (start < 1 || start > 9) {
            throw new RuntimeException("Illegal starting number (must be 1-9): " + start);
        }

        byte[] sequence = new byte[size];

        byte b = (byte) (start - 1);
        for (int i = 0; i < size; i++) {
            if (++b == 10) {
                b = 1;
            }
            sequence[i] = b;
        }

        return sequence;
    }

    private byte[] sequence(int size) {
        return sequence(size, 1);
    }

    private byte[] readBytesFromFile(File file, int size) {

        byte[] bytes = new byte[size];
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(file))) {
            int read = in.read(bytes);
            if (read != size) {
                throw new RuntimeException("Wrong number of bytes read: " + read + ", expected: " + size);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file: " + file.getPath());
        }
        return bytes;
    }
}
