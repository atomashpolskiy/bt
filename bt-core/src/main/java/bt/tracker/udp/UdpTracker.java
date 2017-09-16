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

package bt.tracker.udp;

import bt.metainfo.TorrentId;
import bt.service.IRuntimeLifecycleBinder;
import bt.service.IdentityService;
import bt.tracker.Tracker;
import bt.tracker.TrackerRequestBuilder;
import bt.tracker.TrackerResponse;
import bt.tracker.udp.AnnounceRequest.EventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.SocketAddress;
import java.net.URL;
import java.util.Objects;
import java.util.Optional;

/**
 * Simple implementation of a UDP tracker client
 *
 * @since 1.0
 */
class UdpTracker implements Tracker {

    private static final Logger LOGGER = LoggerFactory.getLogger(UdpTracker.class);

    private IdentityService idService;
    private int listeningPort;
    private int numberOfPeersToRequestFromTracker;
    private URL trackerUrl;
    private UdpMessageWorker worker;

    /**
     * @param trackerUrl String representation of the tracker's URL.
     *                   Must start with "udp://" pseudo-protocol.
     * @since 1.0
     */
    public UdpTracker(IdentityService idService,
                      IRuntimeLifecycleBinder lifecycleBinder,
                      InetAddress localAddress,
                      int listeningPort,
                      int numberOfPeersToRequestFromTracker,
                      String trackerUrl) {
        this.idService = idService;
        this.listeningPort = listeningPort;
        this.numberOfPeersToRequestFromTracker = numberOfPeersToRequestFromTracker;
        // TODO: one UDP socket for all outgoing tracker connections
        this.trackerUrl = toUrl(trackerUrl);
        this.worker = new UdpMessageWorker(new InetSocketAddress(localAddress, 0), getSocketAddress(this.trackerUrl), lifecycleBinder);
    }

    private URL toUrl(String s) {
        if (!s.startsWith("udp://")) {
            throw new IllegalArgumentException("Unexpected URL format: " + s);
        }
        // workaround for java.net.MalformedURLException (unsupported protocol: udp)
        s = s.replace("udp://", "http://");

        try {
            return new URL(s);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid URL: " + s);
        }
    }

    private SocketAddress getSocketAddress(URL url) {
        String host = Objects.requireNonNull(url.getHost(), "Host name is required");
        int port = getPort(url);
        return new InetSocketAddress(host, port);
    }

    private int getPort(URL url) {
        int port = url.getPort();
        if (port < 0) {
            port = url.getDefaultPort();
            if (port < 0) {
                throw new IllegalArgumentException("Can't determine port from URL: " + url.toExternalForm());
            }
        }
        return port;
    }

    @Override
    public TrackerRequestBuilder request(TorrentId torrentId) {
        return new TrackerRequestBuilder(torrentId) {
            @Override
            public TrackerResponse start() {
                return announceEvent(EventType.START);
            }

            @Override
            public TrackerResponse stop() {
                return announceEvent(EventType.STOP);
            }

            @Override
            public TrackerResponse complete() {
                return announceEvent(EventType.COMPLETE);
            }

            @Override
            public TrackerResponse query() {
                return announceEvent(EventType.QUERY);
            }

            private TrackerResponse announceEvent(EventType eventType) {
                AnnounceRequest request = new AnnounceRequest();
                request.setTorrentId(getTorrentId());
                request.setPeerId(idService.getLocalPeerId());
                request.setEventType(eventType);
                request.setListeningPort((short) listeningPort);

                request.setDownloaded(getDownloaded());
                request.setUploaded(getUploaded());
                request.setLeft(getLeft());
                request.setNumwant(numberOfPeersToRequestFromTracker);

                getRequestString(trackerUrl).ifPresent(request::setRequestString);
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Executing tracker UDP request of type {}: {}", eventType.name(), request);
                }
                try {
                    return worker.sendMessage(request, AnnounceResponseHandler.handler());
                } catch (Exception e) {
                    return TrackerResponse.exceptional(e);
                }
            }
        };
    }

    private Optional<String> getRequestString(URL url) {
        String result = url.getPath();
        if (url.getQuery() != null) {
            result += "?" + url.getQuery();
        }
        return result.isEmpty() ? Optional.empty() : Optional.of(result);
    }

    @Override
    public String toString() {
        return "UdpTracker{" +
                "trackerUrl=" + trackerUrl +
                '}';
    }
}
