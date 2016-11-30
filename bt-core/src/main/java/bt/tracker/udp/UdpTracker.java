package bt.tracker.udp;

import bt.metainfo.Torrent;
import bt.service.IRuntimeLifecycleBinder;
import bt.service.IdentityService;
import bt.tracker.Tracker;
import bt.tracker.TrackerRequestBuilder;
import bt.tracker.TrackerResponse;
import bt.tracker.udp.AnnounceRequest.EventType;

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
public class UdpTracker implements Tracker {

    private IdentityService idService;
    private InetSocketAddress localAddress;
    private URL trackerUrl;
    private UdpMessageWorker worker;

    /**
     * @param trackerUrl String representation of the tracker's URL.
     *                   Must start with "udp://" pseudo-protocol.
     * @since 1.0
     */
    public UdpTracker(IdentityService idService,
                      IRuntimeLifecycleBinder lifecycleBinder,
                      String trackerUrl) {
        this.idService = idService;
        this.localAddress = new InetSocketAddress(0);
        // TODO: one UDP socket for all outgoing tracker connections
        this.trackerUrl = toUrl(trackerUrl);
        this.worker = new UdpMessageWorker(localAddress, getSocketAddress(this.trackerUrl), lifecycleBinder);
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
    public TrackerRequestBuilder request(Torrent torrent) {
        return new TrackerRequestBuilder(torrent.getTorrentId()) {
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
                request.setDownloaded(getDownloaded());
                request.setLeft(getLeft());
                request.setUploaded(getUploaded());
                request.setEventType(eventType);
                request.setListeningPort((short) localAddress.getPort());
                getRequestString(trackerUrl).ifPresent(request::setRequestString);
                return worker.sendMessage(request, AnnounceResponseHandler.handler());
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
}
