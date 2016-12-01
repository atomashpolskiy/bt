package bt.protocol.extended;

import com.google.inject.Inject;
import com.google.inject.Provider;

/**
 *<p><b>Note that this class implements a service.
 * Hence, is not a part of the public API and is a subject to change.</b></p>
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
