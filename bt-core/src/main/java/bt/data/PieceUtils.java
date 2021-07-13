package bt.data;

import bt.BtException;
import bt.data.range.*;
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

    private static ChunkDescriptor buildChunkDescriptor(DataRange data, long blockSize, byte[] checksum) {
        BlockRange<DataRange> blockData = Ranges.blockRange(data, blockSize);
        SynchronizedRange<BlockRange<DataRange>> synchronizedRange = new SynchronizedRange<>(blockData);
        SynchronizedDataRange<BlockRange<DataRange>> synchronizedData =
                new SynchronizedDataRange<>(synchronizedRange, BlockRange::getDelegate);
        SynchronizedBlockSet synchronizedBlockSet =
                new SynchronizedBlockSet(blockData.getBlockSet(), synchronizedRange);

        return new DefaultChunkDescriptor(synchronizedData, synchronizedBlockSet, checksum);
    }
}
