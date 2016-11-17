package bt.protocol.extended;

import com.google.inject.Inject;
import com.google.inject.Provider;

/**
 * @since 1.0
 */
public class ExtendedHandshakeProvider implements Provider<ExtendedHandshake> {

    private ExtendedHandshake extendedHandshake;

    @Inject
    public ExtendedHandshakeProvider(ExtendedMessageTypeMapping messageTypeMapping) {
        ExtendedHandshake.Builder builder = ExtendedHandshake.builder();
        messageTypeMapping.visitMappings(builder::addMessageType);
        extendedHandshake = builder.build();
    }

    @Override
    public ExtendedHandshake get() {
        return extendedHandshake;
    }
}
