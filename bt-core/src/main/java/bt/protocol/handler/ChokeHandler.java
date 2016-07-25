package bt.protocol.handler;

import bt.protocol.Choke;
import bt.protocol.MessageContext;

import java.nio.ByteBuffer;

import static bt.protocol.Protocols.verifyPayloadLength;

public class ChokeHandler extends UniqueMessageHandler<Choke> {

    public ChokeHandler() {
        super(Choke.class);
    }

    @Override
    public int doDecode(MessageContext context, ByteBuffer buffer) {
        verifyPayloadLength(Choke.class, 0, buffer.remaining());
        context.setMessage(Choke.instance());
        return 0;
    }

    @Override
    public boolean doEncode(Choke message, ByteBuffer buffer) {
        return true;
    }
}
