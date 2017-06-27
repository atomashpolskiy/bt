package bt.data;

import bt.data.range.BlockRange;
import bt.data.range.Ranges;
import bt.metainfo.Torrent;
import bt.metainfo.TorrentFile;
import bt.tracker.AnnounceKey;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.Assert.assertArrayEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ChunkDescriptorTestUtil {

    public static ChunkDescriptor buildChunk(List<StorageUnit> units, long blockSize) {
        long offsetInFirstUnit = 0;
        long limitInLastUnit = units.get(units.size() - 1).capacity();

        DataRange data = new ReadWriteDataRange(units, offsetInFirstUnit, limitInLastUnit);
        BlockRange<DataRange> blockData = Ranges.blockRange(data, blockSize);
        return new DefaultChunkDescriptor(Ranges.dataRange(blockData), blockData.getBlockSet(), new byte[20]);
    }

    public static List<StorageUnit> mockStorageUnits(long... capacities) {
        return Arrays.stream(capacities)
                .mapToObj(ChunkDescriptorTestUtil::mockStorageUnit)
                .collect(Collectors.<StorageUnit>toList());
    }

    public static StorageUnit mockStorageUnit(long capacity) {
        StorageUnit storageUnit = mock(StorageUnit.class);
        when(storageUnit.capacity()).thenReturn(capacity);
        return storageUnit;
    }

    public static Torrent mockTorrent(String name, long size, long chunkSize, byte[][] chunkHashes, TorrentFile... files) {
        Torrent torrent = mock(Torrent.class);

        when(torrent.getName()).thenReturn(name);
        when(torrent.getChunkHashes()).thenReturn(Arrays.asList(chunkHashes));
        when(torrent.getChunkSize()).thenReturn(chunkSize);
        when(torrent.getSize()).thenReturn(size);
        when(torrent.getFiles()).thenReturn(Arrays.asList(files));
        when(torrent.getAnnounceKey()).thenReturn(Optional.of(new AnnounceKey("http://tracker.org/ann")));

        return torrent;
    }

    public static TorrentFile mockTorrentFile(long size, String... pathElements) {
        TorrentFile file = mock(TorrentFile.class);

        when(file.getSize()).thenReturn(size);
        when(file.getPathElements()).thenReturn(Arrays.asList(pathElements));

        return file;
    }

    /**
     * Creates a periodical list of numbers from 1 to 9, starting with {@code start}
     * and looping if {@code size} is greater than 9.
     * @param size Length of the resulting array
     * @param start Number between 1 and 9 inclusive; sequence starts with this number
     * @return Array of numbers
     */
    public static byte[] sequence(int size, int start) {

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

    public static byte[] sequence(int size) {
        return sequence(size, 1);
    }

    public static void assertFileHasContents(File file, byte[] expectedBytes) {
        byte[] actualBytes = readBytesFromFile(file, expectedBytes.length);
        assertArrayEquals(expectedBytes, actualBytes);
    }

    public static byte[] readBytesFromFile(File file, int size) {

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

    public static void writeBytesToFile(File file, byte[] bytes) {

        file.getParentFile().mkdirs();

        try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(file))) {
            out.write(bytes);
            out.flush();
        } catch (IOException e) {
            throw new RuntimeException("Failed to write file: " + file.getPath());
        }
    }
}
