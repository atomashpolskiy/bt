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

import bt.metainfo.TorrentId;
import bt.processor.ProcessingStage;
import bt.processor.TerminateOnErrorProcessingStage;
import bt.processor.listener.ProcessingEvent;
import bt.torrent.TorrentDescriptor;
import bt.torrent.TorrentRegistry;

public class SeedStage<C extends TorrentContext> extends TerminateOnErrorProcessingStage<C> {

    private TorrentRegistry torrentRegistry;

    public SeedStage(ProcessingStage<C> next,
                     TorrentRegistry torrentRegistry) {
        super(next);
        this.torrentRegistry = torrentRegistry;
    }

    @Override
    protected void doExecute(C context) {
        TorrentDescriptor descriptor = getDescriptor(context.getTorrentId().get());

        while (descriptor.isActive()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException("Unexpectedly interrupted", e);
            }
        }
    }

    private TorrentDescriptor getDescriptor(TorrentId torrentId) {
        return torrentRegistry.getDescriptor(torrentId)
                .orElseThrow(() -> new IllegalStateException("No descriptor present for torrent ID: " + torrentId));
    }

    @Override
    public ProcessingEvent after() {
        return null;
    }
}
