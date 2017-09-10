package bt.net;

import bt.metainfo.TorrentId;
import bt.protocol.Handshake;
import bt.protocol.IHandshakeFactory;
import bt.protocol.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;

/**
 * Handles handshake exchange for outgoing peer connections.
 *
 * @since 1.0
 */
class OutgoingHandshakeHandler implements ConnectionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutgoingHandshakeHandler.class);

    private IHandshakeFactory handshakeFactory;
    private TorrentId torrentId;
    private Collection<HandshakeHandler> handshakeHandlers;
    private long handshakeTimeout;

    public OutgoingHandshakeHandler(IHandshakeFactory handshakeFactory, TorrentId torrentId,
                                    Collection<HandshakeHandler> handshakeHandlers, long handshakeTimeout) {
        this.handshakeFactory = handshakeFactory;
        this.torrentId = torrentId;
        this.handshakeHandlers = handshakeHandlers;
        this.handshakeTimeout = handshakeTimeout;
    }

    @Override
    public boolean handleConnection(PeerConnection connection) {
        Peer peer = connection.getRemotePeer();

        Handshake handshake = handshakeFactory.createHandshake(torrentId);
        handshakeHandlers.forEach(handler ->
                            handler.processOutgoingHandshake(handshake));
        try {
            connection.postMessage(handshake);
        } catch (IOException e) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Failed to send handshake to peer: {}. Reason: {} ({})",
                        peer, e.getClass().getName(), e.getMessage());
            }
            return false;
        }

        Message firstMessage = null;
        try {
            firstMessage = connection.readMessage(handshakeTimeout);
        } catch (IOException e) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Failed to receive handshake from peer: {}. Reason: {} ({})",
                        peer, e.getClass().getName(), e.getMessage());
            }
        }
        if (firstMessage != null) {
            if (Handshake.class.equals(firstMessage.getClass())) {
                Handshake peerHandshake = (Handshake) firstMessage;
                TorrentId incomingTorrentId = peerHandshake.getTorrentId();
                if (torrentId.equals(incomingTorrentId)) {
                    connection.setTorrentId(torrentId);

                    handshakeHandlers.forEach(handler ->
                            handler.processIncomingHandshake(new WriteOnlyPeerConnection(connection), peerHandshake));

                    return true;
                }
            } else {
                LOGGER.warn("Received message of unexpected type '{}' instead of handshake from peer: {}",
                        firstMessage.getClass(), peer);
            }
        }
        return false;
    }
}
