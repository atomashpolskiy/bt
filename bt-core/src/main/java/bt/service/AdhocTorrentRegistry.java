package bt.service;

import bt.data.Storage;
import bt.data.IDataDescriptorFactory;
import bt.metainfo.Torrent;
import bt.metainfo.TorrentId;
import bt.torrent.ITorrentDescriptor;
import bt.torrent.TorrentDescriptor;
import bt.tracker.ITrackerService;
import com.google.inject.Inject;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class AdhocTorrentRegistry implements ITorrentRegistry {

    private ITrackerService trackerService;
    private IDataDescriptorFactory dataDescriptorFactory;
    private IRuntimeLifecycleBinder lifecycleBinder;

    private ConcurrentMap<TorrentId, Torrent> torrents;
    private ConcurrentMap<Torrent, ITorrentDescriptor> descriptors;

    @Inject
    public AdhocTorrentRegistry(ITrackerService trackerService, IDataDescriptorFactory dataDescriptorFactory,
                                IRuntimeLifecycleBinder lifecycleBinder) {

        this.trackerService = trackerService;
        this.dataDescriptorFactory = dataDescriptorFactory;
        this.lifecycleBinder = lifecycleBinder;

        torrents = new ConcurrentHashMap<>();
        descriptors = new ConcurrentHashMap<>();
    }

    @Override
    public Optional<Torrent> getTorrent(TorrentId torrentId) {
        return Optional.ofNullable(torrents.get(torrentId));
    }

    @Override
    public Optional<ITorrentDescriptor> getDescriptor(Torrent torrent) {
        return Optional.ofNullable(descriptors.get(torrent));
    }

    @Override
    public ITorrentDescriptor getOrCreateDescriptor(Torrent torrent, Storage storage) {

        return getDescriptor(torrent).orElseGet(() -> {

            ITorrentDescriptor descriptor = new TorrentDescriptor(trackerService, torrent,
                    dataDescriptorFactory.createDescriptor(torrent, storage));
            ITorrentDescriptor existing = descriptors.putIfAbsent(torrent, descriptor);
            if (existing != null) {
                descriptor = existing;
            } else {
                final ITorrentDescriptor tDescriptor = descriptor;
                lifecycleBinder.onShutdown(tDescriptor.getDataDescriptor().toString(), () -> {
                    try {
                        tDescriptor.getDataDescriptor().close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
            }
            torrents.putIfAbsent(torrent.getTorrentId(), torrent);
            return descriptor;
        });
    }
}
