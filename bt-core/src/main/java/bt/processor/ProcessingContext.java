package bt.processor;

import bt.metainfo.Torrent;
import bt.metainfo.TorrentId;
import bt.torrent.TorrentSessionState;

import java.util.Optional;

public interface ProcessingContext {

    Optional<TorrentId> getTorrentId();

    Optional<Torrent> getTorrent();

    Optional<TorrentSessionState> getState();
}
