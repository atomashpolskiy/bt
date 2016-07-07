package bt.protocol;

import bt.BtException;
import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class ProtocolChain extends BaseProtocol {

    private List<Protocol> delegates;
    private Set<Class<? extends Message>> supportedTypes;

    @Inject
    public ProtocolChain(Set<Protocol> extensions) {

        delegates = new ArrayList<>(extensions.size() + 2);
        delegates.add(new StandardBittorrentProtocol());
        delegates.addAll(extensions);

        Set<Class<? extends Message>> supportedTypes = new HashSet<>();
        for (Protocol delegate : delegates) {
            supportedTypes.addAll(delegate.getSupportedTypes());
        }
        this.supportedTypes = Collections.unmodifiableSet(supportedTypes);
    }

    @Override
    public boolean isSupported(Class<? extends Message> messageType) {
        return supportedTypes.contains(messageType);
    }

    @Override
    public boolean isSupported(byte messageTypeId) {

        for (Protocol delegate : delegates) {
            if (delegate.isSupported(messageTypeId)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Set<Class<? extends Message>> getSupportedTypes() {
        return supportedTypes;
    }

    @Override
    public Class<? extends Message> getMessageType(byte messageTypeId) {

        Class<? extends Message> messageType;
        for (Protocol delegate : delegates) {
            if (delegate.isSupported(messageTypeId)) {
                messageType = delegate.getMessageType(messageTypeId);
                if (messageType != null) {
                    return messageType;
                }
            }
        }
        throw new InvalidMessageException("Unsupported message type ID: " + messageTypeId);
    }

    @Override
    public int fromByteArray(MessageContext context, Class<? extends Message> messageType,
                             byte[] payload, int declaredPayloadLength) {

        assertSupported(Objects.requireNonNull(messageType));

        int consumed;
        for (Protocol delegate : delegates) {
            if (delegate.isSupported(messageType)) {
                consumed = delegate.fromByteArray(context, messageType, payload, declaredPayloadLength);
                if (context.getMessage() != null) {
                    return consumed;
                }
            }
        }
        return 0;
    }

    @Override
    protected byte[] doEncode(Message message) {

        assertSupported(message.getClass());

        byte[] bytes;
        for (Protocol delegate : delegates) {
            if (delegate.isSupported(message.getClass())) {
                bytes = delegate.toByteArray(message);
                if (bytes != null) {
                    return bytes;
                }
            }
        }
        throw new BtException("Failed to serialize message: " + message);
    }

    private void assertSupported(Class<? extends Message> messageType) {
        if (!supportedTypes.contains(messageType)) {
            throw new InvalidMessageException("Unsupported message type: " + messageType.getSimpleName());
        }
    }
}
