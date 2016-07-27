package bt.net;

import bt.metainfo.Torrent;
import bt.metainfo.TorrentId;
import bt.protocol.Handshake;
import bt.protocol.IHandshakeFactory;
import bt.protocol.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

public class OutgoingHandshakeHandler implements ConnectionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutgoingHandshakeHandler.class);

    private IHandshakeFactory handshakeFactory;
    private Torrent torrent;
    private Set<HandshakeHandler> handshakeHandlers;
    private long handshakeTimeout;

    public OutgoingHandshakeHandler(IHandshakeFactory handshakeFactory, Torrent torrent,
                                    Set<HandshakeHandler> handshakeHandlers, long handshakeTimeout) {
        this.handshakeFactory = handshakeFactory;
        this.torrent = torrent;
        this.handshakeHandlers = handshakeHandlers;
        this.handshakeTimeout = handshakeTimeout;
    }

    @Override
    public boolean handleConnection(PeerConnection connection) {

        Handshake handshake = handshakeFactory.createHandshake(torrent);
        handshakeHandlers.forEach(handler ->
                            handler.amendOutgoingHandshake(handshake));
        connection.postMessage(handshake);

        Message firstMessage = connection.readMessage(handshakeTimeout);
        if (firstMessage != null) {
            if (Handshake.class.equals(firstMessage.getClass())) {
                Handshake peerHandshake = (Handshake) firstMessage;
                TorrentId incomingTorrentId = peerHandshake.getTorrentId();
                if (torrent.getTorrentId().equals(incomingTorrentId)) {
                    connection.setTorrentId(torrent.getTorrentId());

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
