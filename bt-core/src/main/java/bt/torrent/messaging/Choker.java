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

package bt.torrent.messaging;

import bt.protocol.Choke;
import bt.protocol.Message;
import bt.protocol.Unchoke;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Implements basic choking/unchoking strategy, that avoids "fibrillation"
 * (quick subsequent chokes and unchokes for the same connection).
 *
 * @since 1.0
 */
class Choker {

    private static final Duration CHOKING_THRESHOLD = Duration.ofMillis(10000);

    private static final Choker instance = new Choker();

    /**
     * @return Choker instance
     * @since 1.0
     */
    public static Choker choker() {
        return instance;
    }

    /**
     * Inspects connection state and yields choke/unchoke messages when appropriate.
     *
     * @param connectionState Connection state for the choker
     *                        to inspect and update choked/unchoked status.
     * @param messageConsumer Message worker
     * @since 1.0
     */
    public void handleConnection(ConnectionState connectionState, Consumer<Message> messageConsumer) {

        Optional<Boolean> shouldChokeOptional = connectionState.getShouldChoke();
        boolean choking = connectionState.isChoking();
        boolean peerInterested = connectionState.isPeerInterested();

        if (!shouldChokeOptional.isPresent()) {
            if (peerInterested && choking) {
                if (mightUnchoke(connectionState)) {
                    shouldChokeOptional = Optional.of(Boolean.FALSE); // should unchoke
                }
            } else if (!peerInterested && !choking) {
                shouldChokeOptional = Optional.of(Boolean.TRUE);
            }
        }

        shouldChokeOptional.ifPresent(shouldChoke -> {
            if (shouldChoke != choking) {
                if (shouldChoke) {
                    // choke immediately
                    connectionState.setChoking(true);
                    messageConsumer.accept(Choke.instance());
                    connectionState.setLastChoked(System.currentTimeMillis());
                } else if (mightUnchoke(connectionState)) {
                    connectionState.setChoking(false);
                    messageConsumer.accept(Unchoke.instance());
                }
            }
        });
    }

    private boolean mightUnchoke(ConnectionState connectionState) {
        // unchoke depending on last choked time to avoid fibrillation
        return System.currentTimeMillis() - connectionState.getLastChoked() >= CHOKING_THRESHOLD.toMillis();
    }
}
