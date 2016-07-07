package bt.net;

import bt.metainfo.Torrent;
import bt.protocol.IHandshakeFactory;
import bt.service.IConfigurationService;
import bt.service.ITorrentRegistry;
import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class HandshakeHandlerFactory implements IHandshakeHandlerFactory {

    private IHandshakeFactory handshakeFactory;
    private HandshakeHandler incomingHandler;
    private IConfigurationService configurationService;

    private List<HandshakeHandler> handshakeHandlers;

    @Inject
    public HandshakeHandlerFactory(IHandshakeFactory handshakeFactory, ITorrentRegistry torrentRegistry,
                                   Set<HandshakeHandler> handshakeHandlers, IConfigurationService configurationService) {
        this.handshakeFactory = handshakeFactory;
        this.configurationService = configurationService;

        incomingHandler = new IncomingHandshakeHandler(handshakeFactory, torrentRegistry, configurationService);

        this.handshakeHandlers = new ArrayList<>();
        this.handshakeHandlers.addAll(handshakeHandlers);
    }

    @Override
    public HandshakeHandler getIncomingHandler() {
        return incomingHandler;
    }

    @Override
    public HandshakeHandler getOutgoingHandler(Torrent torrent) {

        HandshakeHandler ougoing = new OutgoingHandshakeHandler(
                handshakeFactory, torrent, configurationService.getHandshakeTimeOut());

        return new HandshakeSequence(ougoing, handshakeHandlers);
    }

    private static class HandshakeSequence implements HandshakeHandler {

        private HandshakeHandler firstHandler;
        private List<HandshakeHandler> otherHandlers;

        HandshakeSequence(HandshakeHandler firstHandler, List<HandshakeHandler> otherHandlers) {
            this.firstHandler = firstHandler;
            this.otherHandlers = otherHandlers;
        }

        @Override
        public boolean handleConnection(PeerConnection connection) {

            if (!firstHandler.handleConnection(connection)) {
                return false;
            }

            for (HandshakeHandler handshakeHandler : otherHandlers) {
                if (!handshakeHandler.handleConnection(connection)) {
                    return false;
                }
            }
            return true;
        }
    }
}
