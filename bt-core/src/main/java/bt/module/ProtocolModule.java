package bt.module;

import bt.magnet.UtMetadataMessageHandler;
import bt.net.BitfieldConnectionHandler;
import bt.net.ConnectionHandlerFactory;
import bt.net.HandshakeHandler;
import bt.net.IConnectionHandlerFactory;
import bt.net.extended.ExtendedProtocolHandshakeHandler;
import bt.protocol.HandshakeFactory;
import bt.protocol.IHandshakeFactory;
import bt.protocol.Message;
import bt.protocol.StandardBittorrentProtocol;
import bt.protocol.extended.AlphaSortedMapping;
import bt.protocol.extended.ExtendedHandshake;
import bt.protocol.extended.ExtendedHandshakeProvider;
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
     * Contribute a message handler for some message type.
     * <p>Binding key is a unique message type ID, that will be used
     * to encode and decode the binary representation of a message of this type.
     *
     * @since 1.0
     */
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
     */
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
     */
    public static Multibinder<HandshakeHandler> contributeHandshakeHandler(Binder binder) {
        return Multibinder.newSetBinder(binder, HandshakeHandler.class);
    }

    @Override
    public void configure(Binder binder) {

        // trigger creation of extension points
        ProtocolModule.contributeMessageHandler(binder);
        ProtocolModule.contributeExtendedMessageHandler(binder);
        ProtocolModule.contributeHandshakeHandler(binder);

        binder.bind(IHandshakeFactory.class).to(HandshakeFactory.class).in(Singleton.class);

        binder.bind(new TypeLiteral<MessageHandler<Message>>(){}).annotatedWith(BitTorrentProtocol.class)
                .to(StandardBittorrentProtocol.class).in(Singleton.class);

        ProtocolModule.contributeMessageHandler(binder)
                .addBinding(ExtendedProtocol.EXTENDED_MESSAGE_ID).to(ExtendedProtocol.class);

        binder.bind(ExtendedHandshake.class).toProvider(ExtendedHandshakeProvider.class).in(Singleton.class);

        ProtocolModule.contributeExtendedMessageHandler(binder)
                .addBinding("ut_metadata").to(UtMetadataMessageHandler.class);
    }

    @Provides
    @Singleton
    public IConnectionHandlerFactory provideConnectionHandlerFactory(IHandshakeFactory handshakeFactory,
                                                                     TorrentRegistry torrentRegistry,
                                                                     Set<HandshakeHandler> boundHandshakeHandlers,
                                                                     ExtendedHandshakeProvider extendedHandshakeProvider,
                                                                     Config config) {
        List<HandshakeHandler> handshakeHandlers = new ArrayList<>(boundHandshakeHandlers);
        // add default handshake handlers to the beginning of the connection handling chain
        handshakeHandlers.add(new BitfieldConnectionHandler(torrentRegistry));
        handshakeHandlers.add(new ExtendedProtocolHandshakeHandler(extendedHandshakeProvider));

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
