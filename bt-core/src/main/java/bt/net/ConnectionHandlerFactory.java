package bt.net;

import bt.metainfo.TorrentId;
import bt.protocol.IHandshakeFactory;
import bt.service.ITorrentRegistry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ConnectionHandlerFactory implements IConnectionHandlerFactory {

    private IHandshakeFactory handshakeFactory;
    private ConnectionHandler incomingHandler;
    private Duration peerHandshakeTimeout;

    private List<ConnectionHandler> connectionHandlers;
    private Set<HandshakeHandler> handshakeHandlers;

    private Map<TorrentId, ConnectionHandler> outgoingHandlers;

    public ConnectionHandlerFactory(IHandshakeFactory handshakeFactory, ITorrentRegistry torrentRegistry,
                                    Set<ConnectionHandler> connectionHandlers, Set<HandshakeHandler> handshakeHandlers,
                                    Duration peerHandshakeTimeout) {

        this.handshakeFactory = handshakeFactory;

        this.connectionHandlers = new ArrayList<>();
        this.connectionHandlers.addAll(connectionHandlers);
        this.incomingHandler = new ConnectionSequence(
                new IncomingHandshakeHandler(handshakeFactory, torrentRegistry, handshakeHandlers, peerHandshakeTimeout),
                this.connectionHandlers);

        this.outgoingHandlers = new ConcurrentHashMap<>();
        this.handshakeHandlers = handshakeHandlers;

        this.peerHandshakeTimeout = peerHandshakeTimeout;
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
                            peerHandshakeTimeout.toMillis()),
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
