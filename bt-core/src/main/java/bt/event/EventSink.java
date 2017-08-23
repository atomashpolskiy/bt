package bt.event;

import bt.metainfo.TorrentId;
import bt.net.Peer;

public interface EventSink {

    void firePeerDiscovered(TorrentId torrentId, Peer peer);

    void firePeerConnected(TorrentId torrentId, Peer peer);

    void firePeerDisconnected(TorrentId torrentId, Peer peer);
}
