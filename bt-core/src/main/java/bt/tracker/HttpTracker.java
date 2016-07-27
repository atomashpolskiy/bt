package bt.tracker;

import bt.BtException;
import bt.metainfo.Torrent;
import bt.service.IIdService;
import bt.service.INetworkService;
import bt.service.IdService;
import bt.service.NetworkService;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class HttpTracker implements Tracker {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpTracker.class);

    private URI baseUri;
    private IIdService idService;
    private INetworkService networkService;
    private HttpClient httpClient;
    private HttpResponseHandler httpResponseHandler;

    private Charset defaultHttpCharset;

    private ConcurrentMap<URI, byte[]> trackerIds;

    public HttpTracker(URL baseUrl) {

        try {
            this.baseUri = baseUrl.toURI();
        } catch (URISyntaxException e) {
            throw new BtException("Invalid URL: " + baseUrl, e);
        }

        this.idService = new IdService();
        this.networkService = new NetworkService();
        this.httpClient = HttpClients.createMinimal();
        this.httpResponseHandler = new HttpResponseHandler(new TrackerResponseHandler());

        defaultHttpCharset = Charset.forName("ISO-8859-1");

        trackerIds = new ConcurrentHashMap<>();
    }

    @Override
    public TrackerRequestBuilder request(Torrent torrent) {
        return new TrackerRequestBuilder(torrent.getTorrentId()) {
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

        String requestUri;
        try {
            String query = buildQuery(eventType, requestBuilder);

            String baseUrl = baseUri.toASCIIString();
            if (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }
            URL requestUrl = new URL(baseUrl + (baseUri.getRawQuery() == null? "?" : "&") + query);
            requestUri = requestUrl.toURI().toString();

        } catch (Exception e) {
            throw new BtException("Failed to build tracker request", e);
        }

        HttpGet request = new HttpGet(requestUri);
        try {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Executing tracker HTTP request of type " + eventType.name() +
                        "; request URL: " + requestUri);
            }
            return httpClient.execute(request, httpResponseHandler);
        } catch (IOException e) {
            throw new BtException("Failed to execute tracker request", e);
        }
    }

    private String buildQuery(TrackerRequestType eventType, TrackerRequestBuilder requestBuilder) throws Exception {

        StringBuilder buf = new StringBuilder();

        buf.append("info_hash=");
        buf.append(urlEncode(requestBuilder.getTorrentId().getBytes()));

        buf.append("&peer_id=");
        buf.append(urlEncode(idService.getLocalPeerId().getBytes()));

        InetAddress inetAddress = networkService.getInetAddress();
        if (inetAddress != null) {
            buf.append("&ip=");
            buf.append(inetAddress.getHostAddress());
        }

        int port = networkService.getPort();
        buf.append("&port=");
        buf.append(port);

        buf.append("&uploaded=");
        buf.append(requestBuilder.getUploaded());

        buf.append("&downloaded=");
        buf.append(requestBuilder.getDownloaded());

        buf.append("&left=");
        buf.append(requestBuilder.getLeft());

        buf.append("&compact=1");
        buf.append("&numwant=50");

        byte[] secretKey = idService.getSecretKey();
        if (secretKey != null) {
            buf.append("&key=");
            buf.append(urlEncode(secretKey));
        }

        byte[] trackerId = trackerIds.get(baseUri);
        if (trackerId != null) {
            buf.append("&trackerid=");
            buf.append(urlEncode(trackerId));
        }

        switch (eventType) {
            case START: {
                buf.append("&event=started");
                break;
            }
            case STOP: {
                buf.append("&event=stopped");
                break;
            }
            case COMPLETE: {
                buf.append("&event=completed");
                break;
            }
            case QUERY: {
                // do not specify event
                break;
            }
            default: {
                throw new BtException("Unexpected event type: " + eventType.name().toLowerCase());
            }
        }

        return buf.toString();
    }

    private String urlEncode(byte[] bytes) {

        StringBuilder buf = new StringBuilder();
        for (byte b : bytes) {
            char c = (char) b;
            if   ( (c >= 48 && c <= 57) // 0-9
                || (c >= 65 && c <= 90) // A-Z
                || (c >= 97 && c <= 122) // a-z
                ||  c == 45  // -
                ||  c == 46  // .
                ||  c == 95  // _
                ||  c == 126 // ~
            ) {
                buf.append(c);
            } else {
                buf.append("%");
                String hex = Integer.toHexString(b & 0xFF).toUpperCase();
                if (hex.length() == 1) {
                    buf.append("0");
                }
                buf.append(hex);
            }
        }
        return buf.toString();
    }

    private class HttpResponseHandler implements ResponseHandler<TrackerResponse> {

        private TrackerResponseHandler trackerResponseHandler;

        HttpResponseHandler(TrackerResponseHandler trackerResponseHandler) {
            this.trackerResponseHandler = trackerResponseHandler;
        }

        @Override
        public TrackerResponse handleResponse(HttpResponse response) {

            final StatusLine statusLine = response.getStatusLine();
            final HttpEntity entity = response.getEntity();
            if (statusLine.getStatusCode() >= 300) {
                try {
                    EntityUtils.consume(entity);
                } catch (IOException e) {
                    // do nothing...
                }
                throw new BtException("Tracker returned error (" + statusLine.getStatusCode() + ": "
                        + statusLine.getReasonPhrase() + ")");
            }

            if (entity == null) {
                throw new BtException("Tracker response is empty");
            } else {
                try {

                    Charset charset = null;
                    ContentType contentType = ContentType.get(entity);
                    if (contentType != null) {
                        charset = contentType.getCharset();
                    }
                    if (charset == null) {
                        charset = defaultHttpCharset;
                    }

                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    entity.writeTo(bytes);
                    return trackerResponseHandler.handleResponse(bytes.toByteArray(), charset);
                } catch (IOException e) {
                    throw new BtException("Failed to read tracker response", e);
                }
            }
        }
    }
}
