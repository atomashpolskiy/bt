package bt.torrent.messaging;

import bt.protocol.Message;
import bt.torrent.compiler.CompilerVisitor;
import bt.torrent.compiler.MessagingAgentCompiler;

import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

class DefaultMessageRouter implements MessageRouter {

    private MessagingAgentCompiler compiler;

    private List<MessageConsumer<Message>> genericConsumers;
    private Map<Class<?>, Collection<MessageConsumer<?>>> typedConsumers;
    private List<MessageProducer> producers;

    // collection of added consumers/producers in the form of runnable "commands"..
    // quick and dirty!
    private List<Runnable> changes;

    DefaultMessageRouter() {
        this(Collections.emptyList());
    }

    DefaultMessageRouter(Collection<Object> messagingAgents) {
        this.compiler = new MessagingAgentCompiler();

        this.genericConsumers = new ArrayList<>();
        this.typedConsumers = new HashMap<>();
        this.producers = new ArrayList<>();

        this.changes = new ArrayList<>();

        messagingAgents.forEach(this::registerMessagingAgent);
    }

    @Override
    public final void registerMessagingAgent(Object agent) {
        CollectingCompilerVisitor visitor = new CollectingCompilerVisitor(agent);
        compiler.compileAndVisit(agent, visitor);
        addConsumers(visitor.getConsumers());
        addProducers(visitor.getProducers());
    }

    @Override
    public void unregisterMessagingAgent(Object agent) {
        // TODO
    }

    @SuppressWarnings("unchecked")
    private void addConsumers(List<MessageConsumer<?>> messageConsumers) {

        List<MessageConsumer<Message>> genericConsumers = new ArrayList<>();
        Map<Class<?>, Collection<MessageConsumer<?>>> typedMessageConsumers = new HashMap<>();

        messageConsumers.forEach(consumer -> {
            Class<?> consumedType = consumer.getConsumedType();
            if (Message.class.equals(consumedType)) {
                genericConsumers.add((MessageConsumer<Message>) consumer);
            } else {
                Collection<MessageConsumer<?>> consumers = typedMessageConsumers.get(consumedType);
                if (consumers == null) {
                    consumers = new ArrayList<>();
                    typedMessageConsumers.put(consumedType, consumers);
                }
                consumers.add(consumer);
            }
        });

        this.changes.add(() -> {
            this.genericConsumers.addAll(genericConsumers);
            this.typedConsumers.putAll(typedMessageConsumers);
        });
    }

    private void addProducers(Collection<MessageProducer> producers) {
        this.changes.add(() -> {
            this.producers.addAll(producers);
        });
    }

    @Override
    public void consume(Message message, MessageContext context) {
        mergeChanges();
        doConsume(message, context);
    }

    private <T extends Message> void doConsume(T message, MessageContext context) {
        genericConsumers.forEach(consumer -> {
            consumer.consume(message, context);
        });

        Collection<MessageConsumer<?>> consumers = typedConsumers.get(message.getClass());
        if (consumers != null) {
            consumers.forEach(consumer -> {
                @SuppressWarnings("unchecked")
                MessageConsumer<T> typedConsumer = (MessageConsumer<T>) consumer;
                typedConsumer.consume(message, context);
            });
        }
    }

    @Override
    public void produce(Consumer<Message> messageConsumer, MessageContext context) {
        mergeChanges();
        producers.forEach(producer -> {
            producer.produce(messageConsumer, context);
        });
    }

    private void mergeChanges() {
        if (!changes.isEmpty()) {
            changes.forEach(Runnable::run);
            changes.clear();
        }
    }

    private static class CollectingCompilerVisitor implements CompilerVisitor {

        private Object agent;
        private final List<MessageConsumer<?>> consumers;
        private final List<MessageProducer> producers;

        public CollectingCompilerVisitor(Object agent) {
            this.agent = agent;
            this.consumers = new ArrayList<>();
            this.producers = new ArrayList<>();
        }

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

        public List<MessageProducer> getProducers() {
            return producers;
        }

        public List<MessageConsumer<?>> getConsumers() {
            return consumers;
        }
    }
}
