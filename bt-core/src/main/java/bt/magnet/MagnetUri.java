/*
 * Copyright (c) 2016—2017 Andrei Tomashpolskiy and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package bt.magnet;

import bt.metainfo.TorrentId;
import bt.net.InetPeerAddress;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;

/**
 * Programmatic API for working with BEP-9 v1 magnet links.
 *
 * @since 1.3
 */
public class MagnetUri {

    /**
     * Start building a magnet link for a given torrent.
     *
     * @since 1.3
     */
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

    /**
     * Represents the "xt" parameter.
     * E.g. xt=urn:btih:af0d9aa01a9ae123a73802cfa58ccaf355eb19f1
     *
     * @return Torrent ID
     * @since 1.3
     */
    public TorrentId getTorrentId() {
        return torrentId;
    }

    /**
     * Represents the "dn" parameter. Value is URL decoded.
     * E.g. dn=Some%20Display%20Name =&gt; "Some Display Name"
     *
     * @return Suggested display name for the torrent
     * @since 1.3
     */
    public Optional<String> getDisplayName() {
        return displayName;
    }

    /**
     * Represents the collection of values of "tr" parameters. Values are URL decoded.
     * E.g. {@code tr=http%3A%2F%2Fmytracker.com%3A6363&tr=udp%3A%2F%2Fothertrack.org%3A7777}
     * =&gt; ["http://mytracker.com:6363", "udp://othertrack.org:7777"]
     *
     * @return Collection of tracker URLs
     * @since 1.3
     */
    public Collection<String> getTrackerUrls() {
        return trackerUrls;
    }

    /**
     * Represents the collection of values of "x.pe" parameters. Values are URL decoded.
     * E.g. {@code x.pe=124.131.72.242%3A6891&x.pe=11.9.132.61%3A6900}
     * =&gt; [124.131.72.242:6891, 11.9.132.61:6900]
     *
     * @return Collection of well-known peer addresses
     * @since 1.3
     */
    public Collection<InetPeerAddress> getPeerAddresses() {
        return peerAddresses;
    }

    /**
     * @since 1.3
     */
    public static class Builder {
        private TorrentId torrentId;
        private String displayName;
        private Collection<String> trackerUrls;
        private Collection<InetPeerAddress> peerAddresses;

        /**
         * @since 1.3
         */
        public Builder(TorrentId torrentId) {
            this.torrentId = Objects.requireNonNull(torrentId);
        }

        /**
         * Set "dn" parameter.
         * Caller must NOT perform URL encoding, otherwise the value will get encoded twice.
         *
         * @param displayName Suggested display name
         * @since 1.3
         */
        public Builder name(String displayName) {
            this.displayName = Objects.requireNonNull(displayName);
            return this;
        }

        /**
         * Add "tr" parameter.
         * Caller must NOT perform URL encoding, otherwise the value will get encoded twice.
         *
         * @param trackerUrl Tracker URL
         * @since 1.3
         */
        public Builder tracker(String trackerUrl) {
            Objects.requireNonNull(trackerUrl);
            if (trackerUrls == null) {
                trackerUrls = new HashSet<>();
            }
            trackerUrls.add(trackerUrl);
            return this;
        }

        /**
         * Add "x.pe" parameter.
         *
         * @param peerAddress Well-known peer address
         * @since 1.3
         */
        public Builder peer(InetPeerAddress peerAddress) {
            Objects.requireNonNull(peerAddress);
            if (peerAddresses == null) {
                peerAddresses = new HashSet<>();
            }
            peerAddresses.add(peerAddress);
            return this;
        }

        /**
         * @since 1.3
         */
        public MagnetUri buildUri() {
            return new MagnetUri(torrentId, displayName, trackerUrls, peerAddresses);
        }
    }
}
