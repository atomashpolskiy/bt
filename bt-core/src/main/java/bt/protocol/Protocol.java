package bt.protocol;

import java.nio.ByteBuffer;
import java.util.Collection;

public interface Protocol<T> {

    Collection<Class<? extends T>> getSupportedTypes();

    /**
     * Tries to determine the message type based on the (part of the) message available in the byte buffer.
     *
     * @param buffer Byte buffer of arbitrary length containing (a part of) the message.
     *               Decoding should be performed starting with the current position of the buffer.
     * @return Message type or @{code null} if the data is insufficient
     * @throws InvalidMessageException if prefix is invalid or the message type is not supported
     */
    Class<? extends T> readMessageType(ByteBuffer buffer);
}
