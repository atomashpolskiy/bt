package bt.net;

import bt.metainfo.TorrentId;
import bt.protocol.IHandshakeFactory;
import bt.service.IConfigurationService;
import bt.service.ITorrentRegistry;
import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ConnectionHandlerFactory implements IConnectionHandlerFactory {

    private IHandshakeFactory handshakeFactory;
    private ConnectionHandler incomingHandler;
    private IConfigurationService configurationService;

    private List<ConnectionHandler> connectionHandlers;
    private Set<HandshakeHandler> handshakeHandlers;

    private Map<TorrentId, ConnectionHandler> outgoingHandlers;

    @Inject
    public ConnectionHandlerFactory(IHandshakeFactory handshakeFactory, ITorrentRegistry torrentRegistry,
                                    Set<ConnectionHandler> connectionHandlers, Set<HandshakeHandler> handshakeHandlers,
                                    IConfigurationService configurationService) {
        this.handshakeFactory = handshakeFactory;
        this.configurationService = configurationService;

        this.connectionHandlers = new ArrayList<>();
        this.connectionHandlers.addAll(connectionHandlers);

        incomingHandler = new ConnectionSequence(
                new IncomingHandshakeHandler(handshakeFactory, torrentRegistry, handshakeHandlers, configurationService),
                this.connectionHandlers);

        outgoingHandlers = new ConcurrentHashMap<>();
        this.handshakeHandlers = handshakeHandlers;
    }

    @Override
    public ConnectionHandler getIncomingHandler() {
        return incomingHandler;
    }

    @Override
    public ConnectionHandler getOutgoingHandler(TorrentId torrentId) {

        ConnectionHandler outgoing = outgoingHandlers.get(torrentId);
        if (outgoing == null) {
            outgoing = new ConnectionSequence(
                    new OutgoingHandshakeHandler(handshakeFactory, torrentId, handshakeHandlers,
                            configurationService.getHandshakeTimeOut()),
                    connectionHandlers);

            ConnectionHandler existing = outgoingHandlers.putIfAbsent(torrentId, outgoing);
            if (existing != null) {
                outgoing = existing;
            }
        }
        return outgoing;
    }

    private static class ConnectionSequence implements ConnectionHandler {

        private ConnectionHandler firstHandler;
        private List<ConnectionHandler> otherHandlers;

        ConnectionSequence(ConnectionHandler firstHandler, List<ConnectionHandler> otherHandlers) {
            this.firstHandler = firstHandler;
            this.otherHandlers = otherHandlers;
        }

        @Override
        public boolean handleConnection(PeerConnection connection) {

            if (!firstHandler.handleConnection(connection)) {
                return false;
            }

            for (ConnectionHandler connectionHandler : otherHandlers) {
                if (!connectionHandler.handleConnection(connection)) {
                    return false;
                }
            }
            return true;
        }
    }
}
