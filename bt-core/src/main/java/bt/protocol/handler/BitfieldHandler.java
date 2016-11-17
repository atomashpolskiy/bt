package bt.protocol.handler;

import bt.protocol.Bitfield;
import bt.protocol.DecodingContext;

import java.nio.ByteBuffer;

public class BitfieldHandler extends UniqueMessageHandler<Bitfield> {

    public BitfieldHandler() {
        super(Bitfield.class);
    }

    @Override
    public int doDecode(DecodingContext context, ByteBuffer buffer) {
        return decodeBitfield(context, buffer, buffer.remaining());
    }

    @Override
    public boolean doEncode(Bitfield message, ByteBuffer buffer) {
        if (buffer.remaining() < message.getBitfield().length) {
            return false;
        }
        buffer.put(message.getBitfield());
        return true;
    }

    // bitfield: <len=0001+X><id=5><bitfield>
    private static int decodeBitfield(DecodingContext context, ByteBuffer buffer, int length) {

        int consumed = 0;

        if (buffer.remaining() >= length) {
            byte[] bitfield = new byte[length];
            buffer.get(bitfield);
            context.setMessage(new Bitfield(bitfield));
            consumed = length;
        }

        return consumed;
    }
}
