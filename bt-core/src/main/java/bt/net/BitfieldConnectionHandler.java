package bt.net;

import bt.data.Bitfield;
import bt.protocol.Handshake;
import bt.torrent.TorrentDescriptor;
import bt.torrent.TorrentRegistry;
import com.google.inject.Inject;

import java.io.IOException;
import java.util.Optional;

/**
 * Sends local bitfield to a newly connected remote peer.
 *
 * @since 1.0
 */
public class BitfieldConnectionHandler implements HandshakeHandler {

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
                Peer peer = connection.getRemotePeer();
                bt.protocol.Bitfield bitfieldMessage = new bt.protocol.Bitfield(bitfield.getBitmask());
                try {
                    connection.postMessage(bitfieldMessage);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to send bitfield to peer: " + peer, e);
                }
            }
        }
    }

    @Override
    public void processOutgoingHandshake(Handshake handshake) {
        // do nothing
    }
}
