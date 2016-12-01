package bt.protocol.handler;

import bt.protocol.Interested;
import bt.protocol.DecodingContext;

import java.nio.ByteBuffer;

import static bt.protocol.Protocols.verifyPayloadHasLength;

public final class InterestedHandler extends UniqueMessageHandler<Interested> {

    public InterestedHandler() {
        super(Interested.class);
    }

    @Override
    public int doDecode(DecodingContext context, ByteBuffer buffer) {
        verifyPayloadHasLength(Interested.class, 0, buffer.remaining());
        context.setMessage(Interested.instance());
        return 0;
    }

    @Override
    public boolean doEncode(Interested message, ByteBuffer buffer) {
        return true;
    }
}
