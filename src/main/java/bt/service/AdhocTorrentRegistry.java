package bt.service;

import bt.data.DataAccessFactory;
import bt.data.DataDescriptor;
import bt.metainfo.IMetadataService;
import bt.metainfo.Torrent;
import bt.torrent.ITorrentDescriptor;
import bt.torrent.TorrentDescriptor;
import bt.tracker.ITrackerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
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
            descriptor = new TorrentDescriptor(trackerService, torrent,
                    new DataDescriptor(dataAccessFactory, configurationService, torrent));
            ITorrentDescriptor existing = descriptors.putIfAbsent(torrent, descriptor);
            if (existing != null) {
                descriptor = existing;
            }
        }
        return descriptor;
    }
}
