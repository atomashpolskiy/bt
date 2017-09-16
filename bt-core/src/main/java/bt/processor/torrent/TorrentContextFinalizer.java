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

package bt.processor.torrent;

import bt.processor.ContextFinalizer;
import bt.torrent.TorrentDescriptor;
import bt.torrent.TorrentRegistry;
import bt.torrent.TrackerAnnouncer;

public class TorrentContextFinalizer<C extends TorrentContext> implements ContextFinalizer<C> {

    private TorrentRegistry torrentRegistry;

    public TorrentContextFinalizer(TorrentRegistry torrentRegistry) {
        this.torrentRegistry = torrentRegistry;
    }

    @Override
    public void finalizeContext(C context) {
        context.getTorrentId().ifPresent(torrentId -> {
            torrentRegistry.getDescriptor(torrentId).ifPresent(TorrentDescriptor::stop);
        });
        context.getAnnouncer().ifPresent(TrackerAnnouncer::stop);
    }
}
