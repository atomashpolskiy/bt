package bt.protocol;

/**
 * @since 1.0
 */
public final class Piece implements Message {

    private int pieceIndex;
    private int offset;
    private byte[] block;

    /**
     * @since 1.0
     */
    public Piece(int pieceIndex, int offset, byte[] block) throws InvalidMessageException {

        if (pieceIndex < 0 || offset < 0 || block.length == 0) {
            throw new InvalidMessageException("Invalid arguments: piece index (" +
                    pieceIndex + "), offset (" + offset + "), block length (" + block.length + ")");
        }
        this.pieceIndex = pieceIndex;
        this.offset = offset;
        this.block = block;
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
    public byte[] getBlock() {
        return block;
    }

    @Override
    public String toString() {
        return "[" + this.getClass().getSimpleName() + "] piece index {" + pieceIndex + "}, offset {" + offset +
                "}, block {" + block.length + " bytes}";
    }

    @Override
    public Integer getMessageId() {
        return StandardBittorrentProtocol.PIECE_ID;
    }
}
