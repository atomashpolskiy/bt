package bt.net;

import bt.metainfo.TorrentId;
import bt.protocol.Handshake;
import bt.protocol.IHandshakeFactory;
import bt.protocol.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Handles handshake exchange for outgoing peer connections.
 *
 * @since 1.0
 */
class OutgoingHandshakeHandler implements ConnectionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutgoingHandshakeHandler.class);

    private IHandshakeFactory handshakeFactory;
    private TorrentId torrentId;
    private Set<HandshakeHandler> handshakeHandlers;
    private long handshakeTimeout;

    public OutgoingHandshakeHandler(IHandshakeFactory handshakeFactory, TorrentId torrentId,
                                    Set<HandshakeHandler> handshakeHandlers, long handshakeTimeout) {
        this.handshakeFactory = handshakeFactory;
        this.torrentId = torrentId;
        this.handshakeHandlers = handshakeHandlers;
        this.handshakeTimeout = handshakeTimeout;
    }

    @Override
    public boolean handleConnection(PeerConnection connection) {

        Handshake handshake = handshakeFactory.createHandshake(torrentId);
        handshakeHandlers.forEach(handler ->
                            handler.processOutgoingHandshake(handshake));
        connection.postMessage(handshake);

        Message firstMessage = connection.readMessage(handshakeTimeout);
        if (firstMessage != null) {
            if (Handshake.class.equals(firstMessage.getClass())) {
                Handshake peerHandshake = (Handshake) firstMessage;
                TorrentId incomingTorrentId = peerHandshake.getTorrentId();
                if (torrentId.equals(incomingTorrentId)) {
                    ((DefaultPeerConnection) connection).setTorrentId(torrentId);

                    handshakeHandlers.forEach(handler ->
                            handler.processIncomingHandshake(connection.getRemotePeer(), peerHandshake));

                    return true;
                }
            } else {
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Received message of unexpected type " + firstMessage.getClass().getSimpleName() +
                            " in response to handshake; remote peer: " + connection.getRemotePeer());
                }
            }
        }
        return false;
    }
}
