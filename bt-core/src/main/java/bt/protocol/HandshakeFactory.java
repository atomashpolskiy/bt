package bt.protocol;

import bt.BtException;
import bt.Constants;
import bt.metainfo.Torrent;
import bt.service.IPeerRegistry;
import com.google.inject.Inject;

public class HandshakeFactory implements IHandshakeFactory {

    private IPeerRegistry peerRegistry;

    @Inject
    public HandshakeFactory(IPeerRegistry peerRegistry) {
        this.peerRegistry = peerRegistry;
    }

    @Override
    public Handshake createHandshake(Torrent torrent) {
        try {
            return new Handshake(new byte[Constants.HANDSHAKE_RESERVED_LENGTH], torrent.getInfoHash(),
                    peerRegistry.getLocalPeer().getPeerId());
        } catch (InvalidMessageException e) {
            throw new BtException("Failed to create handshake", e);
        }
    }
}
