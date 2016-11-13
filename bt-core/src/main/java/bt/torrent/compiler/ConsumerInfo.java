package bt.torrent.compiler;

import bt.protocol.Message;

import java.lang.invoke.MethodHandle;

class ConsumerInfo {

    private MethodHandle handle;
    private Class<? extends Message> consumedMessageType;

    public MethodHandle getHandle() {
        return handle;
    }

    public void setHandle(MethodHandle handle) {
        this.handle = handle;
    }

    public Class<? extends Message> getConsumedMessageType() {
        return consumedMessageType;
    }

    public void setConsumedMessageType(Class<? extends Message> consumedMessageType) {
        this.consumedMessageType = consumedMessageType;
    }
}
