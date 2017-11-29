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

package bt.peer.lan;

import bt.metainfo.TorrentId;
import bt.protocol.Protocols;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.StringTokenizer;

import static bt.net.InternetProtocolUtils.getLiteralIP;

class AnnounceMessage {
    private static final Charset ascii = Charset.forName("ASCII");
    private static final String DELIMITER = "\r\n";
    private static final String HEADER = "BT-SEARCH * HTTP/1.1" + DELIMITER;
    private static final String TERMINATOR = DELIMITER + DELIMITER;

    public static int calculateMessageSize(int numberOfTorrentIds) {
        return HEADER.length()
                + 6 + 4*8+7+2 /*IPv6 literal max length*/ + 1/*colon*/ + 5/*port*/ + DELIMITER.length() /*port literal max length*/ // "Host: <ip>:<port>\r\n"
                + 6 + 5 /*port literal max length*/ + DELIMITER.length() // "Port: <port>\r\n"
                + (10 + 40 /*infohash length in hex*/ + DELIMITER.length()) * numberOfTorrentIds // "Infohash: <infohash>\r\n"
                + 8 + (Cookie.maxLength()*2)/*pessimistic estimate for other libs*/ + DELIMITER.length() // "cookie: <cookie>\r\n"
                + DELIMITER.length() * 2; // "\r\n\r\n"
    }

    public static Builder builder() {
        return new Builder();
    }

    private final Cookie cookie;
    private final int port;
    private final Set<TorrentId> ids;

    private AnnounceMessage(Cookie cookie, int port, Set<TorrentId> ids) {
        this.port = port;
        this.ids = ids;
        this.cookie = cookie;
    }

    public Cookie getCookie() {
        return cookie;
    }

    public int getPort() {
        return port;
    }

    public Set<TorrentId> getTorrentIds() {
        return ids;
    }

    public void writeTo(ByteBuffer buffer, InetSocketAddress recipient) {
        byte[] message = getMessageBytes(recipient);
        if (buffer.remaining() < message.length) {
            throw new IllegalStateException("Can't write message to buffer: insufficient space");
        }
        buffer.put(message);
    }

    /*
        BT-SEARCH * HTTP/1.1\r\n
        Host: <host>\r\n
        Port: <port>\r\n
        Infohash: <ihash>\r\n
        ...
        cookie: <cookie (optional)>\r\n
        \r\n
        \r\n
     */
    private byte[] getMessageBytes(InetSocketAddress recipient) {
        StringBuilder buf = new StringBuilder();

        buf.append(HEADER);

        buf.append("Host: ");
        buf.append(getLiteralIP(recipient.getAddress()));
        buf.append(":");
        buf.append(recipient.getPort());
        buf.append("\r\n");

        buf.append("Port: ");
        buf.append(port);
        buf.append("\r\n");

        ids.forEach(id -> {
            buf.append("Infohash: ");
            buf.append(Protocols.toHex(id.getBytes()));
            buf.append("\r\n");
        });

        buf.append("cookie: ");
        buf.append(cookie.toString());
        buf.append("\r\n");

        buf.append("\r\n");
        buf.append("\r\n");

        return buf.toString().getBytes(ascii);
    }

    public static AnnounceMessage readFrom(ByteBuffer buffer) {
        byte[] message = new byte[buffer.remaining()];
        buffer.get(message);
        return parse(message);
    }

    private static AnnounceMessage parse(byte[] bytes) {
        Builder builder = new Builder();

        String s = new String(bytes, ascii);
        if (!s.startsWith(HEADER) && !s.endsWith(TERMINATOR)) {
            throw new IllegalStateException("Message has been truncated");
        }
        s = s.substring(HEADER.length(), s.length() - TERMINATOR.length());

        StringTokenizer tokenizer = new StringTokenizer(s, DELIMITER);
        String keyValueSeparator = ": "; // colon-space
        while (tokenizer.hasMoreTokens()) {
            String token = tokenizer.nextToken();
            int k = token.indexOf(keyValueSeparator);
            if (k <= 0 || k >= token.length() - keyValueSeparator.length() - 1) {
                throw new IllegalStateException("Invalid token: " + token);
            }
            String key = token.substring(0, k);
            String value = token.substring(k + keyValueSeparator.length(), token.length());
            switch (key) {
                case "Host": {
                    // ignore
                    break;
                }
                case "Port": {
                    builder.port(Integer.parseInt(value));
                    break;
                }
                case "cookie": {
                    try {
                        builder.cookie(Cookie.fromString(value));
                    } catch (Exception e) {
                        // unsupported cookie format -- not ours
                        builder.cookie(Cookie.unknownCookie());
                    }
                    break;
                }
                case "Infohash": {
                    builder.torrentId(TorrentId.fromBytes(Protocols.fromHex(value)));
                    break;
                }
                default: {
                    // ignore
                }
            }
        }

        return builder.build();
    }

    @Override
    public String toString() {
        return "AnnounceMessage{" +
                "cookie=" + cookie +
                ", port=" + port +
                ", ids=" + ids +
                '}';
    }

    static class Builder {
        private Cookie cookie;
        private int port;
        private Set<TorrentId> ids;

        private Builder() {
        }

        public Builder cookie(Cookie cookie) {
            if (this.cookie != null) {
                throw new IllegalStateException("Cookie already set");
            }
            this.cookie = Objects.requireNonNull(cookie);
            return this;
        }

        public Builder port(int port) {
            if (this.port > 0) {
                throw new IllegalStateException("Port already set");
            }
            if (port <= 0 || port > 65535) {
                throw new IllegalArgumentException("Invalid port number: " + port);
            }
            this.port = port;
            return this;
        }

        public Builder torrentId(TorrentId id) {
            Objects.requireNonNull(id);
            if (ids == null) {
                ids = new HashSet<>();
            }
            ids.add(id);
            return this;
        }

        AnnounceMessage build() {
            if (cookie == null) {
                throw new IllegalStateException("Can't build message: missing cookie");
            }
            if (ids == null) {
                throw new IllegalStateException("Can't build message: no torrents");
            }
            if (port == 0) {
                throw new IllegalStateException("Can't build message: missing port");
            }
            return new AnnounceMessage(cookie, port, ids);
        }
    }
}
