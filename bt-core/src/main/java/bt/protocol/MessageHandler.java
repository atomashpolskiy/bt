package bt.protocol;

import java.nio.ByteBuffer;
import java.util.Collection;

public interface MessageHandler<T extends Message> {

    Collection<Class<? extends T>> getSupportedTypes();

    /**
     * Determines message type based on the (part of the) message available
     * @param buffer Part of the message, excluding length and message type ID
     * @return Message type or null, if data is insufficient
     */
    Class<? extends T> readMessageType(ByteBuffer buffer);

    /**
     * Tries to decode message from the byte buffer. If decoding is successful, then the result is set
     * into the message {@code context}
     *
     * @param context Message context. In case of success the decoded message must be put into this context.
     * @param buffer Byte buffer of arbitrary length containing (a part of) the message payload
     * @param declaredPayloadLength Payload length (excluding message type ID) as declared in the original message
     * @return Number of bytes consumed (0 if the provided data is insufficient)
     * @throws InvalidMessageException if data is invalid
     */
    int decodePayload(MessageContext context, ByteBuffer buffer, int declaredPayloadLength);

    /**
     * @return Encoded message payload
     * @throws InvalidMessageException if message type is not supported or encoding is not possible
     */
    boolean encodePayload(T message, ByteBuffer buffer);
}
