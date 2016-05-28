package bt.service;

import bt.net.InetPeer;
import bt.net.Peer;

import java.net.InetAddress;

public class PeerRegistry implements IPeerRegistry {

    private final Peer localPeer;

    public PeerRegistry(INetworkService networkService, IIdService idService) {
        localPeer = new InetPeer(networkService.getInetAddress(), networkService.getPort(), idService.getPeerId());
    }

    @Override
    public Peer getLocalPeer() {
        return localPeer;
    }

    @Override
    public Peer getOrCreatePeer(InetAddress inetAddress, int port) {
        return new InetPeer(inetAddress, port);
    }
}
