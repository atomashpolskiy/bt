package bt.net;

import bt.metainfo.Torrent;
import bt.protocol.Handshake;
import bt.protocol.InvalidMessageException;
import bt.protocol.Message;
import bt.protocol.MessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

public class OutgoingHandshakeHandler implements HandshakeHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutgoingHandshakeHandler.class);

    private Torrent torrent;
    private Peer localPeer;
    private long handshakeTimeout;

    public OutgoingHandshakeHandler(Torrent torrent, Peer localPeer, long handshakeTimeout) {
        this.torrent = torrent;
        this.localPeer = localPeer;
        this.handshakeTimeout = handshakeTimeout;
    }

    @Override
    public boolean handleConnection(PeerConnection connection) {

        try {
            Handshake handshake = new Handshake(torrent.getInfoHash(), localPeer.getPeerId());
            connection.postMessage(handshake);

            Message firstMessage = connection.readMessage(handshakeTimeout);
            if (firstMessage != null) {
                if (firstMessage.getType() == MessageType.HANDSHAKE) {
                    byte[] incomingInfoHash = ((Handshake) firstMessage).getInfoHash();
                    if (Arrays.equals(torrent.getInfoHash(), incomingInfoHash)) {
                        connection.setTag(torrent.getInfoHash());
                        return true;
                    }
                } else {
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("Received message of unexpected type " + firstMessage.getType().name() +
                                " in response to handshake; remote peer: " + connection.getRemotePeer());
                    }
                }
            }
        } catch (InvalidMessageException e) {
            LOGGER.error("Failed to build a handshake for the new connection", e);
        }
        return false;
    }
}
