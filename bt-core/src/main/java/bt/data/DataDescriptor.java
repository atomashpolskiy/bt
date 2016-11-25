package bt.data;

import bt.BtException;
import bt.metainfo.Torrent;
import bt.metainfo.TorrentFile;
import bt.torrent.Bitfield;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class DataDescriptor implements IDataDescriptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(DataDescriptor.class);

    private Storage storage;

    private Torrent torrent;
    private List<IChunkDescriptor> chunkDescriptors;
    private Bitfield bitfield;

    private Set<StorageUnit> storageUnits;

    public DataDescriptor(Storage storage, Torrent torrent, int transferBlockSize) {
        this.storage = storage;
        this.torrent = torrent;

        storageUnits = new HashSet<>();

        init(transferBlockSize);
    }

    private void init(long transferBlockSize) {

        List<TorrentFile> torrentFiles = torrent.getFiles();

        int filesCount = torrentFiles.size();
        long totalSize = torrent.getSize();
        long chunkSize = torrent.getChunkSize();

        if (transferBlockSize > chunkSize) {
            transferBlockSize = chunkSize;
        }

        // TODO: this should be configured and managed by the storage
        boolean shouldVerifyChunks = true;

        List<IChunkDescriptor> chunkDescriptors = new ArrayList<>((int) Math.ceil(totalSize / chunkSize) + 1);
        Iterator<byte[]> chunkHashes = torrent.getChunkHashes().iterator();
        StorageUnit[] files = new StorageUnit[filesCount];

        long chunkOffset = 0,
             totalSizeOfFiles = 0;

        int firstFileInChunkIndex = 0;

        for (int currentFileIndex = 0; currentFileIndex < filesCount; currentFileIndex++) {

            TorrentFile torrentFile = torrentFiles.get(currentFileIndex);

            long fileSize = torrentFile.getSize();
            StorageUnit storageUnit = storage.getUnit(torrent, torrentFile);
            storageUnits.add(storageUnit);
            files[currentFileIndex] = storageUnit;

            totalSizeOfFiles += fileSize;

            if (totalSizeOfFiles >= chunkSize) {

                do {
                    long limitInCurrentFile = chunkSize - (totalSizeOfFiles - fileSize);

                    if (!chunkHashes.hasNext()) {
                        // TODO: this should probably be handled in DefaultTorrent builder
                        throw new BtException("Wrong number of chunk hashes in the torrent: too few");
                    }

                    chunkDescriptors.add(new ChunkDescriptor(
                            Arrays.copyOfRange(files, firstFileInChunkIndex, currentFileIndex + 1),
                            chunkOffset, limitInCurrentFile, chunkHashes.next(), transferBlockSize, shouldVerifyChunks));

                    firstFileInChunkIndex = currentFileIndex;
                    chunkOffset = limitInCurrentFile;

                    totalSizeOfFiles -= chunkSize;

                // if surplus is bigger than the chunk size,
                // then we need to catch up and create more than one chunk
                } while (totalSizeOfFiles >= chunkSize);

                if (totalSizeOfFiles == 0) {
                    // no bytes left in the current file,
                    // new chunk will begin with the next file
                    firstFileInChunkIndex++;
                    chunkOffset = 0;
                }
            }
            if (currentFileIndex == filesCount - 1 && totalSizeOfFiles > 0) {
                // create chunk for the remainder of the last file
                long remaining = fileSize - chunkOffset;
                if (transferBlockSize > remaining) {
                    transferBlockSize = remaining;
                }
                chunkDescriptors.add(new ChunkDescriptor(
                        Arrays.copyOfRange(files, firstFileInChunkIndex, currentFileIndex + 1),
                        chunkOffset, fileSize, chunkHashes.next(), transferBlockSize, shouldVerifyChunks));
            }
        }

        if (chunkHashes.hasNext()) {
            throw new BtException("Wrong number of chunk hashes in the torrent: too many");
        }

        this.chunkDescriptors = chunkDescriptors;
        this.bitfield = new Bitfield(chunkDescriptors);
    }

    @Override
    public List<IChunkDescriptor> getChunkDescriptors() {
        return chunkDescriptors;
    }

    @Override
    public Bitfield getBitfield() {
        return bitfield;
    }

    @Override
    public void close() {
        storageUnits.forEach(unit -> {
            try {
                unit.close();
            } catch (Exception e) {
                LOGGER.error("Failed to close storage unit: " + unit);
            }
        });
    }

    @Override
    public String toString() {
        return this.getClass().getName() + " <" + torrent.getName() + ">";
    }
}
