package bt.torrent;

import bt.metainfo.Torrent;

public interface TorrentSession {

    Torrent getTorrent();

    TorrentSessionState getState();
}
