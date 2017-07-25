package bt.net.extended;

import bt.metainfo.TorrentId;
import bt.net.HandshakeHandler;
import bt.net.PeerConnection;
import bt.protocol.Handshake;
import bt.protocol.IExtendedHandshakeFactory;
import bt.protocol.extended.ExtendedHandshake;
import com.google.inject.Inject;

/**
 * Sets a reserved bit, indicating that
 * BEP-10: Extension Protocol is supported by the local client.
 *
 * @since 1.0
 */
public class ExtendedProtocolHandshakeHandler implements HandshakeHandler {

    private static final int EXTENDED_FLAG_BIT_INDEX = 43;

    private IExtendedHandshakeFactory extendedHandshakeFactory;

    @Inject
    public ExtendedProtocolHandshakeHandler(IExtendedHandshakeFactory extendedHandshakeFactory) {
        this.extendedHandshakeFactory = extendedHandshakeFactory;
    }

    @Override
    public void processIncomingHandshake(PeerConnection connection, Handshake peerHandshake) {
        ExtendedHandshake extendedHandshake = getHandshake(peerHandshake.getTorrentId());
        // do not send the extended handshake
        // if local client does not have any extensions turned on
        if (!extendedHandshake.getData().isEmpty()) {
            connection.postMessage(extendedHandshake);
        }
    }

    @Override
    public void processOutgoingHandshake(Handshake handshake) {
        ExtendedHandshake extendedHandshake = getHandshake(handshake.getTorrentId());
        // do not advertise support for the extended protocol
        // if local client does not have any extensions turned on
        if (!extendedHandshake.getData().isEmpty()) {
            handshake.setReservedBit(EXTENDED_FLAG_BIT_INDEX);
        }
    }

    private ExtendedHandshake getHandshake(TorrentId torrentId) {
        return extendedHandshakeFactory.getHandshake(torrentId);
    }
}