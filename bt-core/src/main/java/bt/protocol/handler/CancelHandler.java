package bt.protocol.handler;

import bt.protocol.Cancel;
import bt.protocol.InvalidMessageException;
import bt.protocol.DecodingContext;

import java.nio.ByteBuffer;
import java.util.Objects;

import static bt.protocol.Protocols.readInt;
import static bt.protocol.Protocols.verifyPayloadHasLength;

public final class CancelHandler extends UniqueMessageHandler<Cancel> {

    public CancelHandler() {
        super(Cancel.class);
    }

    @Override
    public int doDecode(DecodingContext context, ByteBuffer buffer) {
        verifyPayloadHasLength(Cancel.class, 12, buffer.remaining());
        return decodeCancel(context, buffer);
    }

    @Override
    public boolean doEncode(Cancel message, ByteBuffer buffer) {
        return writeCancel(message.getPieceIndex(), message.getOffset(), message.getLength(), buffer);
    }

    // cancel: <len=0013><id=8><index><begin><length>
    private static boolean writeCancel(int pieceIndex, int offset, int length, ByteBuffer buffer) {

        if (pieceIndex < 0 || offset < 0 || length <= 0) {
            throw new InvalidMessageException("Invalid arguments: pieceIndex (" + pieceIndex
                    + "), offset (" + offset + "), length (" + length + ")");
        }
        if (buffer.remaining() < Integer.BYTES * 3) {
            return false;
        }

        buffer.putInt(pieceIndex);
        buffer.putInt(offset);
        buffer.putInt(length);

        return true;
    }

    private static int decodeCancel(DecodingContext context, ByteBuffer buffer) {

        int consumed = 0;
        int length = Integer.BYTES * 3;

        if (buffer.remaining() >= length) {

            int pieceIndex = Objects.requireNonNull(readInt(buffer));
            int blockOffset = Objects.requireNonNull(readInt(buffer));
            int blockLength = Objects.requireNonNull(readInt(buffer));

            context.setMessage(new Cancel(pieceIndex, blockOffset, blockLength));
            consumed = length;
        }

        return consumed;
    }
}
