package bt.protocol;

import java.nio.ByteBuffer;

public interface Protocol {

    /**
     * Tries to determine the message type based on the (part of the) message available in the byte buffer.
     *
     * @param buffer Byte buffer of arbitrary length containing (a part of) the message.
     *               Decoding should be performed starting with the current position of the buffer.
     * @return Message type or @{code null} if the data is insufficient
     * @throws InvalidMessageException if prefix is invalid or the message type is not supported
     */
    Class<? extends Message> readMessageType(ByteBuffer buffer);

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
    int fromByteArray(MessageContext context, ByteBuffer buffer);

    /**
     * Tries to encoded the provided message and place the result into the byte buffer.
     *
     * @param buffer Byte buffer of arbitrary capacity.
     *               Encoded message should be placed into the buffer starting with its current position.
     *               Protocol should check if the buffer has sufficient space available, and return false
     *               if it's not the case.
     * @return true if message has been successfully encoded and fully written into the provided buffer
     * @throws InvalidMessageException if message type is not supported or the message is invalid
     */
    boolean toByteArray(Message message, ByteBuffer buffer);
}
