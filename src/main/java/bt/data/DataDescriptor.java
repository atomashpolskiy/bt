package bt.data;

import bt.BtException;
import bt.metainfo.Torrent;
import bt.metainfo.TorrentFile;
import bt.service.IConfigurationService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class DataDescriptor implements IDataDescriptor {

    private DataAccessFactory dataAccessFactory;
    private IConfigurationService configurationService;

    private Torrent torrent;
    private List<IChunkDescriptor> chunkDescriptors;

    public DataDescriptor(DataAccessFactory dataAccessFactory, IConfigurationService configurationService,
                          Torrent torrent) {

        this.dataAccessFactory = dataAccessFactory;
        this.configurationService = configurationService;
        this.torrent = torrent;

        init();
    }

    private void init() {

        List<TorrentFile> torrentFiles = torrent.getFiles();

        int filesCount = torrentFiles.size();
        long totalSize = torrent.getSize();
        long chunkSize = torrent.getChunkSize();

        long transferBlockSize = configurationService.getTransferBlockSize();
        if (transferBlockSize > chunkSize) {
            transferBlockSize = chunkSize;
        }

        List<IChunkDescriptor> chunkDescriptors = new ArrayList<>((int) Math.ceil(totalSize / chunkSize) + 1);
        Iterator<byte[]> chunkHashes = torrent.getChunkHashes().iterator();
        DataAccess[] files = new DataAccess[filesCount];

        long chunkOffset = 0,
             totalSizeOfFiles = 0;

        int firstFileInChunkIndex = 0;

        for (int currentFileIndex = 0; currentFileIndex < filesCount; currentFileIndex++) {

            TorrentFile torrentFile = torrentFiles.get(currentFileIndex);

            long fileSize = torrentFile.getSize();
            files[currentFileIndex] = dataAccessFactory.getOrCreateDataAccess(torrent, torrentFile);
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
                            chunkOffset, limitInCurrentFile, chunkHashes.next(), transferBlockSize));

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
                } else if (currentFileIndex == filesCount - 1) {
                    // create chunk for the remainder of the last file
                    long remaining = fileSize - chunkOffset;
                    if (transferBlockSize > remaining) {
                        transferBlockSize = remaining;
                    }
                    chunkDescriptors.add(new ChunkDescriptor(
                            Arrays.copyOfRange(files, firstFileInChunkIndex, currentFileIndex + 1),
                            chunkOffset, fileSize, chunkHashes.next(), transferBlockSize));
                }
            }
        }

        if (chunkHashes.hasNext()) {
            throw new BtException("Wrong number of chunk hashes in the torrent: too many");
        }

        this.chunkDescriptors = chunkDescriptors;
    }

    public List<IChunkDescriptor> getChunkDescriptors() {
        return chunkDescriptors;
    }
}
