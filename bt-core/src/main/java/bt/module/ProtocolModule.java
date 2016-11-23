package bt.module;

import bt.net.BitfieldConnectionHandler;
import bt.net.ConnectionHandler;
import bt.net.HandshakeHandler;
import bt.net.extended.ExtendedConnectionHandler;
import bt.net.extended.ExtendedHandshakeHandler;
import bt.protocol.Message;
import bt.protocol.StandardBittorrentProtocol;
import bt.protocol.extended.AlphaSortedMapping;
import bt.protocol.extended.ExtendedHandshake;
import bt.protocol.extended.ExtendedHandshakeProvider;
import bt.protocol.extended.ExtendedMessage;
import bt.protocol.extended.ExtendedMessageTypeMapping;
import bt.protocol.extended.ExtendedProtocol;
import bt.protocol.handler.MessageHandler;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;

import java.util.Map;

/**
 * This module contributes main protocol-related artifacts.
 *
 * @since 1.0
 */
public class ProtocolModule implements Module {

    /**
     * Contribute a message handler for some extended message type.
     * See BEP-10 for details on protocol extensions.
     * Binding key is a unique message type ID, that will be communicated to a peer
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

    @Override
    public void configure(Binder binder) {

        ProtocolModule.contributeExtendedMessageHandler(binder);

        binder.bind(new TypeLiteral<MessageHandler<Message>>(){}).annotatedWith(BitTorrentProtocol.class)
                .to(StandardBittorrentProtocol.class).in(Singleton.class);

        MapBinder<Integer, MessageHandler<?>> extraMessageHandlerMapBinder =
                    MapBinder.newMapBinder(binder, new TypeLiteral<Integer>(){},
                            new TypeLiteral<MessageHandler<?>>(){}, MessageHandlers.class);
        extraMessageHandlerMapBinder.addBinding(ExtendedProtocol.EXTENDED_MESSAGE_ID).to(ExtendedProtocol.class);

        binder.bind(ExtendedHandshake.class).toProvider(ExtendedHandshakeProvider.class).in(Singleton.class);

        Multibinder<ConnectionHandler> extraConnectionHandlers = Multibinder.newSetBinder(binder, ConnectionHandler.class);
        extraConnectionHandlers.addBinding().to(BitfieldConnectionHandler.class);
        extraConnectionHandlers.addBinding().to(ExtendedConnectionHandler.class);

        Multibinder<HandshakeHandler> extraHandshakeHandlers = Multibinder.newSetBinder(binder, HandshakeHandler.class);
        extraHandshakeHandlers.addBinding().to(ExtendedHandshakeHandler.class);
    }

    @Provides
    @Singleton
    public ExtendedMessageTypeMapping provideExtendedMessageTypeMapping(
            @ExtendedMessageHandlers Map<String, MessageHandler<? extends ExtendedMessage>> handlersByTypeName) {
        return new AlphaSortedMapping(handlersByTypeName);
    }
}
