package bt.protocol;

public class Cancel implements Message {

    private int pieceIndex;
    private int offset;
    private int length;

    Cancel(int pieceIndex, int offset, int length) throws InvalidMessageException {

        if (pieceIndex < 0 || offset < 0 || length <= 0) {
            throw new InvalidMessageException("Illegal arguments: piece index (" +
                    pieceIndex + "), offset (" + offset + "), length (" + length + ")");
        }

        this.pieceIndex = pieceIndex;
        this.offset = offset;
        this.length = length;
    }

    @Override
    public MessageType getType() {
        return MessageType.CANCEL;
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
