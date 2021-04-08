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

    private final MessagingAgentCompiler compiler;

    private volatile List<MessageConsumer<Message>> genericConsumers;
    private volatile Map<Class<?>, Collection<MessageConsumer<?>>> typedConsumers;
    private volatile List<MessageProducer> producers;

    public DefaultMessageRouter() {
        this(Collections.emptyList());
    }

    public DefaultMessageRouter(Collection<Object> messagingAgents) {
        this.compiler = new MessagingAgentCompiler();

        this.genericConsumers = new ArrayList<>();
        this.typedConsumers = new HashMap<>();
        this.producers = new ArrayList<>();

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
        if (messageConsumers.isEmpty()) {
            return;
        }

        synchronized (this) {
            List<MessageConsumer<Message>> newGenericConsumers = new ArrayList<>(this.genericConsumers);
            Map<Class<?>, Collection<MessageConsumer<?>>> newTypedConsumers = new HashMap<>(this.typedConsumers);

            messageConsumers.forEach(consumer -> {
                Class<?> consumedType = consumer.getConsumedType();
                if (Message.class.equals(consumedType)) {
                    newGenericConsumers.add((MessageConsumer<Message>) consumer);
                } else {
                    newTypedConsumers.computeIfAbsent(consumedType, k -> new ArrayList<>()).add(consumer);
                }
            });

            this.genericConsumers = newGenericConsumers;
            this.typedConsumers = newTypedConsumers;
        }
    }

    private void addProducers(Collection<MessageProducer> producers) {
        if (producers.isEmpty()) {
            return;
        }

        synchronized (this) {
            List<MessageProducer> newProducers = new ArrayList<>(this.producers);
            newProducers.addAll(producers);
            this.producers = newProducers;
        }
    }

    @Override
    public void consume(Message message, MessageContext context) {
        doConsume(message, context);
    }

    private <T extends Message> void doConsume(T message, MessageContext context) {
        genericConsumers.forEach(consumer -> consumer.consume(message, context));

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
        producers.forEach(producer -> producer.produce(messageConsumer, context));
    }

    private static class CollectingCompilerVisitor implements CompilerVisitor {

        private final Object agent;
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
