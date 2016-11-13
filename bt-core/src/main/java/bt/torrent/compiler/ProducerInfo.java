package bt.torrent.compiler;

import java.lang.invoke.MethodHandle;

class ProducerInfo {

    private MethodHandle handle;

    public MethodHandle getHandle() {
        return handle;
    }

    public void setHandle(MethodHandle handle) {
        this.handle = handle;
    }
}
