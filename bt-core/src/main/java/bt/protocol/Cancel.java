package bt.protocol;

/**
 * @since 1.0
 */
public final class Cancel implements Message {

    private int pieceIndex;
    private int offset;
    private int length;

    /**
     * @since 1.0
     */
    public Cancel(int pieceIndex, int offset, int length) throws InvalidMessageException {

        if (pieceIndex < 0 || offset < 0 || length <= 0) {
            throw new InvalidMessageException("Illegal arguments: piece index (" +
                    pieceIndex + "), offset (" + offset + "), length (" + length + ")");
        }

        this.pieceIndex = pieceIndex;
        this.offset = offset;
        this.length = length;
    }

    /**
     * @since 1.0
     */
    public int getPieceIndex() {
        return pieceIndex;
    }

    /**
     * @since 1.0
     */
    public int getOffset() {
        return offset;
    }

    /**
     * @since 1.0
     */
    public int getLength() {
        return length;
    }

    @Override
    public String toString() {
        return "[" + this.getClass().getSimpleName() + "] piece index {" + pieceIndex + "}, offset {" + offset +
                "}, length {" + length + "}";
    }

    @Override
    public Integer getMessageId() {
        return StandardBittorrentProtocol.CANCEL_ID;
    }
}
