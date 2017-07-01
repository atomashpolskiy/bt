package bt.protocol.handler;

import bt.protocol.DecodingContext;
import bt.protocol.EncodingContext;
import bt.protocol.Unchoke;

import java.nio.ByteBuffer;

import static bt.protocol.Protocols.verifyPayloadHasLength;

public final class UnchokeHandler extends UniqueMessageHandler<Unchoke> {

    public UnchokeHandler() {
        super(Unchoke.class);
    }

    @Override
    public int doDecode(DecodingContext context, ByteBuffer buffer) {
        verifyPayloadHasLength(Unchoke.class, 0, buffer.remaining());
        context.setMessage(Unchoke.instance());
        return 0;
    }

    @Override
    public boolean doEncode(EncodingContext context, Unchoke message, ByteBuffer buffer) {
        return true;
    }
}
