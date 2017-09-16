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

package bt.module;

import bt.net.HandshakeHandler;
import bt.protocol.Message;
import bt.protocol.extended.ExtendedMessage;
import bt.protocol.handler.MessageHandler;
import com.google.inject.Binder;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;

import java.util.Objects;

/**
 * Used for contributing custom extensions to {@link ProtocolModule}
 *
 * @since 1.5
 */
public class ProtocolModuleExtender {

    private final Binder binder;
    private MapBinder<Integer, MessageHandler<? extends Message>> messageHandlers;
    private MapBinder<String, MessageHandler<? extends ExtendedMessage>> extendedMessageHandlers;
    private Multibinder<HandshakeHandler> handshakeHandlers;

    ProtocolModuleExtender(Binder binder) {
        this.binder = binder;
    }

    ProtocolModuleExtender initAllExtensions() {
        contributeMessageHandlers();
        contributeExtendedMessageHandlers();
        contributeHandshakeHandlers();

        return this;
    }

    /**
     * Contribute a message handler type, that will process BEP-3 messages with the given ID.
     *
     * @since 1.5
     */
    public <M extends Message> ProtocolModuleExtender addMessageHandler(int messageType,
                                                                        Class<? extends MessageHandler<M>> handlerType) {
        Objects.requireNonNull(handlerType);

        contributeMessageHandlers().addBinding(messageType).to(handlerType).in(Singleton.class);
        return this;
    }

    /**
     * Contribute a message handler instance, that will process BEP-3 messages with the given ID.
     *
     * @since 1.5
     */
    public <M extends Message> ProtocolModuleExtender addMessageHandler(int messageType,
                                                                        MessageHandler<M> handler) {
        Objects.requireNonNull(handler);

        contributeMessageHandlers().addBinding(messageType).toInstance(handler);
        return this;
    }

    /**
     * Contribute a message handler type, that will process BEP-10 messages of the given type.
     *
     * @since 1.5
     */
    public <M extends ExtendedMessage> ProtocolModuleExtender addExtendedMessageHandler(String messageType,
                                                                                        Class<? extends MessageHandler<M>> handlerType) {
        Objects.requireNonNull(messageType);
        Objects.requireNonNull(handlerType);

        contributeExtendedMessageHandlers().addBinding(messageType).to(handlerType).in(Singleton.class);
        return this;
    }

    /**
     * Contribute a message handler instance, that will process BEP-10 messages of the given type.
     *
     * @since 1.5
     */
    public <M extends ExtendedMessage> ProtocolModuleExtender addExtendedMessageHandler(String messageType,
                                                                                        MessageHandler<M> handler) {
        Objects.requireNonNull(messageType);
        Objects.requireNonNull(handler);

        contributeExtendedMessageHandlers().addBinding(messageType).toInstance(handler);
        return this;
    }

    /**
     * Contribute a handshake handler type.
     *
     * @since 1.5
     */
    public ProtocolModuleExtender addHandshakeHandler(Class<? extends HandshakeHandler> handlerType) {
        Objects.requireNonNull(handlerType);

        contributeHandshakeHandlers().addBinding().to(handlerType).in(Singleton.class);
        return this;
    }

    /**
     * Contribute a handshake handler instance.
     *
     * @since 1.5
     */
    public ProtocolModuleExtender addHandshakeHandler(HandshakeHandler handler) {
        Objects.requireNonNull(handler);

        contributeHandshakeHandlers().addBinding().toInstance(handler);
        return this;
    }

    private MapBinder<Integer, MessageHandler<? extends Message>> contributeMessageHandlers() {
        if (messageHandlers == null) {
            messageHandlers = MapBinder.newMapBinder(
                    binder,
                    new TypeLiteral<Integer>() {
                    },
                    new TypeLiteral<MessageHandler<?>>() {
                    },
                    MessageHandlers.class);
        }
        return messageHandlers;
    }

    private MapBinder<String, MessageHandler<? extends ExtendedMessage>> contributeExtendedMessageHandlers() {
        if (extendedMessageHandlers == null) {
            extendedMessageHandlers = MapBinder.newMapBinder(
                    binder,
                    new TypeLiteral<String>() {
                    },
                    new TypeLiteral<MessageHandler<? extends ExtendedMessage>>() {
                    },
                    ExtendedMessageHandlers.class);
        }
        return extendedMessageHandlers;
    }

    private Multibinder<HandshakeHandler> contributeHandshakeHandlers() {
        if (handshakeHandlers == null) {
            handshakeHandlers = Multibinder.newSetBinder(binder, HandshakeHandler.class);
        }
        return handshakeHandlers;
    }
}
