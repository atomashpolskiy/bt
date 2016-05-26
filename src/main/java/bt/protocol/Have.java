package bt.protocol;

public class Have implements Message {

    private int pieceIndex;

    Have(int pieceIndex) {
        this.pieceIndex = pieceIndex;
    }

    @Override
    public MessageType getType() {
        return MessageType.HAVE;
    }

    public int getPieceIndex() {
        return pieceIndex;
    }
}
