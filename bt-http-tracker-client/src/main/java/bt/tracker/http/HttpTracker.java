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

package bt.tracker.http;

import bt.BtException;
import bt.metainfo.TorrentId;
import bt.net.Peer;
import bt.peer.IPeerRegistry;
import bt.protocol.crypto.EncryptionPolicy;
import bt.service.IdentityService;
import bt.torrent.TorrentDescriptor;
import bt.torrent.TorrentRegistry;
import bt.torrent.TorrentSessionState;
import bt.tracker.SecretKey;
import bt.tracker.Tracker;
import bt.tracker.TrackerRequestBuilder;
import bt.tracker.TrackerResponse;
import bt.tracker.http.urlencoding.TrackerQueryBuilder;
import org.apache.http.HttpClientConnection;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * Simple implementation of an HTTP tracker client.
 *
 * @since 1.0
 */
public class HttpTracker implements Tracker {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpTracker.class);

    protected enum TrackerRequestType {
        START("started"),
        STOP("stopped"),
        COMPLETE("completed"),
        QUERY(null);
        private final String eventVal;

        TrackerRequestType(String eventVal) {
            this.eventVal = eventVal;
        }

        public String getEventVal() {
            return eventVal;
        }
    }

    private final URI baseUri;
    private final RequestConfig requestConfig;
    private final TorrentRegistry torrentRegistry;
    private final IdentityService idService;
    private final IPeerRegistry peerRegistry;
    private final EncryptionPolicy encryptionPolicy;
    private final int numberOfPeersToRequestFromTracker;
    private final CloseableHttpClient httpClient;
    private final CommonsHttpResponseHandler httpResponseHandler;

    private final ConcurrentMap<URI, byte[]> trackerIds;

    /**
     * @param trackerUrl Tracker URL
     * @param idService  Identity service
     * @since 1.0
     */
    public HttpTracker(String trackerUrl,
                       TorrentRegistry torrentRegistry,
                       IdentityService idService,
                       IPeerRegistry peerRegistry,
                       EncryptionPolicy encryptionPolicy,
                       InetAddress localAddress,
                       int numberOfPeersToRequestFromTracker,
                       Duration timeout) {
        try {
            this.baseUri = new URI(trackerUrl);
        } catch (URISyntaxException e) {
            throw new BtException("Invalid URL: " + trackerUrl, e);
        }

        this.torrentRegistry = torrentRegistry;
        this.idService = idService;
        this.peerRegistry = peerRegistry;
        this.encryptionPolicy = encryptionPolicy;
        this.numberOfPeersToRequestFromTracker = numberOfPeersToRequestFromTracker;
        requestConfig = buildReqConfig(localAddress, timeout);
        this.httpClient = HttpClients.createMinimal(new BasicHttpClientConnectionManager() {
            @Override
            public synchronized void releaseConnection(HttpClientConnection conn, Object state, long keepalive,
                                                       TimeUnit timeUnit) {
                try {
                    // close the connection to the tracker. Maintaining a persistent HTTP 1.1 connection is a waste of
                    // the tracker's resources, as well as ours.
                    conn.close();
                } catch (IOException ex) {
                    LOGGER.debug("Error closing tracker connection.", ex);
                }
                super.releaseConnection(conn, state, keepalive, timeUnit);
            }
        });
        this.httpResponseHandler = new CommonsHttpResponseHandler(new bt.tracker.http.HttpResponseHandler());

        this.trackerIds = new ConcurrentHashMap<>();
    }

    private RequestConfig buildReqConfig(InetAddress localAddress, Duration timeout) {
        Duration trackerTimeout = timeout == null ? Duration.ofSeconds(30) : timeout;
        return RequestConfig.custom()
                .setLocalAddress(localAddress)
                .setConnectTimeout((int) trackerTimeout.toMillis())
                .setSocketTimeout((int) trackerTimeout.toMillis())
                .build();
    }

    @Override
    public TrackerRequestBuilder request(TorrentId torrentId) {
        return new TrackerRequestBuilder(torrentId) {
            @Override
            public TrackerResponse start() {
                return sendEvent(TrackerRequestType.START, this);
            }

            @Override
            public TrackerResponse stop() {
                return sendEvent(TrackerRequestType.STOP, this);
            }

            @Override
            public TrackerResponse complete() {
                return sendEvent(TrackerRequestType.COMPLETE, this);
            }

            @Override
            public TrackerResponse query() {
                return sendEvent(TrackerRequestType.QUERY, this);
            }
        };
    }

    private TrackerResponse sendEvent(TrackerRequestType eventType, TrackerRequestBuilder requestBuilder) {
        String requestUri = buildQueryUri(eventType, requestBuilder);

        HttpGet request = new HttpGet(requestUri);
        request.setHeader("Connection", "close");
        request.setConfig(requestConfig);
        try {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Executing tracker HTTP request of type " + eventType.name() +
                        "; request URL: " + requestUri);
            }
            return httpClient.execute(request, httpResponseHandler);
        } catch (IOException e) {
            return TrackerResponse.exceptional(e);
        }
    }

    private String buildQueryUri(TrackerRequestType eventType, TrackerRequestBuilder requestBuilder) {
        String requestUri;
        try {
            String query = buildQuery(eventType, requestBuilder);

            String baseUrl = baseUri.toASCIIString();
            if (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }
            URL requestUrl = new URL(baseUrl + (baseUri.getRawQuery() == null ? "?" : "&") + query);
            requestUri = requestUrl.toURI().toString();

        } catch (Exception e) {
            throw new BtException("Failed to build tracker request", e);
        }
        return requestUri;
    }

    private String buildQuery(TrackerRequestType eventType, TrackerRequestBuilder requestBuilder) {
        TrackerQueryBuilder queryBuilder = createTrackerQuery(eventType, requestBuilder);
        return queryBuilder.toQuery();
    }

    /**
     * Build the query to send to the tracker. This method is protected so that this class can easily be extended
     * to support additional tracker parameters, for example
     * <a href="https://wiki.theory.org/BitTorrent_Location-aware_Protocol_1.0_Specification">BitTorrent location aware protocol</a>
     *
     * @param eventType      The event type to announce to the tracker
     * @param requestBuilder the information to build the request
     */
    protected TrackerQueryBuilder createTrackerQuery(TrackerRequestType eventType,
                                                     TrackerRequestBuilder requestBuilder) {
        TrackerQueryBuilder queryBuilder = new TrackerQueryBuilder();

        queryBuilder.add("info_hash", requestBuilder.getTorrentId().getBytes());
        queryBuilder.add("peer_id", idService.getLocalPeerId().getBytes());

        Peer peer = peerRegistry.getLocalPeer();
        InetAddress inetAddress = peer.getInetAddress();
        if (inetAddress != null) {
            queryBuilder.add("ip", inetAddress.getHostAddress());
        }

        queryBuilder.add("port", peer.getPort());

        // set the torrent state if we can.
        torrentRegistry.getDescriptor(requestBuilder.getTorrentId())
                .flatMap(TorrentDescriptor::getSessionState)
                .ifPresent(state -> {
                    queryBuilder.add("uploaded", state.getUploaded());
                    queryBuilder.add("downloaded", state.getDownloaded());
                    long left = state.getLeft();
                    if (left != TorrentSessionState.UNKNOWN) {
                        queryBuilder.add("left", state.getLeft());
                    }
                });

        queryBuilder.add("compact", 1);
        int numWant =
                requestBuilder.getNumWant() == null ? numberOfPeersToRequestFromTracker : requestBuilder.getNumWant();
        queryBuilder.add("numwant", numWant);

        Optional<SecretKey> secretKey = idService.getSecretKey();
        if (secretKey.isPresent()) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            secretKey.get().writeTo(bos);
            queryBuilder.add("key", bos.toByteArray());
        }

        byte[] trackerId = trackerIds.get(baseUri);
        if (trackerId != null) {
            queryBuilder.add("trackerid", trackerId);
        }

        if (null != eventType.getEventVal()) {
            queryBuilder.add("event", eventType.getEventVal());
        }

        switch (encryptionPolicy) {
            case PREFER_PLAINTEXT:
            case PREFER_ENCRYPTED:
                queryBuilder.add("supportcrypto", 1);
                break;
            case REQUIRE_ENCRYPTED: {
                queryBuilder.add("requirecrypto", 1);
                break;
            }
            default: {
                // do nothing
            }
        }
        return queryBuilder;
    }

    @Override
    public String toString() {
        return "HttpTracker{" + "baseUri=" + baseUri + '}';
    }

    @Override
    public void close() {
        try {
            this.httpClient.close();
        } catch (IOException ex) {
            LOGGER.info("Error closing tracker http client", ex);
        }
    }
}
