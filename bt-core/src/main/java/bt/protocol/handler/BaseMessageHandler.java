package bt.protocol.handler;

import bt.BtException;
import bt.protocol.Message;
import bt.protocol.DecodingContext;
import bt.protocol.Protocols;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.Objects;

import static bt.protocol.Protocols.readInt;

/**
 * Base class for {@link MessageHandler} implementations, that work with a single message type.
 *
 * @param <T> Message type, that this handler is able to encode and decode.
 * @since 1.0
 */
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

    /**
     * Encode the payload of a message (excluding message prefix -- see {@link Protocols#MESSAGE_PREFIX_SIZE})
     * and write it into the provided buffer.
     *
     * @param buffer Byte buffer to write to.
     *               Encoded message should be placed into the buffer starting with its current position.
     *               This method should check if the buffer has sufficient space available, and return false
     *               if it's not the case.
     *               After this method returns, the buffer's {@link Buffer#position()} is used
     *               to calculate the size of message's payload, which is then specified in the message's prefix.
     * @return true if message has been successfully encoded and fully written into the provided buffer
     * @since 1.0
     */
    protected abstract boolean doEncode(T message, ByteBuffer buffer);

    @Override
    public int decode(DecodingContext context, ByteBuffer buffer) {

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
     * Decode the payload of a message (excluding message prefix -- see {@link Protocols#MESSAGE_PREFIX_SIZE})
     * and place it into the provided context.
     *
     * @param context The context to place the decoded message into.
     * @param buffer Buffer to decode from. {@link Buffer#remaining()} is set
     *               to the declared length of the message.
     *               Message payload starts precisely at buffer's {@link Buffer#position()}.
     * @since 1.0
     */
    protected abstract int doDecode(DecodingContext context, ByteBuffer buffer);
}
