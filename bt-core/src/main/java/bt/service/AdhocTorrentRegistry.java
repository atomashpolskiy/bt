package bt.service;

import bt.data.DataAccessFactory;
import bt.data.IDataDescriptorFactory;
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
    private IDataDescriptorFactory dataDescriptorFactory;

    private ConcurrentMap<String, Torrent> torrents;
    private ConcurrentMap<Torrent, ITorrentDescriptor> descriptors;

    @Inject
    public AdhocTorrentRegistry(ITrackerService trackerService, IDataDescriptorFactory dataDescriptorFactory,
                                IShutdownService shutdownService) {

        this.trackerService = trackerService;
        this.dataDescriptorFactory = dataDescriptorFactory;

        shutdownService.addShutdownHook(() -> descriptors.values().forEach(it -> it.getDataDescriptor().close()));

        torrents = new ConcurrentHashMap<>();
        descriptors = new ConcurrentHashMap<>();
    }

    @Override
    public Torrent getTorrent(byte[] infoHash) {
        return torrents.get(new String(infoHash));
    }

    @Override
    public Optional<ITorrentDescriptor> getDescriptor(Torrent torrent) {
        return Optional.ofNullable(descriptors.get(torrent));
    }

    @Override
    public ITorrentDescriptor getOrCreateDescriptor(Torrent torrent, DataAccessFactory dataAccessFactory) {

        return getDescriptor(torrent).orElseGet(() -> {

            ITorrentDescriptor descriptor = new TorrentDescriptor(trackerService, torrent,
                    dataDescriptorFactory.createDescriptor(torrent, dataAccessFactory));
            ITorrentDescriptor existing = descriptors.putIfAbsent(torrent, descriptor);
            if (existing != null) {
                descriptor = existing;
            }
            torrents.putIfAbsent(new String(torrent.getInfoHash()), torrent);
            return descriptor;
        });
    }
}
