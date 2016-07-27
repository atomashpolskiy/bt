package bt.service;

import bt.data.DataAccessFactory;
import bt.metainfo.Torrent;
import bt.metainfo.TorrentId;
import bt.torrent.ITorrentDescriptor;

import java.util.Optional;

public interface ITorrentRegistry {

    Torrent getTorrent(TorrentId torrentId);

    Optional<ITorrentDescriptor> getDescriptor(Torrent torrent);

    ITorrentDescriptor getOrCreateDescriptor(Torrent torrent, DataAccessFactory dataAccessFactory);
}
