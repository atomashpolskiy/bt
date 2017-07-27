package bt.processor.magnet;

import bt.metainfo.IMetadataService;
import bt.metainfo.Torrent;
import bt.metainfo.TorrentFile;
import bt.metainfo.TorrentId;
import bt.metainfo.TorrentSource;
import bt.net.InetPeer;
import bt.peer.IPeerRegistry;
import bt.processor.BaseProcessingStage;
import bt.processor.ProcessingStage;
import bt.runtime.Config;
import bt.torrent.TorrentDescriptor;
import bt.torrent.TorrentRegistry;
import bt.torrent.messaging.BitfieldCollectingConsumer;
import bt.torrent.messaging.MetadataConsumer;
import bt.tracker.AnnounceKey;
import bt.tracker.ITrackerService;

import java.util.List;
import java.util.Optional;

public class FetchMetadataStage extends BaseProcessingStage<MagnetContext> {

    private IMetadataService metadataService;
    private TorrentRegistry torrentRegistry;
    private ITrackerService trackerService;
    private IPeerRegistry peerRegistry;
    private Config config;

    public FetchMetadataStage(ProcessingStage<MagnetContext> next,
                              IMetadataService metadataService,
                              TorrentRegistry torrentRegistry,
                              ITrackerService trackerService,
                              IPeerRegistry peerRegistry,
                              Config config) {
        super(next);
        this.metadataService = metadataService;
        this.torrentRegistry = torrentRegistry;
        this.trackerService = trackerService;
        this.peerRegistry = peerRegistry;
        this.config = config;
    }

    @Override
    protected void doExecute(MagnetContext context) {
        TorrentId torrentId = context.getMagnetUri().getTorrentId();

        MetadataConsumer metadataConsumer = new MetadataConsumer(metadataService, torrentId, config);
        context.getRouter().registerMessagingAgent(metadataConsumer);

        // need to also receive Bitfields and Haves (without validation for the number of pieces...)
        BitfieldCollectingConsumer bitfieldConsumer = new BitfieldCollectingConsumer();
        context.getRouter().registerMessagingAgent(bitfieldConsumer);

        getDescriptor(torrentId).start();

        context.getMagnetUri().getPeerAddresses().forEach(peerAddress -> {
            peerRegistry.addPeer(torrentId, new InetPeer(peerAddress));
        });

        context.getMagnetUri().getTrackerUrls().forEach(trackerUrl -> {
            // TODO: should we use a single multi-key instead, containing all trackers from the magnet link?
            peerRegistry.addPeerSource(torrentId, new AnnounceKey(trackerUrl));
        });
        // TODO: do we need a tracker announce for magnet-based torrents?
//        TrackerAnnouncer announcer = new TrackerAnnouncer(trackerService, torrentId, null);
//        announcer.start();

        context.getMagnetUri().getPeerAddresses().forEach(address -> {
            context.getSession().get().onPeerDiscovered(new InetPeer(address));
        });

        Torrent torrent = metadataConsumer.waitForTorrent();
        torrent = amendTorrent(torrent, context.getMagnetUri().getDisplayName());

        context.setTorrent(torrent);

        context.setBitfieldConsumer(bitfieldConsumer);
    }

    private TorrentDescriptor getDescriptor(TorrentId torrentId) {
        return torrentRegistry.getDescriptor(torrentId)
                .orElseThrow(() -> new IllegalStateException("No descriptor present for torrent ID: " + torrentId));
    }

    private Torrent amendTorrent(Torrent delegate, Optional<String> displayName) {
        Torrent torrent;

        if (displayName.isPresent()) {
            torrent = new Torrent() {
                @Override
                public TorrentSource getSource() {
                    return delegate.getSource();
                }

                @Override
                public Optional<AnnounceKey> getAnnounceKey() {
                    return Optional.empty();
                }

                @Override
                public TorrentId getTorrentId() {
                    return delegate.getTorrentId();
                }

                @Override
                public String getName() {
                    // prefer name from the info dictionary
                    String name = delegate.getName();
                    return (name == null) ? displayName.orElse(null) : name;
                }

                @Override
                public long getChunkSize() {
                    return delegate.getChunkSize();
                }

                @Override
                public Iterable<byte[]> getChunkHashes() {
                    return delegate.getChunkHashes();
                }

                @Override
                public long getSize() {
                    return delegate.getSize();
                }

                @Override
                public List<TorrentFile> getFiles() {
                    return delegate.getFiles();
                }

                @Override
                public boolean isPrivate() {
                    return delegate.isPrivate();
                }
            };
        } else {
            torrent = delegate;
        }
        return torrent;
    }
}
