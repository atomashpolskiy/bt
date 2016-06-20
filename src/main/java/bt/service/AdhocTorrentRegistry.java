package bt.service;

import bt.data.DataAccessFactory;
import bt.data.DataDescriptor;
import bt.metainfo.Torrent;
import bt.torrent.ITorrentDescriptor;
import bt.torrent.TorrentDescriptor;
import bt.tracker.ITrackerService;
import com.google.inject.Inject;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class AdhocTorrentRegistry implements ITorrentRegistry {

    private ITrackerService trackerService;
    private IConfigurationService configurationService;

    private ConcurrentMap<byte[], Torrent> torrents;
    private ConcurrentMap<Torrent, ITorrentDescriptor> descriptors;

    @Inject
    public AdhocTorrentRegistry(ITrackerService trackerService, IConfigurationService configurationService,
                                IShutdownService shutdownService) {

        this.trackerService = trackerService;
        this.configurationService = configurationService;

        shutdownService.addShutdownHook(() -> descriptors.values().forEach(it -> it.getDataDescriptor().close()));

        torrents = new ConcurrentHashMap<>();
        descriptors = new ConcurrentHashMap<>();
    }

    @Override
    public Torrent getTorrent(byte[] infoHash) {
        return torrents.get(infoHash);
    }

    @Override
    public Optional<ITorrentDescriptor> getDescriptor(Torrent torrent) {
        return Optional.ofNullable(descriptors.get(torrent));
    }

    @Override
    public ITorrentDescriptor getOrCreateDescriptor(Torrent torrent, DataAccessFactory dataAccessFactory) {

        return getDescriptor(torrent).orElseGet(() -> {

            ITorrentDescriptor descriptor = new TorrentDescriptor(trackerService, torrent,
                    new DataDescriptor(dataAccessFactory, configurationService, torrent));
            ITorrentDescriptor existing = descriptors.putIfAbsent(torrent, descriptor);
            if (existing != null) {
                descriptor = existing;
            }
            return descriptor;
        });
    }
}
