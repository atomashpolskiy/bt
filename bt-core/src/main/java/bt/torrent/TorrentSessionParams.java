package bt.torrent;

import bt.torrent.selector.PieceSelector;

/**
 * Contains torrent-specific session parameters and configuration.
 *
 * @since 1.0
 */
public class TorrentSessionParams {

    private PieceSelectionStrategy selectionStrategy;
    private PieceSelector selector;

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
}
