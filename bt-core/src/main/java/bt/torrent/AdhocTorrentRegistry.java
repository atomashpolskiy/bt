package bt.torrent;

import bt.data.Storage;
import bt.data.IDataDescriptorFactory;
import bt.metainfo.Torrent;
import bt.metainfo.TorrentId;
import bt.service.IRuntimeLifecycleBinder;
import bt.tracker.ITrackerService;
import com.google.inject.Inject;

import java.io.IOException;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Simple in-memory torrent registry, that creates new descriptors upon request.
 *
 *<p><b>Note that this class implements a service.
 * Hence, is not a part of the public API and is a subject to change.</b></p>
 */
public class AdhocTorrentRegistry implements TorrentRegistry {

    private ITrackerService trackerService;
    private IDataDescriptorFactory dataDescriptorFactory;
    private IRuntimeLifecycleBinder lifecycleBinder;

    private ConcurrentMap<TorrentId, Torrent> torrents;
    private ConcurrentMap<Torrent, TorrentDescriptor> descriptors;

    @Inject
    public AdhocTorrentRegistry(ITrackerService trackerService,
                                IDataDescriptorFactory dataDescriptorFactory,
                                IRuntimeLifecycleBinder lifecycleBinder) {

        this.trackerService = trackerService;
        this.dataDescriptorFactory = dataDescriptorFactory;
        this.lifecycleBinder = lifecycleBinder;

        this.torrents = new ConcurrentHashMap<>();
        this.descriptors = new ConcurrentHashMap<>();
    }

    @Override
    public Collection<Torrent> getTorrents() {
        return torrents.values();
    }

    @Override
    public Optional<Torrent> getTorrent(TorrentId torrentId) {
        return Optional.ofNullable(torrents.get(torrentId));
    }

    @Override
    public Optional<TorrentDescriptor> getDescriptor(Torrent torrent) {
        return Optional.ofNullable(descriptors.get(torrent));
    }

    @Override
    public TorrentDescriptor getOrCreateDescriptor(Torrent torrent, Storage storage) {
        return getDescriptor(torrent).orElseGet(() -> {
            TorrentDescriptor descriptor = new DefaultTorrentDescriptor(trackerService, torrent,
                    dataDescriptorFactory.createDescriptor(torrent, storage));
            TorrentDescriptor existing = descriptors.putIfAbsent(torrent, descriptor);
            if (existing != null) {
                descriptor = existing;
            } else {
                final TorrentDescriptor tDescriptor = descriptor;
                lifecycleBinder.onShutdown("Closing torrent data descriptor: " + tDescriptor.getDataDescriptor().toString(), () -> {
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
