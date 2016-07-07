package bt.protocol.ext;

import bt.BtException;
import bt.protocol.BaseProtocol;
import bt.protocol.InvalidMessageException;
import bt.protocol.Message;
import bt.protocol.MessageContext;
import com.google.inject.Inject;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class ExtendedProtocol extends BaseProtocol {

    private static final int EXTENDED_ID = 20;
    private static final int HANDSHAKE_TYPE_ID = 0;

    private ExtendedMessageHandler<?> extendedHandshakeHandler;
    private Map<Class<? extends Message>, ExtendedMessageHandler<?>> handlers;

    private ExtendedMessageTypeMapping messageTypeMapping;
    private Map<String, ExtendedMessageHandler<?>> handlersByTypeName;

    @Inject
    public ExtendedProtocol(ExtendedMessageTypeMapping messageTypeMapping,
                            Map<String, ExtendedMessageHandler<?>> handlersByTypeName) {

        this.messageTypeMapping = messageTypeMapping;

        Map<Class<? extends Message>, ExtendedMessageHandler<?>> handlers = new HashMap<>();
        extendedHandshakeHandler = new ExtendedHandshakeMessageHandler();
        handlers.put(ExtendedHandshake.class, extendedHandshakeHandler);

        Set<Class<? extends ExtendedMessage>> seenMessageTypes = new HashSet<>();
        handlersByTypeName.values().forEach(handler -> {

            Class<? extends ExtendedMessage> messageType = Objects.requireNonNull(handler.getMessageType());
            if (seenMessageTypes.contains(messageType)) {
                throw new BtException("Encountered duplicate handler for " + messageType.getSimpleName());
            }
            seenMessageTypes.add(messageType);

            handlers.put(handler.getMessageType(), handler);
        });

        this.handlers = Collections.unmodifiableMap(handlers);
        this.handlersByTypeName = handlersByTypeName;
    }

    @Override
    public boolean isSupported(Class<? extends Message> messageType) {
        return handlers.containsKey(messageType);
    }

    @Override
    public boolean isSupported(byte messageTypeId) {
        return messageTypeId == EXTENDED_ID;
    }

    @Override
    public Set<Class<? extends Message>> getSupportedTypes() {
        return handlers.keySet();
    }

    @Override
    public Class<? extends Message> getMessageType(byte messageTypeId) {
        if (messageTypeId != EXTENDED_ID) {
            throw new InvalidMessageException("Unsupported message type ID: " + messageTypeId);
        }
        return ExtendedMessage.class; // return generic super-type (dirty, but should be sufficient for upstream protocol's needs)
    }

    @Override
    public int fromByteArray(MessageContext context, Class<? extends Message> messageType, byte[] payload, int declaredPayloadLength) {

        assertSupported(Objects.requireNonNull(messageType));
        if (payload.length < declaredPayloadLength) {
            // not ready yet
            return 0;
        }

        int typeId = payload[0];
        // TODO: start parsing at non-zero index (skip the beginning) and stop parsing when complete (ignoring the trailing bytes)
        payload = Arrays.copyOfRange(payload, 1, declaredPayloadLength);

        ExtendedMessageHandler<?> handler;
        if (typeId == HANDSHAKE_TYPE_ID) {
            handler = extendedHandshakeHandler;
        } else {
            String extendedType = messageTypeMapping.getTypeNameForId(typeId);
            if (extendedType == null) {
                throw new BtException("Received unsupported extended message id: " + typeId);
            }
            handler = handlersByTypeName.get(extendedType);
        }
        return handler.fromByteArray(context, payload);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected byte[] doEncode(Message message) {
        Class<? extends Message> messageType = message.getClass();
        assertSupported(messageType);
        return ((ExtendedMessageHandler<ExtendedMessage>) handlers.get(messageType)).toByteArray((ExtendedMessage) message);
    }

    private void assertSupported(Class<? extends Message> messageType) {
        if (!isSupported(messageType)) {
            throw new InvalidMessageException("Unsupported message type: " + messageType.getSimpleName());
        }
    }
}
