package bt.protocol;

/**
 * @since 1.0
 */
public final class Have implements Message {

    private int pieceIndex;

    /**
     * @since 1.0
     */
    public Have(int pieceIndex) throws InvalidMessageException {

        if (pieceIndex < 0) {
            throw new InvalidMessageException("Illegal argument: piece index (" + pieceIndex + ")");
        }

        this.pieceIndex = pieceIndex;
    }

    /**
     * @since 1.0
     */
    public int getPieceIndex() {
        return pieceIndex;
    }

    @Override
    public String toString() {
        return "[" + this.getClass().getSimpleName() + "] piece index {" + pieceIndex + "}";
    }

    @Override
    public Integer getMessageId() {
        return StandardBittorrentProtocol.HAVE_ID;
    }
}
