package bt.protocol.handler;

import bt.protocol.InvalidMessageException;
import bt.protocol.Message;
import bt.protocol.MessageContext;
import bt.protocol.Protocol;

import java.nio.ByteBuffer;

public interface MessageHandler<T extends Message> extends Protocol<T> {

    /**
     * Tries to encode the provided message and place the result into the byte buffer.
     *
     * @param buffer Byte buffer of arbitrary capacity.
     *               Encoded message should be placed into the buffer starting with its current position.
     *               Protocol should check if the buffer has sufficient space available, and return false
     *               if it's not the case.
     * @return true if message has been successfully encoded and fully written into the provided buffer
     * @throws InvalidMessageException if message type is not supported or the message is invalid
     */
    boolean encode(T message, ByteBuffer buffer);

    /**
     * Tries to decode message from the byte buffer. If decoding is successful, then the result is set
     * into the message {@code context}
     *
     * @param context Message context. In case of success the decoded message must be put into this context.
     * @param buffer Byte buffer of arbitrary length containing (a part of) the message.
     *               Decoding should be performed starting with the current position of the buffer.
     * @return Number of bytes consumed (0 if the provided data is insufficient)
     * @throws InvalidMessageException if data is invalid
     */
    int decode(MessageContext context, ByteBuffer buffer);
}
