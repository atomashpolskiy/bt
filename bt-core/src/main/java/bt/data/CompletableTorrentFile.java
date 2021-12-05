/*
 * Copyright (c) 2016—2021 Andrei Tomashpolskiy and individual contributors.
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

package bt.data;

import bt.metainfo.TorrentFile;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * A class that keeps track of how many more chunks must complete before this file finishes.
 */
public class CompletableTorrentFile {
    private final TorrentFile torrentFile;
    private final AtomicLong countdown;
    private final Consumer<TorrentFile> callback;

    public CompletableTorrentFile(TorrentFile tf, long numPieces, Consumer<TorrentFile> callback) {
        this.torrentFile = Objects.requireNonNull(tf);
        if (numPieces < 0) {
            throw new IllegalArgumentException();
        }
        this.countdown = new AtomicLong(numPieces);
        this.callback = callback;
    }

    /**
     * Countdown the number of chunks needed by 1. Returns true if this chunk has finished
     *
     * @return true iff this chunk is finished
     */
    public boolean countdown() {
        long newCountNeeded = countdown.decrementAndGet();

        if (newCountNeeded < 0)
            throw new IllegalStateException();

        return newCountNeeded == 0;
    }

    public TorrentFile getTorrentFile() {
        return torrentFile;
    }
}
