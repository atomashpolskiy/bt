package bt.net;

import bt.metainfo.Torrent;
import bt.service.ITorrentRegistry;
import bt.torrent.Bitfield;
import bt.torrent.ITorrentDescriptor;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class BitfieldConnectionHandler implements ConnectionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(BitfieldConnectionHandler.class);

    private ITorrentRegistry torrentRegistry;

    @Inject
    public BitfieldConnectionHandler(ITorrentRegistry torrentRegistry) {
        this.torrentRegistry = torrentRegistry;
    }

    @Override
    public boolean handleConnection(PeerConnection connection) {
        Torrent torrent = torrentRegistry.getTorrent(connection.getTorrentId());

        Optional<ITorrentDescriptor> descriptorOptional = torrentRegistry.getDescriptor(torrent);
        if (descriptorOptional.isPresent() && descriptorOptional.get().isActive()) {
            Bitfield bitfield = descriptorOptional.get().getDataDescriptor().getBitfield();

            if (bitfield.getPiecesComplete() > 0) {
                bt.protocol.Bitfield bitfieldMessage = new bt.protocol.Bitfield(bitfield.getBitmask());
                connection.postMessage(bitfieldMessage);

                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Sending " + bitfieldMessage + " for " + connection.getRemotePeer());
                }
            }
            return true;
        }
        return false;
    }
}
