package bt.net;

import bt.data.Bitfield;
import bt.protocol.Handshake;
import bt.torrent.TorrentDescriptor;
import bt.torrent.TorrentRegistry;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Sends local bitfield to a newly connected remote peer.
 *
 * @since 1.0
 */
public class BitfieldConnectionHandler implements HandshakeHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(BitfieldConnectionHandler.class);

    private TorrentRegistry torrentRegistry;

    @Inject
    public BitfieldConnectionHandler(TorrentRegistry torrentRegistry) {
        this.torrentRegistry = torrentRegistry;
    }

    @Override
    public void processIncomingHandshake(PeerConnection connection, Handshake peerHandshake) {
        Optional<TorrentDescriptor> descriptorOptional = torrentRegistry.getDescriptor(connection.getTorrentId());
        if (descriptorOptional.isPresent() && descriptorOptional.get().isActive()
                && descriptorOptional.get().getDataDescriptor() != null) {
            Bitfield bitfield = descriptorOptional.get().getDataDescriptor().getBitfield();

            if (bitfield.getPiecesComplete() > 0) {
                bt.protocol.Bitfield bitfieldMessage = new bt.protocol.Bitfield(bitfield.getBitmask());
                connection.postMessage(bitfieldMessage);

                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Sending " + bitfieldMessage + " for " + connection.getRemotePeer());
                }
            }
        }
    }

    @Override
    public void processOutgoingHandshake(Handshake handshake) {
        // do nothing
    }
}
