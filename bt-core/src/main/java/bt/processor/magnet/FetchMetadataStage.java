package bt.processor.magnet;

import bt.magnet.MagnetUri;
import bt.metainfo.IMetadataService;
import bt.metainfo.Torrent;
import bt.metainfo.TorrentFile;
import bt.metainfo.TorrentId;
import bt.metainfo.TorrentSource;
import bt.net.InetPeer;
import bt.processor.BaseProcessingStage;
import bt.processor.ProcessingStage;
import bt.runtime.Config;
import bt.torrent.TorrentDescriptor;
import bt.torrent.TorrentRegistry;
import bt.torrent.messaging.BitfieldCollectingConsumer;
import bt.torrent.messaging.MetadataConsumer;
import bt.tracker.AnnounceKey;
import bt.tracker.ITrackerService;
import bt.tracker.TrackerAnnouncer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class FetchMetadataStage extends BaseProcessingStage<MagnetContext> {

    private IMetadataService metadataService;
    private TorrentRegistry torrentRegistry;
    private ITrackerService trackerService;
    private Config config;

    public FetchMetadataStage(ProcessingStage<MagnetContext> next,
                              IMetadataService metadataService,
                              TorrentRegistry torrentRegistry,
                              ITrackerService trackerService,
                              Config config) {
        super(next);
        this.metadataService = metadataService;
        this.torrentRegistry = torrentRegistry;
        this.trackerService = trackerService;
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

        context.getMagnetUri().getPeerAddresses().forEach(address -> {
            context.getSession().get().onPeerDiscovered(new InetPeer(address));
        });

        Torrent torrent = metadataConsumer.waitForTorrent();
        Optional<AnnounceKey> announceKey = createAnnounceKey(context.getMagnetUri());
        torrent = amendTorrent(torrent, context.getMagnetUri().getDisplayName(), announceKey);
        if (announceKey.isPresent()) {
            TrackerAnnouncer announcer = new TrackerAnnouncer(trackerService, torrent);
            announcer.start();
        }

        context.setTorrent(torrent);

        context.setBitfieldConsumer(bitfieldConsumer);
    }

    private TorrentDescriptor getDescriptor(TorrentId torrentId) {
        return torrentRegistry.getDescriptor(torrentId)
                .orElseThrow(() -> new IllegalStateException("No descriptor present for torrent ID: " + torrentId));
    }

    private Optional<AnnounceKey> createAnnounceKey(MagnetUri magnetUri) {
        Optional<AnnounceKey> announceKey;

        Collection<String> trackerUrls = magnetUri.getTrackerUrls();
        if (trackerUrls.isEmpty()) {
            announceKey = Optional.empty();
        } else {
            List<List<String>> trackerTiers = Collections.singletonList(new ArrayList<>(trackerUrls));
            announceKey = Optional.of(new AnnounceKey(trackerTiers));
        }
        return announceKey;
    }

    private Torrent amendTorrent(Torrent delegate, Optional<String> displayName, Optional<AnnounceKey> announceKey) {
        Torrent torrent;

        if (displayName.isPresent() || announceKey.isPresent()) {
            torrent = new Torrent() {
                @Override
                public TorrentSource getSource() {
                    return delegate.getSource();
                }

                @Override
                public Optional<AnnounceKey> getAnnounceKey() {
                    return announceKey;
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
