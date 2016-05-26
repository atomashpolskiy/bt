package bt.protocol;

public class Request implements Message {

    private int pieceIndex;
    private int offset;
    private int length;

    Request(int pieceIndex, int offset, int length) {
        this.pieceIndex = pieceIndex;
        this.offset = offset;
        this.length = length;
    }

    @Override
    public MessageType getType() {
        return MessageType.REQUEST;
    }

    public int getPieceIndex() {
        return pieceIndex;
    }

    public int getOffset() {
        return offset;
    }

    public int getLength() {
        return length;
    }
}
