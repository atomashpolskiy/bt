package bt.net;

import bt.metainfo.Torrent;
import bt.metainfo.TorrentId;
import bt.protocol.Handshake;
import bt.protocol.IHandshakeFactory;
import bt.protocol.Message;
import bt.torrent.TorrentDescriptor;
import bt.torrent.TorrentRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collection;
import java.util.Optional;

/**
 * Handles handshake exchange for incoming peer connections.
 *
 * @since 1.0
 */
class IncomingHandshakeHandler implements ConnectionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(IncomingHandshakeHandler.class);

    private IHandshakeFactory handshakeFactory;
    private TorrentRegistry torrentRegistry;
    private Collection<HandshakeHandler> handshakeHandlers;
    private Duration handshakeTimeout;

    public IncomingHandshakeHandler(IHandshakeFactory handshakeFactory, TorrentRegistry torrentRegistry,
                                    Collection<HandshakeHandler> handshakeHandlers, Duration handshakeTimeout) {
        this.handshakeFactory = handshakeFactory;
        this.torrentRegistry = torrentRegistry;
        this.handshakeHandlers = handshakeHandlers;
        this.handshakeTimeout = handshakeTimeout;
    }

    @Override
    public boolean handleConnection(PeerConnection connection) {

        Message firstMessage = connection.readMessage(handshakeTimeout.toMillis());
        if (firstMessage != null) {
            if (Handshake.class.equals(firstMessage.getClass())) {

                Handshake peerHandshake = (Handshake) firstMessage;
                Optional<Torrent> torrentOptional = torrentRegistry.getTorrent(peerHandshake.getTorrentId());
                if (torrentOptional.isPresent()) {
                    Torrent torrent = torrentOptional.get();
                    Optional<TorrentDescriptor> descriptorOptional = torrentRegistry.getDescriptor(torrent);
                    if (descriptorOptional.isPresent() && descriptorOptional.get().isActive()) {
                        TorrentId torrentId = torrent.getTorrentId();

                        Handshake handshake = handshakeFactory.createHandshake(torrent.getTorrentId());
                        handshakeHandlers.forEach(handler ->
                                handler.processOutgoingHandshake(handshake));

                        connection.postMessage(handshake);
                        ((DefaultPeerConnection) connection).setTorrentId(torrentId);

                        handshakeHandlers.forEach(handler ->
                                handler.processIncomingHandshake(new WriteOnlyPeerConnection(connection), peerHandshake));

                        return true;
                    }
                }
            } else {
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Received message of unexpected type " + firstMessage.getClass().getSimpleName() +
                            " as handshake; remote peer: " + connection.getRemotePeer());
                }
            }
        }
        return false;
    }
}
