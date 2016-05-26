package bt.protocol;

public class Piece implements Message {

    private int pieceIndex;
    private int offset;
    private byte[] block;

    Piece(int pieceIndex, int offset, byte[] block) {
        this.pieceIndex = pieceIndex;
        this.offset = offset;
        this.block = block;
    }

    @Override
    public MessageType getType() {
        return MessageType.PIECE;
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
}
