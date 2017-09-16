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
import bt.net.PeerId;
import bt.protocol.Protocols;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Objects;

class AnnounceRequest extends UdpTrackerMessage {

    private static final int ANNOUNCE_TYPE_ID = 1;

    public enum EventType {

        QUERY(0), COMPLETE(1), START(2), STOP(3);

        private int code;

        EventType(int code) {
            this.code = code;
        }

        public int code() {
            return code;
        }
    }

    private TorrentId torrentId;
    private PeerId peerId;
    private long downloaded;
    private long left;
    private long uploaded;
    private int numwant;
    private EventType eventType;
    private short listeningPort;
    private String requestString;

    public AnnounceRequest() {
        super(ANNOUNCE_TYPE_ID);
    }

    public void setTorrentId(TorrentId torrentId) {
        this.torrentId = torrentId;
    }

    public void setPeerId(PeerId peerId) {
        this.peerId = peerId;
    }

    public void setDownloaded(long downloaded) {
        this.downloaded = downloaded;
    }

    public void setLeft(long left) {
        this.left = left;
    }

    public void setUploaded(long uploaded) {
        this.uploaded = uploaded;
    }

    public void setNumwant(int numwant) {
        this.numwant = numwant;
    }

    public void setEventType(EventType eventType) {
        this.eventType = eventType;
    }

    public void setListeningPort(short listeningPort) {
        this.listeningPort = listeningPort;
    }

    public void setRequestString(String requestString) {
        this.requestString = requestString;
    }

    @Override
    protected void writeBodyTo(OutputStream out) throws IOException {
        out.write(Objects.requireNonNull(torrentId).getBytes());
        out.write(Objects.requireNonNull(peerId).getBytes());
        out.write(Protocols.getLongBytes(downloaded));
        out.write(Protocols.getLongBytes(left));
        out.write(Protocols.getLongBytes(uploaded));
        out.write(Protocols.getIntBytes(Objects.requireNonNull(eventType).code()));
        out.write(Protocols.getIntBytes(0)); // local ip
        out.write(Protocols.getIntBytes(0)); // secret key
        out.write(Protocols.getIntBytes(numwant)); // numwant
        out.write(Protocols.getShortBytes(listeningPort));

        // extensions
        if (requestString != null) {
            out.write(0b0000000000000010);

            byte[] bytes = requestString.getBytes("ISO-8859-1");
            if (bytes.length > 255) {
                bytes = Arrays.copyOfRange(bytes, 0, 255);
            }
            out.write(bytes.length);
            out.write(bytes);
        } else {
            out.write(Protocols.getShortBytes(0));
        }
    }

    @Override
    public String toString() {
        return "AnnounceRequest{" +
                "id=" + getId() +
                ", torrentId=" + torrentId +
                ", peerId=" + peerId +
                ", downloaded=" + downloaded +
                ", left=" + left +
                ", uploaded=" + uploaded +
                ", eventType=" + eventType + (eventType == null ? "" : "(" + eventType.code + ")") +
                ", listeningPort=" + listeningPort +
                (requestString == null ? "" : ", requestString=" + requestString) +
                '}';
    }
}
