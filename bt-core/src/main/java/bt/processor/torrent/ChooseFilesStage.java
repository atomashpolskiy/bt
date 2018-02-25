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

import bt.data.Bitfield;
import bt.data.DataDescriptor;
import bt.metainfo.Torrent;
import bt.metainfo.TorrentFile;
import bt.processor.ProcessingStage;
import bt.processor.TerminateOnErrorProcessingStage;
import bt.processor.listener.ProcessingEvent;
import bt.runtime.Config;
import bt.torrent.BitfieldBasedStatistics;
import bt.torrent.TorrentDescriptor;
import bt.torrent.TorrentRegistry;
import bt.torrent.fileselector.SelectionResult;
import bt.torrent.fileselector.TorrentFileSelector;
import bt.torrent.messaging.Assignments;
import bt.torrent.selector.IncompletePiecesValidator;
import bt.torrent.selector.PieceSelector;
import bt.torrent.selector.ValidatingSelector;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.IntStream;

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

        Set<TorrentFile> selectedFiles = new HashSet<>();
        if (context.getFileSelector().isPresent()) {
            TorrentFileSelector selector = context.getFileSelector().get();
            List<TorrentFile> files = torrent.getFiles();
            List<SelectionResult> selectionResults = selector.selectFiles(files);
            if (selectionResults.size() != files.size()) {
                throw new IllegalStateException("Invalid number of selection results");
            }
            for (int i = 0; i < files.size(); i++) {
                if (!selectionResults.get(i).shouldSkip()) {
                    selectedFiles.add(files.get(i));
                }
            }
        } else {
            selectedFiles = new HashSet<>(torrent.getFiles());
        }

        Bitfield bitfield = descriptor.getDataDescriptor().getBitfield();
        Set<Integer> validPieces = getValidPieces(descriptor.getDataDescriptor(), selectedFiles);
        PieceSelector selector = createSelector(context.getPieceSelector(), bitfield, validPieces);
        BitfieldBasedStatistics pieceStatistics = context.getPieceStatistics();
        Assignments assignments = new Assignments(bitfield, selector, pieceStatistics, config);

        updateSkippedPieces(bitfield, validPieces);
        context.setAssignments(assignments);
    }

    private void updateSkippedPieces(Bitfield bitfield, Set<Integer> validPieces) {
        IntStream.range(0, bitfield.getPiecesTotal()).forEach(pieceIndex -> {
            if (!validPieces.contains(pieceIndex)) {
                bitfield.skip(pieceIndex);
            }
        });
    }

    private Set<Integer> getValidPieces(DataDescriptor dataDescriptor, Set<TorrentFile> selectedFiles) {
        Set<Integer> validPieces = new HashSet<>();
        IntStream.range(0, dataDescriptor.getBitfield().getPiecesTotal()).forEach(pieceIndex -> {
            for (TorrentFile file : dataDescriptor.getFilesForPiece(pieceIndex)) {
                if (selectedFiles.contains(file)) {
                    validPieces.add(pieceIndex);
                    break;
                }
            }
        });
        return validPieces;
    }

    private PieceSelector createSelector(PieceSelector selector,
                                         Bitfield bitfield,
                                         Set<Integer> selectedFilesPieces) {
        Predicate<Integer> incompletePiecesValidator = new IncompletePiecesValidator(bitfield);
        Predicate<Integer> selectedFilesValidator = selectedFilesPieces::contains;
        Predicate<Integer> validator = (pieceIndex) ->
                selectedFilesValidator.test(pieceIndex) && incompletePiecesValidator.test(pieceIndex);
        return new ValidatingSelector(validator, selector);
    }

    @Override
    public ProcessingEvent after() {
        return ProcessingEvent.FILES_CHOSEN;
    }
}
