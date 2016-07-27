package bt.net;

import bt.metainfo.Torrent;
import bt.metainfo.TorrentId;
import bt.protocol.Handshake;
import bt.protocol.IHandshakeFactory;
import bt.protocol.Message;
import bt.service.IConfigurationService;
import bt.service.ITorrentRegistry;
import bt.torrent.ITorrentDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.Set;

public class IncomingHandshakeHandler implements ConnectionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(IncomingHandshakeHandler.class);

    private IHandshakeFactory handshakeFactory;
    private ITorrentRegistry torrentRegistry;
    private Set<HandshakeHandler> handshakeHandlers;
    private IConfigurationService configurationService;

    public IncomingHandshakeHandler(IHandshakeFactory handshakeFactory, ITorrentRegistry torrentRegistry,
                                    Set<HandshakeHandler> handshakeHandlers, IConfigurationService configurationService) {
        this.handshakeFactory = handshakeFactory;
        this.torrentRegistry = torrentRegistry;
        this.handshakeHandlers = handshakeHandlers;
        this.configurationService = configurationService;
    }

    @Override
    public boolean handleConnection(PeerConnection connection) {

        Message firstMessage = connection.readMessage(configurationService.getHandshakeTimeOut());
        if (firstMessage != null) {
            if (Handshake.class.equals(firstMessage.getClass())) {

                Handshake peerHandshake = (Handshake) firstMessage;
                Torrent torrent = torrentRegistry.getTorrent(peerHandshake.getTorrentId());

                Optional<ITorrentDescriptor> descriptorOptional = torrentRegistry.getDescriptor(torrent);
                if (descriptorOptional.isPresent() && descriptorOptional.get().isActive()) {
                    TorrentId torrentId = torrent.getTorrentId();

                    Handshake handshake = handshakeFactory.createHandshake(torrent);
                    handshakeHandlers.forEach(handler ->
                            handler.amendOutgoingHandshake(handshake));

                    connection.postMessage(handshake);
                    connection.setTorrentId(torrentId);

                    handshakeHandlers.forEach(handler ->
                            handler.processIncomingHandshake(connection.getRemotePeer(), peerHandshake));

                    return true;
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
