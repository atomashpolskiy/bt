package bt.tracker.udp;

import bt.metainfo.Torrent;
import bt.service.IRuntimeLifecycleBinder;
import bt.service.IdService;
import bt.tracker.Tracker;
import bt.tracker.TrackerRequestBuilder;
import bt.tracker.TrackerResponse;
import bt.tracker.udp.AnnounceRequest.EventType;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URL;
import java.util.Objects;

public class UdpTracker implements Tracker {

    private IdService idService;
    private UdpMessageWorker worker;
    private InetSocketAddress localAddress;

    public UdpTracker(IdService idService,
                      IRuntimeLifecycleBinder lifecycleBinder,
                      URL trackerUrl) {
        this.idService = idService;
        this.localAddress = new InetSocketAddress(0);
        // TODO: one UDP socket for all outgoing tracker connections
        this.worker = new UdpMessageWorker(localAddress, getSocketAddress(trackerUrl), lifecycleBinder);
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
                return worker.sendMessage(request, AnnounceResponseHandler.handler());
            }
        };
    }
}
