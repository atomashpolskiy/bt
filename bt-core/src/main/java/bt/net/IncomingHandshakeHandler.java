package bt.net;

import bt.metainfo.Torrent;
import bt.protocol.Handshake;
import bt.protocol.IHandshakeFactory;
import bt.protocol.Message;
import bt.service.IConfigurationService;
import bt.service.ITorrentRegistry;
import bt.torrent.ITorrentDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class IncomingHandshakeHandler implements HandshakeHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(IncomingHandshakeHandler.class);

    private IHandshakeFactory handshakeFactory;
    private ITorrentRegistry torrentRegistry;
    private IConfigurationService configurationService;

    public IncomingHandshakeHandler(IHandshakeFactory handshakeFactory, ITorrentRegistry torrentRegistry,
                                    IConfigurationService configurationService) {
        this.handshakeFactory = handshakeFactory;
        this.torrentRegistry = torrentRegistry;
        this.configurationService = configurationService;
    }

    @Override
    public boolean handleConnection(PeerConnection connection) {

        Message firstMessage = connection.readMessage(configurationService.getHandshakeTimeOut());
        if (firstMessage != null) {
            if (Handshake.class.equals(firstMessage.getClass())) {

                Handshake handshake = (Handshake) firstMessage;
                Torrent torrent = torrentRegistry.getTorrent(handshake.getInfoHash());

                Optional<ITorrentDescriptor> descriptorOptional = torrentRegistry.getDescriptor(torrent);
                if (descriptorOptional.isPresent() && descriptorOptional.get().isActive()) {
                    byte[] infoHash = torrent.getInfoHash();
                    connection.postMessage(handshakeFactory.createHandshake(torrent));
                    connection.setTag(infoHash);
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
