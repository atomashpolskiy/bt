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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Set;

class LocalServiceDiscoveryAnnouncer {

    private final ByteBuffer sendBuffer;

    private final AnnounceGroupChannel channel;
    private final Cookie cookie;
    private final Set<Integer> localPorts;

    public LocalServiceDiscoveryAnnouncer(
            AnnounceGroupChannel channel,
            Cookie cookie,
            Set<Integer> localPorts,
            LocalServiceDiscoveryConfig config) {

        this.sendBuffer = createBuffer(config);

        this.channel = channel;
        this.cookie = cookie;
        this.localPorts = localPorts;
    }

    private static ByteBuffer createBuffer(LocalServiceDiscoveryConfig config) {
        int maxMessageSize = AnnounceMessage.calculateMessageSize(config.getLocalServiceDiscoveryMaxTorrentsPerAnnounce());
        return ByteBuffer.allocateDirect(maxMessageSize * 2);
    }

    public AnnounceGroup getGroup() {
        return channel.getGroup();
    }

    public void announce(Collection<TorrentId> ids) throws IOException {
        for (Integer port : localPorts) {
            AnnounceMessage message = buildMessage(port, ids);

            sendBuffer.clear();
            message.writeTo(sendBuffer, channel.getGroup().getAddress());
            sendBuffer.flip();

            try {
                channel.send(sendBuffer);
            } catch (IOException e) {
                channel.closeQuietly();
                throw e;
            }
        }
    }

    private AnnounceMessage buildMessage(int port, Collection<TorrentId> ids) {
        AnnounceMessage.Builder builder = AnnounceMessage.builder().cookie(cookie).port(port);
        ids.forEach(builder::torrentId);
        return builder.build();
    }
}
