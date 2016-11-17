package bt.protocol;

import java.nio.ByteBuffer;
import java.util.Collection;

/**
 * Protocol is responsible for determining message types.
 * When a message is received from peer,
 * the protocol is the first entity in the message processing chain
 * to correctly determine the type of message
 * and delegate the decoding of the message
 * to a particular message handler.
 *
 * @param <T> Common supertype for all message types, supported by this protocol.
 * @since 1.0
 */
public interface Protocol<T> {

    /**
     * @return All message types, supported by this protocol.
     * @since 1.0
     */
    Collection<Class<? extends T>> getSupportedTypes();

    /**
     * Tries to determine the message type based on the (part of the) message available in the byte buffer.
     *
     * @param buffer Byte buffer of arbitrary length containing (a part of) the message.
     *               Decoding should be performed starting with the current position of the buffer.
     * @return Message type or @{code null} if the data is insufficient
     * @throws InvalidMessageException if prefix is invalid or the message type is not supported
     * @since 1.0
     */
    Class<? extends T> readMessageType(ByteBuffer buffer);
}
