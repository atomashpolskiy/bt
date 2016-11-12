package bt.net;

import bt.metainfo.TorrentId;

public interface PeerActivityListener {

    void onPeerDiscovered(Peer peer);

    void onPeerConnected(TorrentId torrentId, Peer peer);

    void onPeerDisconnected(Peer peer);
}
