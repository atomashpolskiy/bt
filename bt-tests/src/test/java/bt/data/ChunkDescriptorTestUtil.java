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
