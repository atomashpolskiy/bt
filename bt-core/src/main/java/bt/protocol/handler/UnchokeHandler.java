package bt.protocol.handler;

import bt.protocol.MessageContext;
import bt.protocol.Unchoke;

import java.nio.ByteBuffer;

import static bt.protocol.Protocols.verifyPayloadLength;

public class UnchokeHandler extends UniqueMessageHandler<Unchoke> {

    public UnchokeHandler() {
        super(Unchoke.class);
    }

    @Override
    public int doDecode(MessageContext context, ByteBuffer buffer) {
        verifyPayloadLength(Unchoke.class, 0, buffer.remaining());
        context.setMessage(Unchoke.instance());
        return 0;
    }

    @Override
    public boolean doEncode(Unchoke message, ByteBuffer buffer) {
        return true;
    }
}
