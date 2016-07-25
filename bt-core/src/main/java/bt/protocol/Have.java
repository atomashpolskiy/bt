package bt.protocol;

public final class Have implements Message {

    private int pieceIndex;

    public Have(int pieceIndex) throws InvalidMessageException {

        if (pieceIndex < 0) {
            throw new InvalidMessageException("Illegal argument: piece index (" + pieceIndex + ")");
        }

        this.pieceIndex = pieceIndex;
    }

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
