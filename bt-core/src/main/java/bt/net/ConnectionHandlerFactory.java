package bt.net;

import bt.metainfo.TorrentId;
import bt.protocol.IHandshakeFactory;
import bt.torrent.TorrentRegistry;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 *<p><b>Note that this class implements a service.
 * Hence, is not a part of the public API and is a subject to change.</b></p>
 */
public class ConnectionHandlerFactory implements IConnectionHandlerFactory {

    private IHandshakeFactory handshakeFactory;
    private ConnectionHandler incomingHandler;
    private Duration peerHandshakeTimeout;

    private Collection<HandshakeHandler> handshakeHandlers;

    private Map<TorrentId, ConnectionHandler> outgoingHandlers;

    public ConnectionHandlerFactory(IHandshakeFactory handshakeFactory,
                                    TorrentRegistry torrentRegistry,
                                    Collection<HandshakeHandler> handshakeHandlers,
                                    Duration peerHandshakeTimeout) {
        this.handshakeFactory = handshakeFactory;
        this.incomingHandler = new IncomingHandshakeHandler(handshakeFactory, torrentRegistry,
                handshakeHandlers, peerHandshakeTimeout);

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
        Objects.requireNonNull(torrentId, "Missing torrent ID");
        ConnectionHandler outgoing = outgoingHandlers.get(torrentId);
        if (outgoing == null) {
            outgoing = new OutgoingHandshakeHandler(handshakeFactory, torrentId,
                    handshakeHandlers, peerHandshakeTimeout.toMillis());
            ConnectionHandler existing = outgoingHandlers.putIfAbsent(torrentId, outgoing);
            if (existing != null) {
                outgoing = existing;
            }
        }
        return outgoing;
    }
}
