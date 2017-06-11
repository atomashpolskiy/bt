package bt.magnet;

import bt.metainfo.TorrentId;
import bt.net.InetPeerAddress;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;

public class MagnetUri {

    public static Builder torrentId(TorrentId torrentId) {
        return new Builder(torrentId);
    }

    private TorrentId torrentId;
    private Optional<String> displayName;
    private Collection<String> trackerUrls;
    private Collection<InetPeerAddress> peerAddresses;

    private MagnetUri(TorrentId torrentId,
                      String displayName,
                      Collection<String> trackerUrls,
                      Collection<InetPeerAddress> peerAddresses) {
        this.torrentId = torrentId;
        this.displayName = Optional.ofNullable(displayName);
        this.trackerUrls = (trackerUrls == null) ? Collections.emptyList() : trackerUrls;
        this.peerAddresses = (peerAddresses == null) ? Collections.emptyList() : peerAddresses;
    }

    public TorrentId getTorrentId() {
        return torrentId;
    }

    public Optional<String> getDisplayName() {
        return displayName;
    }

    public Collection<String> getTrackerUrls() {
        return trackerUrls;
    }

    public Collection<InetPeerAddress> getPeerAddresses() {
        return peerAddresses;
    }

    public static class Builder {
        private TorrentId torrentId;
        private String displayName;
        private Collection<String> trackerUrls;
        private Collection<InetPeerAddress> peerAddresses;

        public Builder(TorrentId torrentId) {
            this.torrentId = Objects.requireNonNull(torrentId);
        }

        public Builder name(String displayName) {
            this.displayName = Objects.requireNonNull(displayName);
            return this;
        }

        public Builder tracker(String trackerUrl) {
            Objects.requireNonNull(trackerUrl);
            if (trackerUrls == null) {
                trackerUrls = new HashSet<>();
            }
            trackerUrls.add(trackerUrl);
            return this;
        }

        public Builder peer(InetPeerAddress peerAddress) {
            Objects.requireNonNull(peerAddress);
            if (peerAddresses == null) {
                peerAddresses = new HashSet<>();
            }
            peerAddresses.add(peerAddress);
            return this;
        }

        public MagnetUri buildUri() {
            return new MagnetUri(torrentId, displayName, trackerUrls, peerAddresses);
        }
    }
}
