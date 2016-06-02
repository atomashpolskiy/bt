package bt.service;

import bt.BtException;
import bt.data.ChunkDescriptor;
import bt.data.DataAccess;
import bt.data.DataAccessFactory;
import bt.data.DataDescriptor;
import bt.data.IChunkDescriptor;
import bt.data.IDataDescriptor;
import bt.metainfo.IMetadataService;
import bt.metainfo.Torrent;
import bt.metainfo.TorrentFile;
import bt.torrent.ITorrentDescriptor;
import bt.torrent.TorrentDescriptor;
import bt.tracker.ITrackerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class AdhocTorrentRegistry implements ITorrentRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdhocTorrentRegistry.class);

    private IMetadataService metadataService;
    private ITrackerService trackerService;
    private IConfigurationService configurationService;
    private DataAccessFactory dataAccessFactory;

    private ConcurrentMap<byte[], Torrent> torrents;
    private ConcurrentMap<Torrent, ITorrentDescriptor> descriptors;

    public AdhocTorrentRegistry(IMetadataService metadataService, ITrackerService trackerService,
                                IConfigurationService configurationService, DataAccessFactory dataAccessFactory) {

        this.metadataService = metadataService;
        this.trackerService = trackerService;
        this.configurationService = configurationService;
        this.dataAccessFactory = dataAccessFactory;

        torrents = new ConcurrentHashMap<>();
        descriptors = new ConcurrentHashMap<>();
    }

    @Override
    public Torrent getTorrent(byte[] infoHash) {
        return torrents.get(infoHash);
    }

    public Torrent createTorrentFromMetainfo(URL metainfoUrl) {

        Torrent torrent = metadataService.fromUrl(metainfoUrl);

        Torrent existing = torrents.putIfAbsent(torrent.getInfoHash(), torrent);
        if (existing != null) {
            LOGGER.warn("Received request to add an existing torrent: " + torrent.getName());
            torrent = existing;
        }
        return torrent;
    }

    @Override
    public ITorrentDescriptor getDescriptor(Torrent torrent) {

        ITorrentDescriptor descriptor = descriptors.get(torrent);
        if (descriptor == null) {
            descriptor = new TorrentDescriptor(trackerService, configurationService, torrent, buildDataDescriptor(torrent));
            ITorrentDescriptor existing = descriptors.putIfAbsent(torrent, descriptor);
            if (existing != null) {
                descriptor = existing;
            }
        }
        return descriptor;
    }

    private IDataDescriptor buildDataDescriptor(Torrent torrent) {

        List<TorrentFile> torrentFiles = torrent.getFiles();

        int filesCount = torrentFiles.size();
        long totalSize = torrent.getSize();
        long chunkSize = torrent.getChunkSize();

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
                            chunkOffset, limitInCurrentFile, chunkHashes.next(), configurationService.getTransferBlockSize()
                    ));

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
        }

        if (chunkHashes.hasNext()) {
            throw new BtException("Wrong number of chunk hashes in the torrent: too many");
        }

        return new DataDescriptor(chunkDescriptors);
    }
}
