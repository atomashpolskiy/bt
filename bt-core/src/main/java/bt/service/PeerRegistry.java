package bt.service;

import bt.metainfo.Torrent;
import bt.net.InetPeer;
import bt.net.Peer;
import com.google.inject.Inject;

import java.net.InetAddress;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class PeerRegistry implements IPeerRegistry {

    private Set<PeerSource> peerSources;
    private final Peer localPeer;

    ConcurrentMap<Torrent, Long> lastQueryTimes;

    @Inject
    public PeerRegistry(INetworkService networkService, IIdService idService, Set<PeerSource> peerSources) {

        this.peerSources = peerSources;

        localPeer = new InetPeer(networkService.getInetAddress(), networkService.getPort(), idService.getPeerId());
        lastQueryTimes = new ConcurrentHashMap<>();
    }

    @Override
    public Peer getLocalPeer() {
        return localPeer;
    }

    @Override
    public Peer getOrCreatePeer(InetAddress inetAddress, int port) {
        return new InetPeer(inetAddress, port);
    }

    @Override
    public Collection<Peer> getPeersForTorrent(Torrent torrent) {

        Collection<Peer> peers = new HashSet<>();
        for (PeerSource peerSource : peerSources) {
            peers.addAll(peerSource.getPeersForTorrent(torrent));
        }
        return peers;
    }
}
