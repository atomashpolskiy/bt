package bt.runtime.protocol.ext;

import bt.BtException;
import bt.protocol.InvalidMessageException;
import bt.protocol.Message;
import bt.protocol.MessageContext;
import bt.protocol.MessageHandler;
import com.google.inject.Inject;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ExtendedProtocol implements MessageHandler<Message> {

    public static final int EXTENDED_MESSAGE_ID = 20;
    private static final int HANDSHAKE_TYPE_ID = 0;

    private MessageHandler<?> extendedHandshakeHandler;

    private Map<Class<? extends Message>, MessageHandler<?>> handlers;
    private Map<String, Class<? extends Message>> uniqueTypes;
    private Map<String, MessageHandler<?>> handlersByTypeName;

    private ExtendedMessageTypeMapping messageTypeMapping;

    @Inject
    public ExtendedProtocol(ExtendedMessageTypeMapping messageTypeMapping,
                            Map<String, MessageHandler<?>> handlersByTypeName) {

        this.messageTypeMapping = messageTypeMapping;

        Map<Class<? extends Message>, MessageHandler<?>> handlers = new HashMap<>();
        extendedHandshakeHandler = new ExtendedHandshakeMessageHandler();
        handlers.put(ExtendedHandshake.class, extendedHandshakeHandler);

        Map<String, Class<? extends Message>> uniqueTypes = new HashMap<>();
        handlersByTypeName.forEach((typeName, handler) -> {

            if (handler.getSupportedTypes().isEmpty()) {
                throw new BtException("No supported types declared in handler: " + handler.getClass().getName());
            } else {
                uniqueTypes.put(typeName, handler.getSupportedTypes().iterator().next());
            }

            handler.getSupportedTypes().forEach(messageType -> {
                if (handlers.keySet().contains(messageType)) {
                    throw new BtException("Encountered duplicate handler for message type: " + messageType.getSimpleName());
                }
                handlers.put(messageType, handler);
            });
        });

        this.handlers = Collections.unmodifiableMap(handlers);
        this.handlersByTypeName = handlersByTypeName;
        this.uniqueTypes = uniqueTypes;
    }

    @Override
    public Collection<Class<? extends Message>> getSupportedTypes() {
        return handlers.keySet();
    }

    @Override
    public Class<? extends Message> readMessageType(byte[] data) {
        if (data.length == 0) {
            return null;
        }
        Integer messageTypeId = (int) data[0];
        if (messageTypeId == HANDSHAKE_TYPE_ID) {
            return ExtendedHandshake.class;
        }

        Class<? extends Message> messageType;
        String typeName = messageTypeMapping.getTypeNameForId(messageTypeId);
        if (typeName == null) {
            throw new InvalidMessageException("Unknown message type ID: " + messageTypeId);
        }
        messageType = uniqueTypes.get(typeName);
        if (messageType == null) {
            messageType = handlersByTypeName.get(typeName).readMessageType(Arrays.copyOfRange(data, 1, data.length));
        }
        return messageType;
    }

    @Override
    public int decodePayload(MessageContext context, byte[] data, int declaredPayloadLength) {

        if (data.length < declaredPayloadLength) {
            // not ready yet
            return 0;
        }

        int typeId = data[0];
        // TODO: start parsing at non-zero index (skip the beginning) and stop parsing when complete (ignoring the trailing bytes)
        data = Arrays.copyOfRange(data, 1, declaredPayloadLength);

        MessageHandler<?> handler;
        if (typeId == HANDSHAKE_TYPE_ID) {
            handler = extendedHandshakeHandler;
        } else {
            String extendedType = messageTypeMapping.getTypeNameForId(typeId);
            if (extendedType == null) {
                throw new BtException("Received unsupported extended message id: " + typeId);
            }
            handler = handlersByTypeName.get(extendedType);
        }
        int consumed = handler.decodePayload(context, data, declaredPayloadLength - 1);
        if (consumed > 0) {
            consumed += 1; // type ID was trimmed when passing data to handler
        }
        return consumed;
    }

    @Override
    public byte[] encodePayload(Message message) {
        Class<? extends Message> messageType = message.getClass();
        return doEncode(message, messageType);
    }

    @SuppressWarnings("unchecked")
    private <T extends Message> byte[] doEncode(Message message, Class<T> messageType) {
        byte[] payload = ((MessageHandler<T>) handlers.get(messageType)).encodePayload((T) message);
        byte[] bytes = new byte[payload.length + 1];

        if (ExtendedHandshake.class.equals(messageType)) {
            bytes[0] = HANDSHAKE_TYPE_ID;
        } else {
            bytes[0] = messageTypeMapping.getIdForTypeName(messageTypeMapping.getTypeNameForJavaType(messageType)).byteValue();
        }

        System.arraycopy(payload, 0, bytes, 1, payload.length);
        return bytes;
    }
}
