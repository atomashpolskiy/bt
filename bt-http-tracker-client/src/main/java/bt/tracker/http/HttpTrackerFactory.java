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

import bt.peer.IPeerRegistry;
import bt.protocol.crypto.EncryptionPolicy;
import bt.runtime.Config;
import bt.service.IdentityService;
import bt.tracker.Tracker;
import bt.tracker.TrackerFactory;
import com.google.inject.Inject;

import java.net.InetAddress;

/**
 * Creates HTTP tracker clients.
 *
 * @since 1.0
 */
public class HttpTrackerFactory implements TrackerFactory {

    private IdentityService idService;
    private IPeerRegistry peerRegistry;
    private EncryptionPolicy encryptionPolicy;
    private InetAddress localAddress;
    private int numberOfPeersToRequestFromTracker;

    @Inject
    public HttpTrackerFactory(IdentityService idService, IPeerRegistry peerRegistry, Config config) {
        this.idService = idService;
        this.peerRegistry = peerRegistry;
        this.encryptionPolicy = config.getEncryptionPolicy();
        this.localAddress = config.getAcceptorAddress();
        this.numberOfPeersToRequestFromTracker = config.getNumberOfPeersToRequestFromTracker();
    }

    @Override
    public Tracker getTracker(String trackerUrl) {
        return new HttpTracker(trackerUrl, idService, peerRegistry, encryptionPolicy, localAddress,
                numberOfPeersToRequestFromTracker);
    }
}
