package bt.processor.torrent;

import bt.data.Storage;
import bt.metainfo.Torrent;
import bt.metainfo.TorrentId;
import bt.processor.ProcessingContext;
import bt.torrent.TorrentSession;
import bt.torrent.messaging.TorrentWorker;
import bt.torrent.selector.PieceSelector;

public class TorrentContext implements ProcessingContext {

    private TorrentId torrentId;
    private Torrent torrent;
    private PieceSelector pieceSelector;
    private TorrentSession session;
    private Storage storage;

    public TorrentContext(TorrentId torrentId,
                          PieceSelector pieceSelector,
                          TorrentSession session,
                          Storage storage) {
        this.torrentId = torrentId;
        this.pieceSelector = pieceSelector;
        this.session = session;
        this.storage = storage;
    }

    @Override
    public TorrentId getTorrentId() {
        return torrentId;
    }

    public Torrent getTorrent() {
        return torrent;
    }

    public void setTorrent(Torrent torrent) {
        this.torrent = torrent;
    }

    public PieceSelector getPieceSelector() {
        return pieceSelector;
    }

    public TorrentSession getSession() {
        return session;
    }

    public TorrentWorker getTorrentWorker() {
        return session.getTorrentWorker();
    }

    public Storage getStorage() {
        return storage;
    }
}
