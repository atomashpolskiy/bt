package bt.processor.magnet;

import bt.data.Storage;
import bt.magnet.MagnetUri;
import bt.processor.torrent.TorrentContext;
import bt.torrent.selector.PieceSelector;

public class MagnetContext extends TorrentContext {

    private final MagnetUri magnetUri;

    public MagnetContext(MagnetUri magnetUri, PieceSelector pieceSelector, Storage storage) {
        super(pieceSelector, storage, null);
        this.magnetUri = magnetUri;
    }

    public MagnetUri getMagnetUri() {
        return magnetUri;
    }
}
