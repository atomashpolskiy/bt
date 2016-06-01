package bt.service;

import bt.metainfo.Torrent;
import bt.torrent.ITorrentDescriptor;

public interface ITorrentRegistry {

    Torrent getTorrent(byte[] infoHash);

    ITorrentDescriptor getDescriptor(Torrent torrent);
}
