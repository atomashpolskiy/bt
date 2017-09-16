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

import bt.magnet.UtMetadataMessageHandler;
import bt.net.BitfieldConnectionHandler;
import bt.net.ConnectionHandlerFactory;
import bt.net.HandshakeHandler;
import bt.net.IConnectionHandlerFactory;
import bt.net.extended.ExtendedProtocolHandshakeHandler;
import bt.protocol.HandshakeFactory;
import bt.protocol.IExtendedHandshakeFactory;
import bt.protocol.IHandshakeFactory;
import bt.protocol.Message;
import bt.protocol.StandardBittorrentProtocol;
import bt.protocol.extended.AlphaSortedMapping;
import bt.protocol.extended.ExtendedHandshakeFactory;
import bt.protocol.extended.ExtendedMessage;
import bt.protocol.extended.ExtendedMessageTypeMapping;
import bt.protocol.extended.ExtendedProtocol;
import bt.protocol.handler.MessageHandler;
import bt.runtime.Config;
import bt.torrent.TorrentRegistry;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This module contributes main protocol-related artifacts.
 *
 * @since 1.0
 */
public class ProtocolModule implements Module {

    /**
     * Returns the extender for contributing custom extensions to the ProtocolModule.
     * Should be invoked from the dependent Module's {@link Module#configure(Binder)} method.
     *
     * @param binder DI binder passed to the Module that invokes this method.
     * @return Extender for contributing custom extensions
     * @since 1.5
     */
    public static ProtocolModuleExtender extend(Binder binder) {
        return new ProtocolModuleExtender(binder);
    }

    /**
     * Contribute a message handler for some message type.
     * <p>Binding key is a unique message type ID, that will be used
     * to encode and decode the binary representation of a message of this type.
     *
     * @since 1.0
     * @deprecated since 1.5 in favor of {@link ProtocolModuleExtender#addMessageHandler(int, Class)} and its' overloaded versions
     */
    @Deprecated
    public static MapBinder<Integer, MessageHandler<? extends Message>> contributeMessageHandler(Binder binder) {
        return MapBinder.newMapBinder(
                binder,
                new TypeLiteral<Integer>(){},
                new TypeLiteral<MessageHandler<?>>(){},
                MessageHandlers.class);
    }

    /**
     * Contribute a message handler for some extended message type.
     * <p>See BEP-10 for details on protocol extensions.
     * <p>Binding key is a unique message type ID, that will be communicated to a peer
     * during the extended handshake procedure in the 'm' dictionary of an extended handshake message.
     *
     * @since 1.0
     * @deprecated since 1.5 in favor of {@link ProtocolModuleExtender#addExtendedMessageHandler(String, Class)}
     *             and its' overloaded versions
     */
    @Deprecated
    public static MapBinder<String, MessageHandler<? extends ExtendedMessage>> contributeExtendedMessageHandler(Binder binder) {
        return MapBinder.newMapBinder(
                binder,
                new TypeLiteral<String>(){},
                new TypeLiteral<MessageHandler<? extends ExtendedMessage>>(){},
                ExtendedMessageHandlers.class);
    }

    /**
     * Contribute a handshake handler.
     *
     * @since 1.0
     * @deprecated since 1.5 in favor of {@link ProtocolModuleExtender#addHandshakeHandler(Class)} and its' overloaded versions
     */
    @Deprecated
    public static Multibinder<HandshakeHandler> contributeHandshakeHandler(Binder binder) {
        return Multibinder.newSetBinder(binder, HandshakeHandler.class);
    }

    @Override
    public void configure(Binder binder) {

        ProtocolModule.extend(binder).initAllExtensions()
                .addMessageHandler(ExtendedProtocol.EXTENDED_MESSAGE_ID, ExtendedProtocol.class)
                .addExtendedMessageHandler("ut_metadata", UtMetadataMessageHandler.class);

        binder.bind(IHandshakeFactory.class).to(HandshakeFactory.class).in(Singleton.class);

        binder.bind(new TypeLiteral<MessageHandler<Message>>(){}).annotatedWith(BitTorrentProtocol.class)
                .to(StandardBittorrentProtocol.class).in(Singleton.class);

        binder.bind(IExtendedHandshakeFactory.class).to(ExtendedHandshakeFactory.class).in(Singleton.class);
    }

    @Provides
    @Singleton
    public IConnectionHandlerFactory provideConnectionHandlerFactory(IHandshakeFactory handshakeFactory,
                                                                     TorrentRegistry torrentRegistry,
                                                                     Set<HandshakeHandler> boundHandshakeHandlers,
                                                                     ExtendedHandshakeFactory extendedHandshakeFactory,
                                                                     Config config) {
        List<HandshakeHandler> handshakeHandlers = new ArrayList<>(boundHandshakeHandlers);
        // add default handshake handlers to the beginning of the connection handling chain
        handshakeHandlers.add(new BitfieldConnectionHandler(torrentRegistry));
        handshakeHandlers.add(new ExtendedProtocolHandshakeHandler(extendedHandshakeFactory));

        return new ConnectionHandlerFactory(handshakeFactory, torrentRegistry,
                handshakeHandlers, config.getPeerHandshakeTimeout());
    }

    @Provides
    @Singleton
    public ExtendedMessageTypeMapping provideExtendedMessageTypeMapping(
            @ExtendedMessageHandlers Map<String, MessageHandler<? extends ExtendedMessage>> handlersByTypeName) {
        return new AlphaSortedMapping(handlersByTypeName);
    }
}
