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

package bt.torrent.compiler;

import bt.protocol.Message;
import bt.torrent.annotation.Consumes;
import bt.torrent.annotation.Produces;
import bt.torrent.messaging.MessageContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Messaging agent compiler.
 *
 * @since 1.0
 */
public class MessagingAgentCompiler {

    private static final Logger LOGGER = LoggerFactory.getLogger(MessagingAgentCompiler.class);

    private static final String CONSUMERS_KEY = "consumers";
    private static final String PRODUCERS_KEY = "producers";

    private Map<Class<?>, Map<String, Collection<?>>> compiledTypes;

    /**
     * @since 1.0
     */
    public MessagingAgentCompiler() {
        this.compiledTypes = new HashMap<>();
    }

    /**
     * Parse an arbitrary object.
     *
     * @param object Some object, that has methods, annotated with {@link Consumes} or {@link Produces}
     * @param visitor Provides callbacks for the compiler to invoke
     *                upon finding a consumer or producer method.
     * @since 1.0
     */
    public void compileAndVisit(Object object, CompilerVisitor visitor) {

        Class<?> objectType = object.getClass();
        Map<String, Collection<?>> compiledType = compiledTypes.get(objectType);
        if (compiledType == null) {
            compiledType = compileType(objectType);
            compiledTypes.put(objectType, compiledType);
        }

        compiledType.get(CONSUMERS_KEY).forEach(o -> {
            ConsumerInfo consumerInfo = (ConsumerInfo) o;
            visitor.visitConsumer(consumerInfo.getConsumedMessageType(), consumerInfo.getHandle());
        });

        compiledType.get(PRODUCERS_KEY).forEach(o -> {
            ProducerInfo producerInfo = (ProducerInfo) o;
            visitor.visitProducer(producerInfo.getHandle());
        });
    }

    private Map<String, Collection<?>> compileType(Class<?> type) {

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Compiling messaging agent type: " + type.getName());
        }

        Collection<ConsumerInfo> compiledConsumers = new ArrayList<>();
        Collection<ProducerInfo> compiledProducers = new ArrayList<>();

        Map<String, Collection<?>> compiledType = new HashMap<String, Collection<?>>() {{
            put(CONSUMERS_KEY, compiledConsumers);
            put(PRODUCERS_KEY, compiledProducers);
        }};

        int methodCount = compileType(type, compiledConsumers, compiledProducers);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Compiled " + methodCount + " consumer/producer methods");
        }
        return compiledType;
    }

    /**
     * @return Total number of consumer/producer methods compiled
     */
    private int compileType(Class<?> type, Collection<ConsumerInfo> consumersAcc, Collection<ProducerInfo> producerAcc) {

        int methodCount = 0;

        for (Method method : type.getDeclaredMethods()) {

            Consumes consumes = method.getAnnotation(Consumes.class);
            Produces produces = method.getAnnotation(Produces.class);

            if (consumes != null || produces != null) {

                if (!Modifier.isPublic(method.getModifiers())) {
                    throw new IllegalStateException("Method representing consumer/producer must be public: " + method.getName());
                }
                if (consumes != null && produces != null) {
                    throw new IllegalStateException("Method can not be both consumer and producer: " + method.getName());
                }

                if (consumes != null) {
                    ConsumerInfo consumerInfo = buildConsumerInfo(method);
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Compiled consumer method {consumedType=" +
                                consumerInfo.getConsumedMessageType().getName() +
                                "}: " + method.getName());
                    }
                    consumersAcc.add(consumerInfo);
                } else if (produces != null) {
                    ProducerInfo producerInfo = buildProducerInfo(method);
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Compiled producer method: " + method.getName());
                    }
                    producerAcc.add(producerInfo);
                }

                methodCount++;
            }
        }

        Class<?> supertype = type.getSuperclass();
        if (supertype != null && !Object.class.equals(supertype)) {
            methodCount += compileType(supertype, consumersAcc, producerAcc);
        }

        Class<?>[] interfaceTypes = type.getInterfaces();
        for (Class<?> interfaceType : interfaceTypes) {
            methodCount += compileType(interfaceType, consumersAcc, producerAcc);
        }

        return methodCount;
    }

    private static ConsumerInfo buildConsumerInfo(Method method) {

        Class<?>[] parameterTypes = method.getParameterTypes();

        if (parameterTypes.length == 0 || parameterTypes.length > 2) {
            throw new IllegalStateException("Consumer method must have at least one and at most two parameters: " +
                Message.class.getName() + " or it's subclass (required), " +
                    MessageContext.class.getName() + " (optional)");
        }
        if (!Message.class.isAssignableFrom(parameterTypes[0])) {
            throw new IllegalStateException("Consumer method must have " + Message.class.getName() +
                    " or it's subclass as the first parameter");
        }
        if (parameterTypes.length == 2) {
            if (!MessageContext.class.equals(parameterTypes[1])) {
                throw new IllegalStateException("Consumer method must have " + MessageContext.class.getName() +
                        " as the second parameter");
            }
        }

        @SuppressWarnings("unchecked")
        Class<? extends Message> consumedType = (Class<? extends Message>) parameterTypes[0];

        ConsumerInfo consumerInfo = new ConsumerInfo();
        consumerInfo.setConsumedMessageType(consumedType);

        MethodHandle handle;
        try {
            handle = MethodHandles.publicLookup().unreflect(method);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to create method handle: " + method.getName(), e);
        }
        consumerInfo.setHandle(handle);

        return consumerInfo;
    }

    private ProducerInfo buildProducerInfo(Method method) {

        Class<?>[] parameterTypes = method.getParameterTypes();

        if (parameterTypes.length == 0 || parameterTypes.length > 2) {
            throw new IllegalStateException("Producer method must have at least one and at most two parameters: " +
                Consumer.class.getName() + "<" + Message.class.getName() +  "> (required), " +
                    MessageContext.class.getName() + " (optional)");
        }
        Optional<Type> argumentType = unwrapSingleTypeParameter(method.getGenericParameterTypes()[0]);
        if (!Consumer.class.isAssignableFrom(parameterTypes[0])
                || !argumentType.isPresent() || !Message.class.equals(argumentType.get())) {
            throw new IllegalStateException("Producer method must have " + Consumer.class.getName() +
                    "<" + Message.class.getName() + "> as the first parameter");
        }
        if (parameterTypes.length == 2) {
            if (!MessageContext.class.equals(parameterTypes[1])) {
                throw new IllegalStateException("Producer method must have " + MessageContext.class.getName() +
                        " as the second parameter");
            }
        }

        MethodHandle handle;
        try {
            handle = MethodHandles.publicLookup().unreflect(method);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to create method handle: " + method.getName(), e);
        }

        ProducerInfo producerInfo = new ProducerInfo();
        producerInfo.setHandle(handle);
        return producerInfo;
    }

    private static Optional<Type> unwrapSingleTypeParameter(Type type) {
        if (type instanceof ParameterizedType) {
            Type argumentType = ((ParameterizedType) type).getActualTypeArguments()[0];
            return Optional.of(argumentType);
        }
        return Optional.empty();
    }
}
