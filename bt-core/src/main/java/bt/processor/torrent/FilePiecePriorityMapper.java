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

package bt.processor.torrent;

import bt.data.DataDescriptor;
import bt.metainfo.TorrentFile;
import bt.torrent.fileselector.FilePriority;
import bt.torrent.fileselector.FilePrioritySelector;
import bt.torrent.fileselector.FilePrioritySkipSelector;
import bt.torrent.fileselector.TorrentFileSelector;
import bt.torrent.fileselector.UpdatedFilePriority;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A class which creates BitSets of the pieces that are skipped or high priority from the list of files that are
 * prioritized and skipped in a torrent. It also creates a list of all of the files that are not skipped
 */
public class FilePiecePriorityMapper {
    private final List<TorrentFile> allFilesToDownload;
    private final BitSet skippedPieces;
    private final BitSet highPriorityPieces;

    /**
     * Create a new File Pieces priority mapper from a priority updater.
     * Note: skipped pieces are NOT computed when a mapper is constructed this way.
     *
     * @param dataDescriptor the data descriptor of the torrent
     * @param files          the files that are being downloaded
     * @param selector       the selector with the updated priority information
     * @return the class with the priority mapping
     */
    public static FilePiecePriorityMapper createPiecePriorityMapper(DataDescriptor dataDescriptor,
                                                                    List<TorrentFile> files,
                                                                    FilePrioritySelector selector) {
        final List<FilePriority> selectionResults = files.stream()
                .map(selector::prioritize)
                .map(UpdatedFilePriority::toSelectionResult)
                .collect(Collectors.toList());

        if (selectionResults.size() != files.size()) {
            throw new IllegalStateException("Invalid number of selection results");
        }

        return createPiecePriorityMapper(dataDescriptor, files, selectionResults);
    }

    /**
     * Create a new File Pieces priority mapper from a choosing file selector
     *
     * @param dataDescriptor the data descriptor of the torrent.
     * @param files          the files that are being downloaded.
     * @param selector       the selector with the updated information. nullable
     * @return the class with the priority mapping. Skipped files and
     */
    public static FilePiecePriorityMapper createPiecePriorityMapper(DataDescriptor dataDescriptor,
                                                                    List<TorrentFile> files,
                                                                    FilePrioritySkipSelector selector) {
        if (selector != null) {
            List<FilePriority> filePriorities = getFilePriorities(files, selector);
            if (filePriorities.size() != files.size()) {
                throw new IllegalStateException("Invalid number of selection results");
            }

            return createPiecePriorityMapper(dataDescriptor, files, filePriorities);
        }
        return createPiecePriorityMapper(dataDescriptor, Collections.emptySet(), Collections.emptySet(),
                Collections.unmodifiableList(new ArrayList<>(files)));
    }

    private static List<FilePriority> getFilePriorities(List<TorrentFile> files, FilePrioritySkipSelector selector) {
        // keep backwards compatibility
        if (selector instanceof TorrentFileSelector) {
            return ((TorrentFileSelector) selector).selectFiles(files).stream()
                    .map(r -> r.shouldSkip() ? FilePriority.SKIP : FilePriority.NORMAL_PRIORITY)
                    .collect(Collectors.toList());
        }
        return files.stream().map(selector::prioritize).collect(Collectors.toList());
    }

    private static FilePiecePriorityMapper createPiecePriorityMapper(DataDescriptor dataDescriptor,
                                                                     List<TorrentFile> files,
                                                                     List<FilePriority> filePriorities) {
        List<TorrentFile> allFilesToDownload = new ArrayList<>();
        Set<TorrentFile> skippedFiles = new HashSet<>();
        Set<TorrentFile> highPriorityFiles = new HashSet<>();
        populateFileStatusSets(skippedFiles, highPriorityFiles, allFilesToDownload, files, filePriorities);
        allFilesToDownload = Collections.unmodifiableList(allFilesToDownload);
        return createPiecePriorityMapper(dataDescriptor, skippedFiles, highPriorityFiles, allFilesToDownload);
    }

    private static FilePiecePriorityMapper createPiecePriorityMapper(DataDescriptor dataDescriptor,
                                                                     Set<TorrentFile> skippedFile,
                                                                     Set<TorrentFile> highPriorityFiles,
                                                                     List<TorrentFile> allFilesToDownload) {
        BitSet skippedPieces = skippedFile.isEmpty() ?
                new BitSet(0) : dataDescriptor.getPiecesWithOnlyFiles(skippedFile);
        BitSet highPriorityPieces = highPriorityFiles.isEmpty() ?
                new BitSet(0) : dataDescriptor.getAllPiecesForFiles(highPriorityFiles);
        return new FilePiecePriorityMapper(skippedPieces, highPriorityPieces, allFilesToDownload);
    }

    private static void populateFileStatusSets(Set<TorrentFile> skippedFiles,
                                               Set<TorrentFile> highPriorityFiles,
                                               List<TorrentFile> filesToDownload,
                                               List<TorrentFile> files,
                                               List<FilePriority> filePriorities) {
        for (int i = 0; i < files.size(); i++) {
            final TorrentFile currFile = files.get(i);
            switch (filePriorities.get(i)) {
                case SKIP:
                    skippedFiles.add(currFile);
                    break;
                case HIGH_PRIORITY:
                    highPriorityFiles.add(currFile);
                case NORMAL_PRIORITY:
                    filesToDownload.add(currFile);
                    break;
                default:
                    throw new IllegalStateException("Unknown result " + filePriorities.get(i));
            }
        }
    }

    private FilePiecePriorityMapper(BitSet skippedPieces, BitSet highPriorityPieces,
                                    List<TorrentFile> allFilesToDownload) {
        this.skippedPieces = skippedPieces;
        this.highPriorityPieces = highPriorityPieces;
        this.allFilesToDownload = allFilesToDownload;
    }

    /**
     * Get the BitSet of pieces that were marked as skipped from this priority mapper
     *
     * @return the BitSet of pieces that are skipped
     */
    public BitSet getSkippedPieces() {
        return skippedPieces;
    }

    /**
     * Get the BitSet of pieces that were marked as high priority from this priority mapper
     *
     * @return the BitSet of pieces that are high priority pieces
     */
    public BitSet getHighPriorityPieces() {
        return highPriorityPieces;
    }

    /**
     * Return the list of all files that are downloaded (not skipped)
     *
     * @return the list of all files that are downloaded (not skipped)
     */
    public List<TorrentFile> getAllFilesToDownload() {
        return allFilesToDownload;
    }
}
