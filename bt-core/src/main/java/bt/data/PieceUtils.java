/*
 * Copyright (c) 2016—2021 Andrei Tomashpolskiy and individual contributors.
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

import bt.BtException;
import bt.data.range.BlockRange;
import bt.data.range.Ranges;
import bt.data.range.SynchronizedBlockSet;
import bt.data.range.SynchronizedDataRange;
import bt.data.range.SynchronizedRange;
import bt.metainfo.Torrent;
import bt.metainfo.TorrentFile;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A utility class for functionality related to pieces
 */
public class PieceUtils {
    /**
     * Calculate the number of chunks there are in a torrent based on the totalSize and chunkSize
     *
     * @param totalSize the totalSize of the torrent
     * @param chunkSize the chunk size of the torrent
     * @return the number of chunks in the torrent
     */
    public static int calculateNumberOfChunks(long totalSize, long chunkSize) {
        return (int) ((totalSize + chunkSize - 1) / chunkSize);
    }

    static List<ChunkDescriptor> buildChunkDescriptors(Torrent torrent, long transferBlockSize, long totalSize,
                                                       long chunkSize,
                                                       int chunksTotal, List<List<TorrentFile>> pieceNumToFile,
                                                       Map<StorageUnit, TorrentFile> storageUnitsToFilesMap,
                                                       List<StorageUnit> nonEmptyStorageUnits) {
        Iterator<byte[]> chunkHashes = torrent.getChunkHashes().iterator();
        List<ChunkDescriptor> chunks = new ArrayList<>(chunksTotal);
        if (nonEmptyStorageUnits.size() > 0) {
            DataRange data = buildReadWriteDataRange(nonEmptyStorageUnits);

            for (long remaining = totalSize; remaining > 0; remaining -= chunkSize) {
                long off = chunks.size() * chunkSize;
                long lim = Math.min(chunkSize, remaining);

                DataRange subrange = data.getSubrange(off, lim);

                if (!chunkHashes.hasNext()) {
                    throw new BtException("Wrong number of chunk hashes in the torrent: too few");
                }

                ArrayList<TorrentFile> chunkFiles = new ArrayList<>();
                subrange.visitUnits((unit, off1, lim1) -> chunkFiles.add(storageUnitsToFilesMap.get(unit)));
                chunkFiles.trimToSize();
                pieceNumToFile.add(chunkFiles);

                chunks.add(buildChunkDescriptor(subrange, transferBlockSize, chunkHashes.next()));
            }
        }
        if (chunkHashes.hasNext()) {
            throw new BtException("Wrong number of chunk hashes in the torrent: too many");
        }
        return chunks;
    }

    /**
     * Builds a read write data range with the ordered passed in storage units
     *
     * @param nonEmptyStorageUnits the non empty storage units to create this datarange with
     * @return the built datarange
     */
    public static DataRange buildReadWriteDataRange(List<StorageUnit> nonEmptyStorageUnits) {
        long limitInLastUnit = nonEmptyStorageUnits.get(nonEmptyStorageUnits.size() - 1).capacity();
        return new ReadWriteDataRange(nonEmptyStorageUnits, 0, limitInLastUnit);
    }

    private static ChunkDescriptor buildChunkDescriptor(DataRange data, long transferBlockSize, byte[] checksum) {
        BlockRange<DataRange> blockData = Ranges.blockRange(data, transferBlockSize);
        SynchronizedRange<BlockRange<DataRange>> synchronizedRange = new SynchronizedRange<>(blockData);
        SynchronizedDataRange<BlockRange<DataRange>> synchronizedData =
                new SynchronizedDataRange<>(synchronizedRange, BlockRange::getDelegate);
        SynchronizedBlockSet synchronizedBlockSet =
                new SynchronizedBlockSet(blockData.getBlockSet(), synchronizedRange);

        return new DefaultChunkDescriptor(synchronizedData, synchronizedBlockSet, checksum);
    }
}
