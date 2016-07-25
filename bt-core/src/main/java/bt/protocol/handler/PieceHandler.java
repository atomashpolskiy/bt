package bt.protocol.handler;

import bt.protocol.InvalidMessageException;
import bt.protocol.MessageContext;
import bt.protocol.Piece;

import java.nio.ByteBuffer;
import java.util.Objects;

import static bt.protocol.Protocols.readInt;

public class PieceHandler extends UniqueMessageHandler<Piece> {

    public PieceHandler() {
        super(Piece.class);
    }

    @Override
    public int doDecode(MessageContext context, ByteBuffer buffer) {
        return decodePiece(context, buffer, buffer.remaining());
    }

    @Override
    public boolean doEncode(Piece message, ByteBuffer buffer) {
        return writePiece(message.getPieceIndex(), message.getOffset(), message.getBlock(), buffer);
    }

    // piece: <len=0009+X><id=7><index><begin><block>
    private static boolean writePiece(int pieceIndex, int offset, byte[] block, ByteBuffer buffer) {

        if (pieceIndex < 0 || offset < 0) {
            throw new InvalidMessageException("Invalid arguments: pieceIndex (" + pieceIndex
                    + "), offset (" + offset + ")");
        }
        if (block.length == 0) {
            throw new InvalidMessageException("Invalid block: empty");
        }
        if (buffer.remaining() < Integer.BYTES * 2 + block.length) {
            return false;
        }

        buffer.putInt(pieceIndex);
        buffer.putInt(offset);
        buffer.put(block);

        return true;
    }

    private static int decodePiece(MessageContext context, ByteBuffer buffer, int length) {

        int consumed = 0;

        if (buffer.remaining() >= length) {

            int pieceIndex = Objects.requireNonNull(readInt(buffer));
            int blockOffset = Objects.requireNonNull(readInt(buffer));
            byte[] block = new byte[length - Integer.BYTES * 2];
            buffer.get(block);

            context.setMessage(new Piece(pieceIndex, blockOffset, block));
            consumed = length;
        }

        return consumed;
    }
}
