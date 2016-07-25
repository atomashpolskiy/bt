package bt.protocol.handler;

import bt.protocol.Interested;
import bt.protocol.MessageContext;

import java.nio.ByteBuffer;

import static bt.protocol.Protocols.verifyPayloadLength;

public class InterestedHandler extends UniqueMessageHandler<Interested> {

    public InterestedHandler() {
        super(Interested.class);
    }

    @Override
    public int doDecode(MessageContext context, ByteBuffer buffer) {
        verifyPayloadLength(Interested.class, 0, buffer.remaining());
        context.setMessage(Interested.instance());
        return 0;
    }

    @Override
    public boolean doEncode(Interested message, ByteBuffer buffer) {
        return true;
    }
}
