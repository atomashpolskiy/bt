package bt.net;

import bt.metainfo.Torrent;
import bt.metainfo.TorrentId;
import bt.protocol.Handshake;
import bt.protocol.IHandshakeFactory;
import bt.protocol.Message;
import bt.service.TorrentRegistry;
import bt.torrent.ITorrentDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

/**
 * Handles handshake exchange for incoming peer connections.
 *
 * @since 1.0
 */
public class IncomingHandshakeHandler implements ConnectionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(IncomingHandshakeHandler.class);

    private IHandshakeFactory handshakeFactory;
    private TorrentRegistry torrentRegistry;
    private Set<HandshakeHandler> handshakeHandlers;
    private Duration handshakeTimeout;

    public IncomingHandshakeHandler(IHandshakeFactory handshakeFactory, TorrentRegistry torrentRegistry,
                                    Set<HandshakeHandler> handshakeHandlers, Duration handshakeTimeout) {
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
                    Optional<ITorrentDescriptor> descriptorOptional = torrentRegistry.getDescriptor(torrent);
                    if (descriptorOptional.isPresent() && descriptorOptional.get().isActive()) {
                        TorrentId torrentId = torrent.getTorrentId();

                        Handshake handshake = handshakeFactory.createHandshake(torrent.getTorrentId());
                        handshakeHandlers.forEach(handler ->
                                handler.processOutgoingHandshake(handshake));

                        connection.postMessage(handshake);
                        connection.setTorrentId(torrentId);

                        handshakeHandlers.forEach(handler ->
                                handler.processIncomingHandshake(connection.getRemotePeer(), peerHandshake));

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
