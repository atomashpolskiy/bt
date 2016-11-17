package bt.module;

import bt.net.ConnectionHandler;
import bt.net.HandshakeHandler;
import bt.protocol.Message;
import bt.protocol.StandardBittorrentProtocol;
import bt.protocol.handler.MessageHandler;
import bt.net.extended.ExtendedConnectionHandler;
import bt.net.extended.ExtendedHandshakeHandler;
import bt.protocol.extended.AlphaSortedMapping;
import bt.protocol.extended.ExtendedHandshake;
import bt.protocol.extended.ExtendedHandshakeProvider;
import bt.protocol.extended.ExtendedMessage;
import bt.protocol.extended.ExtendedMessageTypeMapping;
import bt.protocol.extended.ExtendedProtocol;
import bt.net.BitfieldConnectionHandler;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * This module contributes main protocol-related artifacts.
 *
 * @since 1.0
 */
public class ProtocolModule implements Module {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProtocolModule.class);

    private Map<String, MessageHandler<? extends ExtendedMessage>> extendedMessageHandlers;

    /**
     * Contribute an extended message handler.
     * See BEP-10 for details on protocol extensions.
     *
     * @param messageTypeName Unique message type ID, that will be communicated to a peer
     *                        during the extended handshake procedure in the 'm' dictionary
     *                        of an extended handshake message.
     * @param handler Message handler for some extended message type
     * @since 1.0
     */
    public void addExtendedMessageHandler(String messageTypeName,
                                          MessageHandler<? extends ExtendedMessage> handler) {

        Objects.requireNonNull(messageTypeName);
        if (messageTypeName.isEmpty()) {
            throw new IllegalArgumentException("Empty message type name");
        }

        Objects.requireNonNull(handler);

        if (extendedMessageHandlers == null) {
            extendedMessageHandlers = new HashMap<>();
        } else if (extendedMessageHandlers.containsKey(messageTypeName)) {
            LOGGER.warn("Overriding handler for extended message type: " + messageTypeName);
        }
        extendedMessageHandlers.put(messageTypeName, handler);
    }

    @Override
    public void configure(Binder binder) {

        binder.bind(new TypeLiteral<MessageHandler<Message>>(){}).annotatedWith(BitTorrentProtocol.class)
                .to(StandardBittorrentProtocol.class).in(Singleton.class);

        MapBinder<Integer, MessageHandler<?>> extraMessageHandlerMapBinder =
                    MapBinder.newMapBinder(binder, new TypeLiteral<Integer>(){},
                            new TypeLiteral<MessageHandler<?>>(){}, MessageHandlers.class);

        MapBinder<String, MessageHandler<? extends ExtendedMessage>> extendedMessageHandlerMapBinder =
                    MapBinder.newMapBinder(binder, new TypeLiteral<String>(){},
                            new TypeLiteral<MessageHandler<? extends ExtendedMessage>>(){}, ExtendedMessageHandlers.class);

        Multibinder<ConnectionHandler> extraConnectionHandlers = Multibinder.newSetBinder(binder, ConnectionHandler.class);
        extraConnectionHandlers.addBinding().to(BitfieldConnectionHandler.class);

        Multibinder<HandshakeHandler> extraHandshakeHandlers = Multibinder.newSetBinder(binder, HandshakeHandler.class);

        if (extendedMessageHandlers != null && extendedMessageHandlers.size() > 0) {

            binder.bind(ExtendedMessageTypeMapping.class).to(AlphaSortedMapping.class).in(Singleton.class);
            extraMessageHandlerMapBinder.addBinding(ExtendedProtocol.EXTENDED_MESSAGE_ID).to(ExtendedProtocol.class);

            binder.bind(ExtendedHandshake.class).toProvider(ExtendedHandshakeProvider.class).in(Singleton.class);

            extendedMessageHandlers.forEach((key, value) ->
                    extendedMessageHandlerMapBinder.addBinding(key).toInstance(value));

            extraConnectionHandlers.addBinding().to(ExtendedConnectionHandler.class);
            extraHandshakeHandlers.addBinding().to(ExtendedHandshakeHandler.class);
        }
    }
}
