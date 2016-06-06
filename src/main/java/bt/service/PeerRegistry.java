package bt.service;

import bt.metainfo.Torrent;
import bt.net.InetPeer;
import bt.net.Peer;
import bt.tracker.ITrackerService;
import bt.tracker.Tracker;
import bt.tracker.TrackerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class PeerRegistry implements IPeerRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(PeerRegistry.class);

    private ITrackerService trackerService;
    private IConfigurationService configurationService;
    private final Peer localPeer;

    ConcurrentMap<Torrent, Long> lastQueryTimes;

    public PeerRegistry(INetworkService networkService, IIdService idService, ITrackerService trackerService,
                        IConfigurationService configurationService) {

        this.trackerService = trackerService;
        this.configurationService = configurationService;

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
    public Iterable<Peer> getPeersForTorrent(Torrent torrent) {

        Long lastQueryTime = lastQueryTimes.get(torrent);
        if (lastQueryTime != null &&
                System.currentTimeMillis() - lastQueryTime <= configurationService.getPeerRefreshThreshold()) {
            return Collections.emptyList();
        }

        Tracker tracker = trackerService.getTracker(torrent.getTrackerUrl());
        TrackerResponse response = tracker.request(torrent).query();
        lastQueryTimes.put(torrent, System.currentTimeMillis());
        if (response.isSuccess()) {
            return response.getPeers();
        } else {
            LOGGER.warn("Failed to get peers for torrent -- " +
                    "unexpected error during interaction with the tracker: " + response.getErrorMessage());
            return Collections.emptyList();
        }
    }
}
