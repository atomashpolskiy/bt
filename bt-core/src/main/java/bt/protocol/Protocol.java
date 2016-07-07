package bt.protocol;

import java.util.Set;

public interface Protocol {

    boolean isSupported(Class<? extends Message> messageType);

    boolean isSupported(byte messageTypeId);

    Set<Class<? extends Message>> getSupportedTypes();

    /**
     * @param messageTypeId Bittorrent message ID
     * @return Message type
     * @throws InvalidMessageException if this message type ID is not supported
     */
    Class<? extends Message> getMessageType(byte messageTypeId);

    /**
     * @return Message type or @{code null} if the data is insufficient
     * @throws InvalidMessageException if prefix is invalid or the message type is not supported
     */
    Class<? extends Message> readMessageType(byte[] prefix);

    /**
     * Tries to decode message from the byte buffer. If decoding is successful, then the result is set
     * into the message {@code context}
     *
     * @param context Message context. In case of success the decoded message must be put into this context.
     * @param data Byte buffer of arbitrary length containing (a part of) the message
     * @return Number of bytes consumed (0 if the provided data is insufficient)
     * @throws InvalidMessageException if data is invalid
     */
    int fromByteArray(MessageContext context, byte[] data);

    /**
     * Tries to decode message from the byte buffer. If decoding is successful, then the result is set
     * into the message {@code context}
     *
     * @param context Message context. In case of success the decoded message must be put into this context.
     * @param messageType Expected message type
     * @param payload Byte buffer of arbitrary length containing message payload
     * @param declaredPayloadLength Payload length (excluding message type ID) as declared in the original message
     * @return Number of bytes consumed (0 if the provided data is insufficient)
     * @throws InvalidMessageException if message type is not supported or the data is invalid
     */
    int fromByteArray(MessageContext context, Class<? extends Message> messageType,
                      byte[] payload, int declaredPayloadLength);

    /**
     * @return Encoded message
     * @throws InvalidMessageException if message type is not supported or encoding is not possible
     */
    byte[] toByteArray(Message message);
}
