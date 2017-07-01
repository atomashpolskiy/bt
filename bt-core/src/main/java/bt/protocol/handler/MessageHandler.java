package bt.protocol.handler;

import bt.protocol.DecodingContext;
import bt.protocol.EncodingContext;
import bt.protocol.InvalidMessageException;
import bt.protocol.Message;
import bt.protocol.Protocol;

import java.nio.ByteBuffer;

/**
 * Message encoder/decoder.
 *
 * @param <T> Message type or a common supertype of several message types,
 *            that this handler is able to encode and decode.
 * @since 1.0
 */
public interface MessageHandler<T extends Message> extends Protocol<T> {

    /**
     * Tries to encode the provided message and place the result into the byte buffer.
     *
     * @param context Encoding context
     * @param buffer Byte buffer of arbitrary capacity.
     *               Encoded message should be placed into the buffer starting with its current position.
     *               Protocol should check if the buffer has sufficient space available, and return false
     *               if it's not the case.
     * @return true if message has been successfully encoded and fully written into the provided buffer
     * @throws InvalidMessageException if message type is not supported or the message is invalid
     * @since 1.3
     */
    boolean encode(EncodingContext context, T message, ByteBuffer buffer);

    /**
     * Tries to decode message from the byte buffer. If decoding is successful, then the result is set
     * into the message {@code context}
     *
     * @param context Message context. In case of success the decoded message must be put into this context.
     * @param buffer Byte buffer of arbitrary length containing (a part of) the message.
     *               Decoding should be performed starting with the current position of the buffer.
     * @return Number of bytes consumed (0 if the provided data is insufficient)
     * @throws InvalidMessageException if data is invalid
     * @since 1.0
     */
    int decode(DecodingContext context, ByteBuffer buffer);
}
