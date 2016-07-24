package bt.protocol;

import java.nio.ByteBuffer;

public interface Protocol {

    /**
     * @return Message type or @{code null} if the data is insufficient
     * @throws InvalidMessageException if prefix is invalid or the message type is not supported
     */
    Class<? extends Message> readMessageType(ByteBuffer buffer);

    /**
     * Tries to decode message from the byte buffer. If decoding is successful, then the result is set
     * into the message {@code context}
     *
     * @param context Message context. In case of success the decoded message must be put into this context.
     * @param buffer Byte buffer of arbitrary length containing (a part of) the message
     * @return Number of bytes consumed (0 if the provided data is insufficient)
     * @throws InvalidMessageException if data is invalid
     */
    int fromByteArray(MessageContext context, ByteBuffer buffer);

    /**
     * @return Encoded message
     * @throws InvalidMessageException if message type is not supported or encoding is not possible
     */
    boolean toByteArray(Message message, ByteBuffer buffer);
}
