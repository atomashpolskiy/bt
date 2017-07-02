package bt.torrent;

import bt.data.Storage;
import bt.torrent.selector.PieceSelector;

/**
 * Contains torrent-specific session parameters and configuration.
 *
 * @since 1.0
 */
public class TorrentSessionParams {

    private PieceSelectionStrategy selectionStrategy;
    private PieceSelector selector;
    private Storage storage;

    /**
     * Set preferred piece selection strategy.
     *
     * @since 1.0
     */
    public void setSelectionStrategy(PieceSelectionStrategy selectionStrategy) {
        this.selector = null;
        this.selectionStrategy = selectionStrategy;
    }

    /**
     * @see #setSelectionStrategy(PieceSelectionStrategy)
     * @since 1.0
     */
    public PieceSelectionStrategy getSelectionStrategy() {
        return selectionStrategy;
    }

    /**
     * Set preferred piece selection strategy.
     *
     * @since 1.1
     */
    public void setPieceSelector(PieceSelector selector) {
        this.selectionStrategy = null;
        this.selector = selector;
    }

    /**
     * @see #setPieceSelector(PieceSelector)
     * @since 1.1
     */
    public PieceSelector getPieceSelector() {
        return selector;
    }

    /**
     * Set storage.
     *
     * @since 1.3
     */
    public void setStorage(Storage storage) {
        this.storage = storage;
    }

    /**
     * @see #setStorage(Storage)
     * @since 1.3
     */
    public Storage getStorage() {
        return storage;
    }
}
