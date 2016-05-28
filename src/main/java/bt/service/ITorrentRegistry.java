package bt.service;

import bt.metainfo.Torrent;

public interface ITorrentRegistry {

    Torrent getTorrent(byte[] infoHash);

    TorrentDescriptor getDescriptor(Torrent torrent);
}
