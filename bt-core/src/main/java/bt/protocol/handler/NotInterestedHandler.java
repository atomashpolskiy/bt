package bt.protocol.handler;

import bt.protocol.MessageContext;
import bt.protocol.NotInterested;

import java.nio.ByteBuffer;

import static bt.protocol.Protocols.verifyPayloadLength;

public class NotInterestedHandler extends UniqueMessageHandler<NotInterested> {

    public NotInterestedHandler() {
        super(NotInterested.class);
    }

    @Override
    public int doDecode(MessageContext context, ByteBuffer buffer) {
        verifyPayloadLength(NotInterested.class, 0, buffer.remaining());
        context.setMessage(NotInterested.instance());
        return 0;
    }

    @Override
    public boolean doEncode(NotInterested message, ByteBuffer buffer) {
        return true;
    }
}
