package bt.net;

import bt.metainfo.Torrent;
import bt.protocol.Handshake;
import bt.protocol.IHandshakeFactory;
import bt.protocol.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

public class OutgoingHandshakeHandler implements HandshakeHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutgoingHandshakeHandler.class);

    private IHandshakeFactory handshakeFactory;
    private Torrent torrent;
    private long handshakeTimeout;

    public OutgoingHandshakeHandler(IHandshakeFactory handshakeFactory, Torrent torrent, long handshakeTimeout) {
        this.handshakeFactory = handshakeFactory;
        this.torrent = torrent;
        this.handshakeTimeout = handshakeTimeout;
    }

    @Override
    public boolean handleConnection(PeerConnection connection) {

        Handshake handshake = handshakeFactory.createHandshake(torrent);
        connection.postMessage(handshake);

        Message firstMessage = connection.readMessage(handshakeTimeout);
        if (firstMessage != null) {
            if (Handshake.class.equals(firstMessage.getClass())) {
                byte[] incomingInfoHash = ((Handshake) firstMessage).getInfoHash();
                if (Arrays.equals(torrent.getInfoHash(), incomingInfoHash)) {
                    connection.setTag(torrent.getInfoHash());
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
