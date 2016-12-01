package bt.protocol.handler;

import bt.protocol.Have;
import bt.protocol.InvalidMessageException;
import bt.protocol.DecodingContext;

import java.nio.ByteBuffer;
import java.util.Objects;

import static bt.protocol.Protocols.readInt;
import static bt.protocol.Protocols.verifyPayloadHasLength;

public final class HaveHandler extends UniqueMessageHandler<Have> {

    public HaveHandler() {
        super(Have.class);
    }

    @Override
    public int doDecode(DecodingContext context, ByteBuffer buffer) {
        verifyPayloadHasLength(Have.class, 4, buffer.remaining());
        return decodeHave(context, buffer);
    }

    @Override
    public boolean doEncode(Have message, ByteBuffer buffer) {
        return writeHave(message.getPieceIndex(), buffer);
    }

    // have: <len=0005><id=4><piece index>
    private static boolean writeHave(int pieceIndex, ByteBuffer buffer) {
        if (pieceIndex < 0) {
            throw new InvalidMessageException("Invalid piece index: " + pieceIndex);
        }
        if (buffer.remaining() < Integer.BYTES) {
            return false;
        }

        buffer.putInt(pieceIndex);
        return true;
    }

    private static int decodeHave(DecodingContext context, ByteBuffer buffer) {

        int consumed = 0;
        int length = Integer.BYTES;

        if (buffer.remaining() >= length) {
            Integer pieceIndex = Objects.requireNonNull(readInt(buffer));
            context.setMessage(new Have(pieceIndex));
            consumed = length;
        }

        return consumed;
    }
}
