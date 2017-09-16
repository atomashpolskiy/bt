/*
 * Copyright (c) 2016—2017 Andrei Tomashpolskiy and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

public class DefaultMessageRouter implements MessageRouter {

    private MessagingAgentCompiler compiler;

    private List<MessageConsumer<Message>> genericConsumers;
    private Map<Class<?>, Collection<MessageConsumer<?>>> typedConsumers;
    private List<MessageProducer> producers;

    // collection of added consumers/producers in the form of runnable "commands"..
    // quick and dirty!
    private List<Runnable> changes;
    private final Object changesLock;

    public DefaultMessageRouter() {
        this(Collections.emptyList());
    }

    public DefaultMessageRouter(Collection<Object> messagingAgents) {
        this.compiler = new MessagingAgentCompiler();

        this.genericConsumers = new ArrayList<>();
        this.typedConsumers = new HashMap<>();
        this.producers = new ArrayList<>();

        this.changes = new ArrayList<>();
        this.changesLock = new Object();

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

        synchronized (changesLock) {
            this.changes.add(() -> {
                this.genericConsumers.addAll(genericConsumers);
                this.typedConsumers.putAll(typedMessageConsumers);
            });
        }
    }

    private void addProducers(Collection<MessageProducer> producers) {
        synchronized (changesLock) {
            this.changes.add(() -> {
                this.producers.addAll(producers);
            });
        }
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
        synchronized (changesLock) {
            if (!changes.isEmpty()) {
                changes.forEach(Runnable::run);
                changes.clear();
            }
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
