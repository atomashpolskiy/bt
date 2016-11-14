package bt.torrent.messaging;

import bt.net.Peer;
import bt.protocol.Message;
import bt.torrent.compiler.CompilerVisitor;
import bt.torrent.compiler.MessagingAgentCompiler;

import java.lang.invoke.MethodHandle;
import java.util.HashSet;
import java.util.Set;

public class PeerWorkerFactory implements IPeerWorkerFactory {

    private Set<MessageConsumer<?>> consumers;
    private Set<MessageProducer> producers;

    public PeerWorkerFactory(Set<Object> messagingAgents) {

        MessagingAgentCompiler compiler = new MessagingAgentCompiler();

        Set<MessageConsumer<?>> consumers = new HashSet<>();
        Set<MessageProducer> producers = new HashSet<>();

        messagingAgents.forEach(agent -> compiler.compileAndVisit(agent, new CompilerVisitor() {
            @Override
            public <T extends Message> void visitConsumer(Class<T> consumedType, MethodHandle handle) {
                // full handle sig is (obj, message, context):void
                boolean reduced = handle.type().parameterCount() == 2;
                consumers.add(new MessageConsumer<T>() {
                    @Override
                    public Class<T> getConsumedType() {
                        return consumedType;
                    }

                    @Override
                    public void consume(T message, MessageContext context) {
                        try {
                            if (reduced) {
                                handle.invoke(agent, message);
                            } else {
                                handle.invoke(agent, message, context);
                            }
                        } catch (Throwable t) {
                            throw new RuntimeException("Failed to invoke message consumer", t);
                        }
                    }
                });
            }

            @Override
            public void visitProducer(MethodHandle handle) {
                // full handle sig is (obj, consumer, context):void
                boolean reduced = handle.type().parameterCount() == 2;
                producers.add((messageConsumer, context) -> {
                    try {
                        if (reduced) {
                                handle.invoke(agent, messageConsumer);
                            } else {
                                handle.invoke(agent, messageConsumer, context);
                            }
                    } catch (Throwable t) {
                        throw new RuntimeException("Failed to invoke message producer", t);
                    }
                });
            }
        }));

        this.consumers = consumers;
        this.producers = producers;
    }

    @Override
    public IPeerWorker createPeerWorker(Peer peer) {
        return new PeerWorker(peer, consumers, producers);
    }
}
