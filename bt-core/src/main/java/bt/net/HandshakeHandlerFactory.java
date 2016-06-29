package bt.net;

import bt.metainfo.Torrent;
import bt.protocol.IHandshakeFactory;
import bt.service.IConfigurationService;
import bt.service.ITorrentRegistry;
import com.google.inject.Inject;

public class HandshakeHandlerFactory implements IHandshakeHandlerFactory {

    private IHandshakeFactory handshakeFactory;
    private ITorrentRegistry torrentRegistry;
    private IConfigurationService configurationService;

    @Inject
    public HandshakeHandlerFactory(IHandshakeFactory handshakeFactory, ITorrentRegistry torrentRegistry,
                                   IConfigurationService configurationService) {
        this.handshakeFactory = handshakeFactory;
        this.torrentRegistry = torrentRegistry;
        this.configurationService = configurationService;
    }

    @Override
    public HandshakeHandler getIncomingHandler() {
        return new IncomingHandshakeHandler(handshakeFactory, torrentRegistry, configurationService);
    }

    @Override
    public HandshakeHandler getOutgoingHandler(Torrent torrent) {
        return new OutgoingHandshakeHandler(handshakeFactory, torrent, configurationService.getHandshakeTimeOut());
    }
}
