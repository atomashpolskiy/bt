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

import bt.metainfo.Torrent;
import bt.processor.ProcessingStage;
import bt.processor.TerminateOnErrorProcessingStage;
import bt.processor.listener.ProcessingEvent;

public class FetchTorrentStage extends TerminateOnErrorProcessingStage<TorrentContext> {

    public FetchTorrentStage(ProcessingStage<TorrentContext> next) {
        super(next);
    }

    @Override
    protected void doExecute(TorrentContext context) {
        Torrent torrent = context.getTorrentSupplier().get();
        context.setTorrentId(torrent.getTorrentId());
        context.setTorrent(torrent);
    }

    @Override
    public ProcessingEvent after() {
        return ProcessingEvent.TORRENT_FETCHED;
    }
}
