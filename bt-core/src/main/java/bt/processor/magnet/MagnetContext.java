package bt.processor.magnet;

import bt.data.Storage;
import bt.magnet.MagnetUri;
import bt.metainfo.TorrentId;
import bt.processor.torrent.TorrentContext;
import bt.torrent.selector.PieceSelector;

import java.util.Optional;

public class MagnetContext extends TorrentContext {

    private final MagnetUri magnetUri;

    public MagnetContext(MagnetUri magnetUri, PieceSelector pieceSelector, Storage storage) {
        super(pieceSelector, storage, null);
        this.magnetUri = magnetUri;
    }

    public MagnetUri getMagnetUri() {
        return magnetUri;
    }

    @Override
    public Optional<TorrentId> getTorrentId() {
        return Optional.of(magnetUri.getTorrentId());
    }

    @Override
    public void setTorrentId(TorrentId torrentId) {
        throw new UnsupportedOperationException();
    }
}
