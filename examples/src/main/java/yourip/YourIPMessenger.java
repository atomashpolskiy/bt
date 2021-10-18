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

package yourip;

import bt.peer.IPeerRegistry;
import bt.protocol.Message;
import bt.torrent.annotation.Consumes;
import bt.torrent.annotation.Produces;
import bt.torrent.messaging.ExtensionConnectionState;
import bt.torrent.messaging.MessageContext;
import com.google.inject.Inject;

import java.util.function.Consumer;

public class YourIPMessenger {

    private final IPeerRegistry peerRegistry;

    @Inject
    public YourIPMessenger(IPeerRegistry peerRegistry) {
        this.peerRegistry = peerRegistry;
    }

    @Consumes
    public void consume(YourIP message, MessageContext context) {
        System.out.println("I am " + peerRegistry.getLocalPeer() +
                ", for peer " + context.getPeer() + " my external address is " + message.getAddress());
    }

    @Produces
    public void produce(Consumer<Message> messageConsumer, MessageContext context) {
        YourIPState yourIPState = context.getConnectionState().getOrBuildExtensionState(YourIPState.class);
        if (context.getPeer().supportsExtension(YourIP.id()) && !yourIPState.known) {
            String address = context.getPeer().getInetAddress().toString() +
                    ":" + context.getConnectionKey().getRemotePort();
            messageConsumer.accept(new YourIP(address));
            yourIPState.known = true;
        }
    }

    public static class YourIPState implements ExtensionConnectionState {
        private boolean known = false;
    }
}
