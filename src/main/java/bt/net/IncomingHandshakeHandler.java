package bt.net;

import bt.metainfo.Torrent;
import bt.protocol.Handshake;
import bt.protocol.InvalidMessageException;
import bt.protocol.Message;
import bt.protocol.MessageType;
import bt.service.IConfigurationService;
import bt.service.IPeerRegistry;
import bt.service.ITorrentRegistry;
import bt.torrent.ITorrentDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IncomingHandshakeHandler implements HandshakeHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(IncomingHandshakeHandler.class);

    private ITorrentRegistry torrentRegistry;
    private IPeerRegistry peerRegistry;
    private IConfigurationService configurationService;

    public IncomingHandshakeHandler(ITorrentRegistry torrentRegistry, IPeerRegistry peerRegistry,
                                    IConfigurationService configurationService) {
        this.torrentRegistry = torrentRegistry;
        this.peerRegistry = peerRegistry;
        this.configurationService = configurationService;
    }

    @Override
    public boolean handleConnection(PeerConnection connection) {

        Message firstMessage = connection.readMessage(configurationService.getHandshakeTimeOut());
        if (firstMessage != null) {
            if (firstMessage.getType() == MessageType.HANDSHAKE) {

                Handshake handshake = (Handshake) firstMessage;
                Torrent torrent = torrentRegistry.getTorrent(handshake.getInfoHash());
                ITorrentDescriptor descriptor = torrentRegistry.getDescriptor(torrent);
                if (descriptor.isActive()) {
                    try {
                        byte[] infoHash = torrent.getInfoHash();
                        connection.postMessage(new Handshake(infoHash, peerRegistry.getLocalPeer().getPeerId()));
                        return true;
                    } catch (InvalidMessageException e) {
                        LOGGER.error("Failed to build a handshake response for the incoming connection", e);
                    }
                }
            } else {
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Received message of unexpected type " + firstMessage.getType().name() +
                            " as handshake; remote peer: " + connection.getRemotePeer());
                }
            }
        }
        return false;
    }
}
