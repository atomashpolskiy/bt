package bt.torrent.compiler;

import bt.protocol.Message;

import java.lang.invoke.MethodHandle;

public interface CompilerVisitor {

    <T extends Message> void visitConsumer(Class<T> consumedType, MethodHandle handle);

    void visitProducer(MethodHandle handle);
}
