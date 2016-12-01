package bt.protocol;

import bt.BtException;
import bt.metainfo.TorrentId;
import bt.peer.IPeerRegistry;
import com.google.inject.Inject;

/**
 *<p><b>Note that this class implements a service.
 * Hence, is not a part of the public API and is a subject to change.</b></p>
 */
public class HandshakeFactory implements IHandshakeFactory {

    private static final int HANDSHAKE_RESERVED_LENGTH = 8;

    private IPeerRegistry peerRegistry;

    @Inject
    public HandshakeFactory(IPeerRegistry peerRegistry) {
        this.peerRegistry = peerRegistry;
    }

    @Override
    public Handshake createHandshake(TorrentId torrentId) {
        try {
            return new Handshake(new byte[HANDSHAKE_RESERVED_LENGTH], torrentId,
                    peerRegistry.getLocalPeer().getPeerId().orElseThrow(() -> new BtException("Local peer is missing ID")));
        } catch (InvalidMessageException e) {
            throw new BtException("Failed to create handshake", e);
        }
    }
}
