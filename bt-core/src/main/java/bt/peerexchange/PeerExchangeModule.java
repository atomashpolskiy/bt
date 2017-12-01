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

package bt.peerexchange;

import bt.module.ProtocolModule;
import bt.module.ServiceModule;
import com.google.inject.Binder;
import com.google.inject.Module;

import java.util.Objects;

public class PeerExchangeModule implements Module {

    private PeerExchangeConfig config;

    public PeerExchangeModule() {
        this.config = new PeerExchangeConfig();
    }

    public PeerExchangeModule(PeerExchangeConfig config) {
        this.config = Objects.requireNonNull(config);
    }

    @Override
    public void configure(Binder binder) {

        binder.bind(PeerExchangeConfig.class).toInstance(config);

        // explicit singleton binding is required to prevent multibinders from creating 2 instances
        // see https://github.com/google/guice/issues/791
        // also, this service contributes startup lifecycle bindings and should be instantiated eagerly
        binder.bind(PeerExchangePeerSourceFactory.class).asEagerSingleton();

        ServiceModule.extend(binder).addPeerSourceFactory(PeerExchangePeerSourceFactory.class);
        ServiceModule.extend(binder).addMessagingAgentType(PeerExchangePeerSourceFactory.class);
        ProtocolModule.extend(binder).addExtendedMessageHandler("ut_pex", PeerExchangeMessageHandler.class);
    }
}
