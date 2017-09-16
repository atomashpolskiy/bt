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

import bt.BtException;
import bt.protocol.Protocols;

class ConnectResponseHandler implements UdpTrackerResponseHandler<Session> {

    private static final ConnectResponseHandler instance = new ConnectResponseHandler();

    public static ConnectResponseHandler handler() {
        return instance;
    }

    @Override
    public Session onSuccess(byte[] data) {
        return new Session(Protocols.readLong(data, 0));
    }

    @Override
    public Session onError(String message) {
        throw new BtException("Tracker returned error for connect request: " + message);
    }
}
