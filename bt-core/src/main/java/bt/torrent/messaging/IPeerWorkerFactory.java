package bt.torrent.messaging;

import bt.net.Peer;

public interface IPeerWorkerFactory {

    IPeerWorker createPeerWorker(Peer peer);
}
