/*
 * Copyright (c) 2016—2018 Andrei Tomashpolskiy and individual contributors.
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

import bt.data.LocalBitfield;
import bt.metainfo.Torrent;
import bt.processor.ProcessingStage;
import bt.processor.TerminateOnErrorProcessingStage;
import bt.processor.listener.ProcessingEvent;
import bt.runtime.Config;
import bt.torrent.BitfieldBasedStatistics;
import bt.torrent.TorrentDescriptor;
import bt.torrent.TorrentRegistry;
import bt.torrent.messaging.Assignments;
import bt.torrent.selector.PrioritizedPieceSelector;
import bt.torrent.selector.ValidatingSelector;

public class ChooseFilesStage<C extends TorrentContext> extends TerminateOnErrorProcessingStage<C> {
    private TorrentRegistry torrentRegistry;
    private Config config;

    public ChooseFilesStage(ProcessingStage<C> next,
                            TorrentRegistry torrentRegistry,
                            Config config) {
        super(next);
        this.torrentRegistry = torrentRegistry;
        this.config = config;
    }

    @Override
    protected void doExecute(C context) {
        Torrent torrent = context.getTorrent().get();
        TorrentDescriptor descriptor = torrentRegistry.getDescriptor(torrent.getTorrentId()).get();

        FilePiecePriorityMapper piecePriorityMapper = FilePiecePriorityMapper.createPiecePriorityMapper(
                descriptor.getDataDescriptor(), torrent.getFiles(), context.getFileSelector().orElse(null));

        LocalBitfield bitfield = descriptor.getDataDescriptor().getBitfield();
        ValidatingSelector selector = wrapAndInitSelector(context.getPieceSelector(), bitfield, piecePriorityMapper);
        BitfieldBasedStatistics pieceStatistics = context.getPieceStatistics();
        Assignments assignments = new Assignments(bitfield, selector, pieceStatistics, config);

        bitfield.setSkippedPieces(piecePriorityMapper.getSkippedPieces());
        context.setAllNonSkippedFiles(piecePriorityMapper.getAllFilesToDownload());
        context.setAssignments(assignments);
    }

    private ValidatingSelector wrapAndInitSelector(PrioritizedPieceSelector selector,
                                                   LocalBitfield localBitfield,
                                                   FilePiecePriorityMapper piecePriorityMapper) {
        selector.initSelector(localBitfield.getPiecesTotal());
        // only set the priority of the pieces if they were not yet set. If they have been set it means
        // that something externally updated pieces priority and we shouldn't overwrite it at this stage.
        selector.setHighPriorityPiecesIfNull(piecePriorityMapper.getHighPriorityPieces());
        return new ValidatingSelector(localBitfield, piecePriorityMapper.getSkippedPieces(), selector);
    }

    @Override
    public ProcessingEvent after() {
        return ProcessingEvent.FILES_CHOSEN;
    }
}
