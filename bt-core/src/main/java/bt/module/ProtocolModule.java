package bt.module;

import bt.net.ConnectionHandler;
import bt.net.HandshakeHandler;
import bt.protocol.Message;
import bt.protocol.StandardBittorrentProtocol;
import bt.protocol.handler.MessageHandler;
import bt.runtime.net.ext.ExtendedConnectionHandler;
import bt.runtime.net.ext.ExtendedHandshakeHandler;
import bt.runtime.protocol.ext.AlphaSortedMessageTypeMapping;
import bt.runtime.protocol.ext.ExtendedHandshake;
import bt.runtime.protocol.ext.ExtendedHandshakeProvider;
import bt.runtime.protocol.ext.ExtendedMessage;
import bt.runtime.protocol.ext.ExtendedMessageTypeMapping;
import bt.runtime.protocol.ext.ExtendedProtocol;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ProtocolModule implements Module {

    private Map<String, MessageHandler<? extends ExtendedMessage>> extendedMessageHandlers;

    public void addExtendedMessageHandler(String messageTypeName,
                                          MessageHandler<? extends ExtendedMessage> handler) {

        Objects.requireNonNull(messageTypeName);
        Objects.requireNonNull(handler);

        if (extendedMessageHandlers == null) {
            extendedMessageHandlers = new HashMap<>();
        }
        extendedMessageHandlers.put(messageTypeName, handler);
    }

    @Override
    public void configure(Binder binder) {

        binder.bind(new TypeLiteral<MessageHandler<Message>>(){})
                .to(StandardBittorrentProtocol.class).in(Singleton.class);

        MapBinder<Integer, MessageHandler<?>> extraMessageHandlerMapBinder =
                    MapBinder.newMapBinder(binder, new TypeLiteral<Integer>(){}, new TypeLiteral<MessageHandler<?>>(){});

        MapBinder<String, MessageHandler<? extends ExtendedMessage>> extendedMessageHandlerMapBinder =
                    MapBinder.newMapBinder(binder, new TypeLiteral<String>(){},
                            new TypeLiteral<MessageHandler<? extends ExtendedMessage>>(){});

        Multibinder<ConnectionHandler> extraConnectionHandlers = Multibinder.newSetBinder(binder, ConnectionHandler.class);
        Multibinder<HandshakeHandler> extraHandshakeHandlers = Multibinder.newSetBinder(binder, HandshakeHandler.class);

        if (extendedMessageHandlers != null && extendedMessageHandlers.size() > 0) {

            binder.bind(ExtendedMessageTypeMapping.class).to(AlphaSortedMessageTypeMapping.class).in(Singleton.class);
            extraMessageHandlerMapBinder.addBinding(ExtendedProtocol.EXTENDED_MESSAGE_ID).to(ExtendedProtocol.class);

            binder.bind(ExtendedHandshake.class).toProvider(ExtendedHandshakeProvider.class).in(Singleton.class);

            extendedMessageHandlers.forEach((key, value) ->
                    extendedMessageHandlerMapBinder.addBinding(key).toInstance(value));

            extraConnectionHandlers.addBinding().to(ExtendedConnectionHandler.class);
            extraHandshakeHandlers.addBinding().to(ExtendedHandshakeHandler.class);
        }
    }
}
