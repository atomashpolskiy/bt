package bt.protocol.handler;

import bt.protocol.Message;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Collections;

abstract class UniqueMessageHandler<T extends Message> extends BaseMessageHandler<T> {

    private Class<T> type;
    private Collection<Class<? extends T>> supportedTypes;

    protected UniqueMessageHandler(Class<T> type) {
        this.type = type;
        supportedTypes = Collections.singleton(type);
    }

    @Override
    public Collection<Class<? extends T>> getSupportedTypes() {
        return supportedTypes;
    }

    @Override
    public Class<? extends T> readMessageType(ByteBuffer buffer) {
        return type;
    }
}
