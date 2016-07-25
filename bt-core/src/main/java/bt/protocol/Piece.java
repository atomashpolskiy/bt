package bt.protocol;

public final class Piece implements Message {

    private int pieceIndex;
    private int offset;
    private byte[] block;

    public Piece(int pieceIndex, int offset, byte[] block) throws InvalidMessageException {

        if (pieceIndex < 0 || offset < 0 || block.length == 0) {
            throw new InvalidMessageException("Invalid arguments: piece index (" +
                    pieceIndex + "), offset (" + offset + "), block length (" + block.length + ")");
        }
        this.pieceIndex = pieceIndex;
        this.offset = offset;
        this.block = block;
    }

    public int getPieceIndex() {
        return pieceIndex;
    }

    public int getOffset() {
        return offset;
    }

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
