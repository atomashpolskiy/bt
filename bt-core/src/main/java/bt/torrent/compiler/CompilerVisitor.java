package bt.torrent.compiler;

import bt.protocol.Message;

import java.lang.invoke.MethodHandle;

/**
 * Instances of this class provide callbacks for the {@link MessagingAgentCompiler}.
 *
 * @since 1.0
 */
public interface CompilerVisitor {

    /**
     * Visit a message consumer method.
     *
     * @param consumedType Class, representing a message type,
     *                     that this consumer is interested in
     * @param handle Method handle. Method arity is 1 or 2.
     * @see bt.torrent.annotation.Consumes
     * @param <T> (A subtype of) Message type
     * @since 1.0
     */
    <T extends Message> void visitConsumer(Class<T> consumedType, MethodHandle handle);

    /**
     * Visit a message producer method.
     *
     * @param handle Method handle. Method arity is 1 or 2.
     * @see bt.torrent.annotation.Consumes
     * @since 1.0
     */
    void visitProducer(MethodHandle handle);
}
