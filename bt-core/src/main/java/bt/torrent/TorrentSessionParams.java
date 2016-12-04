package bt.torrent;

/**
 * Contains torrent-specific session parameters and configuration.
 *
 * @since 1.0
 */
public class TorrentSessionParams {

    private PieceSelectionStrategy selectionStrategy;

    /**
     * Set preferred piece selection strategy.
     *
     * @since 1.0
     */
    public void setSelectionStrategy(PieceSelectionStrategy selectionStrategy) {
        this.selectionStrategy = selectionStrategy;
    }

    /**
     * @see #setSelectionStrategy(PieceSelectionStrategy)
     */
    public PieceSelectionStrategy getSelectionStrategy() {
        return selectionStrategy;
    }
}
