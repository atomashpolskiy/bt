package bt.protocol;

public class Have implements Message {

    private int pieceIndex;

    Have(int pieceIndex) throws InvalidMessageException {

        if (pieceIndex < 0) {
            throw new InvalidMessageException("Illegal argument: piece index (" + pieceIndex + ")");
        }

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
