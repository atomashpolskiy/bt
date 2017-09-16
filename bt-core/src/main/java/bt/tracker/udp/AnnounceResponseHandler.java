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

import bt.protocol.Protocols;
import bt.tracker.CompactPeerInfo;
import bt.tracker.CompactPeerInfo.AddressType;
import bt.tracker.TrackerResponse;

import java.util.Arrays;

class AnnounceResponseHandler implements UdpTrackerResponseHandler<TrackerResponse> {

    private static final int INTERVAL_OFFSET = 0;
    private static final int LEECHERS_OFFSET = 4;
    private static final int SEEDERS_OFFSET = 8;
    private static final int PEERS_OFFSET = 12;

    private static AnnounceResponseHandler instance = new AnnounceResponseHandler();

    public static AnnounceResponseHandler handler() {
        return instance;
    }

    @Override
    public TrackerResponse onSuccess(byte[] data) {
        TrackerResponse response = TrackerResponse.ok();
        response.setInterval(Protocols.readInt(data, INTERVAL_OFFSET));
        response.setLeecherCount(Protocols.readInt(data, LEECHERS_OFFSET));
        response.setSeederCount(Protocols.readInt(data, SEEDERS_OFFSET));

        if (data.length > PEERS_OFFSET) {
            byte[] peers = Arrays.copyOfRange(data, PEERS_OFFSET, data.length);
            response.setPeers(new CompactPeerInfo(peers, AddressType.IPV4));
        }

        return response;
    }

    @Override
    public TrackerResponse onError(String message) {
        return TrackerResponse.failure(message);
    }
}
