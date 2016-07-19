package bt.service;

import bt.metainfo.Torrent;

public interface PeerSourceFactory {

    PeerSource getPeerSource(Torrent torrent);
}
