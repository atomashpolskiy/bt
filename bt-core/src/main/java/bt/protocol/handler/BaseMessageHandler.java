package bt.protocol.handler;

import bt.BtException;
import bt.protocol.Message;
import bt.protocol.MessageContext;
import bt.protocol.Protocols;

import java.nio.ByteBuffer;
import java.util.Objects;

import static bt.protocol.Protocols.readInt;

public abstract class BaseMessageHandler<T extends Message> implements MessageHandler<T> {

    @Override
    public boolean encode(T message, ByteBuffer buffer) {

        if (buffer.remaining() < Protocols.MESSAGE_PREFIX_SIZE) {
            return false;
        }

        int begin = buffer.position();
        buffer.position(begin + Protocols.MESSAGE_PREFIX_SIZE);
        if (doEncode(message, buffer)) {
            int end = buffer.position();
            int payloadLength = end - begin - Protocols.MESSAGE_PREFIX_SIZE;
            if (payloadLength < 0) {
                throw new BtException("Unexpected payload length: " + payloadLength);
            }
            buffer.position(begin);
            buffer.putInt(payloadLength + Protocols.MESSAGE_TYPE_SIZE);
            buffer.put(message.getMessageId().byteValue());
            buffer.position(end);
            return true;
        } else {
            buffer.position(begin);
            return false;
        }
    }

    protected abstract boolean doEncode(T message, ByteBuffer buffer);

    @Override
    public int decode(MessageContext context, ByteBuffer buffer) {

        if (buffer.remaining() < Protocols.MESSAGE_PREFIX_SIZE) {
            return 0;
        }

        Integer length = Objects.requireNonNull(readInt(buffer));
        if (buffer.remaining() < length) {
            return 0;
        }
        buffer.get(); // skip message ID

        int initialLimit = buffer.limit();
        buffer.limit(buffer.position() + length - Protocols.MESSAGE_TYPE_SIZE);
        try {
            int consumed = doDecode(context, buffer);
            if (context.getMessage() != null) {
                return consumed + Protocols.MESSAGE_PREFIX_SIZE;
            }
        } finally {
            buffer.limit(initialLimit);
        }
        return 0;
    }

    /**
     * @param buffer Buffer's remaining is equal to the declared length of the message
     */
    protected abstract int doDecode(MessageContext context, ByteBuffer buffer);
}
