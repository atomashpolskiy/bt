package bt.torrent.messaging;

import bt.metainfo.TorrentId;
import bt.net.Peer;

import java.util.Optional;

/**
 *<p><b>Note that this class implements a service.
 * Hence, is not a part of the public API and is a subject to change.</b></p>
 */
public class PeerWorkerFactory implements IPeerWorkerFactory {

    private MessageRouter router;

    public PeerWorkerFactory(MessageRouter router) {
        this.router = router;
    }

    @Override
    public PeerWorker createPeerWorker(Peer peer) {
        return createPeerWorker(Optional.empty(), peer);
    }

    @Override
    public PeerWorker createPeerWorker(TorrentId torrentId, Peer peer) {
        return createPeerWorker(Optional.of(torrentId), peer);
    }

    private PeerWorker createPeerWorker(Optional<TorrentId> torrentId, Peer peer) {
        return new RoutingPeerWorker(peer, torrentId, router);
    }
}
