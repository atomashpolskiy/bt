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

package bt.data;

import bt.metainfo.Torrent;
import bt.metainfo.TorrentFile;
import bt.torrent.callbacks.FileDownloadCompleteCallback;
import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * <p><b>Note that this class is not a part of the public API and is a subject to change.</b></p>
 */
class DefaultDataDescriptor implements DataDescriptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultDataDescriptor.class);

    private final Torrent torrent;
    private final Storage storage;
    private final DataReader reader;
    private final ChunkVerifier verifier;

    private final FileDownloadCompleteCallback fileCompletionCallback;

    private List<ChunkDescriptor> chunkDescriptors;
    private LocalBitfield bitfield;

    private List<List<TorrentFile>> filesForPieces;
    private Collection<StorageUnit> storageUnits;

    public DefaultDataDescriptor(Storage storage,
                                 Torrent torrent,
                                 FileDownloadCompleteCallback fileCompletionCallback,
                                 ChunkVerifier verifier,
                                 DataReaderFactory dataReaderFactory,
                                 int transferBlockSize) {
        this.storage = storage;
        this.torrent = torrent;
        this.fileCompletionCallback = fileCompletionCallback;
        this.verifier = verifier;

        init(transferBlockSize);

        this.reader = dataReaderFactory.createReader(torrent, this);
    }

    private void init(long transferBlockSize) {
        List<TorrentFile> files = torrent.getFiles();

        long totalSize = torrent.getSize();
        long chunkSize = torrent.getChunkSize();

        transferBlockSize = Math.min(transferBlockSize, chunkSize);

        int chunksTotal = PieceUtils.calculateNumberOfChunks(totalSize, chunkSize);
        List<List<TorrentFile>> pieceNumToFile = new ArrayList<>(chunksTotal);

        final Map<StorageUnit, TorrentFile> storageUnitsToFilesMap = buildStorageUnitToFilesMap(files);

        // filter out empty files (and create them at once)
        List<StorageUnit> nonEmptyStorageUnits = handleEmptyStorageUnits(storageUnitsToFilesMap);

        List<ChunkDescriptor> chunks = PieceUtils
                .buildChunkDescriptors(torrent, transferBlockSize, totalSize, chunkSize, chunksTotal, pieceNumToFile,
                        storageUnitsToFilesMap, nonEmptyStorageUnits);

        List<List<CompletableTorrentFile>> countdownTorrentFiles =
                createListOfCountdownFiles(torrent.getFiles(), pieceNumToFile);


        this.bitfield = buildBitfield(chunks, countdownTorrentFiles);
        this.chunkDescriptors = chunks;
        this.storageUnits = nonEmptyStorageUnits;
        this.filesForPieces = pieceNumToFile;
    }

    private Map<StorageUnit, TorrentFile> buildStorageUnitToFilesMap(List<TorrentFile> files) {
        final Map<StorageUnit, TorrentFile> storageUnitsToFilesMap =
                Maps.newLinkedHashMapWithExpectedSize(files.size());
        files.forEach(f -> storageUnitsToFilesMap.put(storage.getUnit(torrent, f), f));
        return Collections.unmodifiableMap(storageUnitsToFilesMap);
    }

    private List<List<CompletableTorrentFile>> createListOfCountdownFiles(List<TorrentFile> allFiles,
                                                                          List<List<TorrentFile>> chunkToTorrentFile) {
        Map<TorrentFile, Long> torrentFileToPieceCount = chunkToTorrentFile.stream()
                .flatMap(List::stream)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        Map<TorrentFile, CompletableTorrentFile> tfToCountingTf = allFiles.stream()
                .collect(Collectors.toMap(
                        Function.identity(),
                        tf -> new CompletableTorrentFile(tf, torrentFileToPieceCount.getOrDefault(tf, 0L), null)
                ));
        return chunkToTorrentFile.stream()
                .map(list -> list.stream()
                        .map(tfToCountingTf::get)
                        .collect(Collectors.toList()))
                .collect(Collectors.toList());
    }

    /**
     * Create empty files for each empty storage unit. Return the non-empty storage units in a list
     *
     * @param storageUnitsToFilesMap the storage units to process
     * @return the list of non-empty storage units
     */
    private List<StorageUnit> handleEmptyStorageUnits(Map<StorageUnit, TorrentFile> storageUnitsToFilesMap) {
        List<StorageUnit> nonEmptyStorageUnits = new ArrayList<>();
        for (StorageUnit unit : storageUnitsToFilesMap.keySet()) {
            if (unit.capacity() > 0) {
                nonEmptyStorageUnits.add(unit);
            } else {
                if (!unit.createEmpty()) {
                    throw new IllegalStateException("Failed to initialize storage unit: " + unit);
                }
                // close these units after file creation - no reason to keep them open
                try {
                    unit.close();
                } catch (IOException ex) {
                    throw new UncheckedIOException("Failed to close storage unit after initialization: " + unit, ex);
                }
            }
        }
        return Collections.unmodifiableList(nonEmptyStorageUnits);
    }

    private LocalBitfield buildBitfield(List<ChunkDescriptor> chunks,
                                        List<List<CompletableTorrentFile>> chunkToCountdownFiles) {
        LocalBitfield bitfield = new LocalBitfield(chunks.size(), chunkToCountdownFiles) {
            @Override
            protected void fileFinishedCallback(TorrentFile tf) {
                if (fileCompletionCallback != null) {
                    fileCompletionCallback.fileDownloadCompleted(torrent, tf, storage);
                }
            }
        };

        verifier.verify(chunks, bitfield);
        return bitfield;
    }

    @Override
    public List<ChunkDescriptor> getChunkDescriptors() {
        return chunkDescriptors;
    }

    @Override
    public LocalBitfield getBitfield() {
        return bitfield;
    }

    @Override
    public List<TorrentFile> getFilesForPiece(int pieceIndex) {
        if (pieceIndex < 0 || pieceIndex >= bitfield.getPiecesTotal()) {
            throw new IllegalArgumentException("Invalid piece index: " + pieceIndex +
                    ", expected 0.." + bitfield.getPiecesTotal());
        }
        return filesForPieces.get(pieceIndex);
    }

    @Override
    public BitSet getAllPiecesForFiles(Set<TorrentFile> files) {
        return getPieces(pieceFiles -> pieceFiles.stream().anyMatch(files::contains));
    }

    @Override
    public BitSet getPiecesWithOnlyFiles(Set<TorrentFile> files) {
        return getPieces(files::containsAll);
    }

    private BitSet getPieces(Predicate<List<TorrentFile>> includePredicate) {
        BitSet ret = new BitSet(bitfield.getPiecesTotal());
        for (int i = 0; i < bitfield.getPiecesTotal(); i++) {
            if (includePredicate.test(filesForPieces.get(i))) {
                ret.set(i);
            }
        }
        return ret;
    }

    @Override
    public DataReader getReader() {
        return reader;
    }

    @Override
    public void waitForAllPieces() throws InterruptedException {
        bitfield.waitForAllPieces();
    }

    @Override
    public void close() {
        storageUnits.forEach(unit -> {
            try {
                unit.close();
            } catch (Exception e) {
                LOGGER.error("Failed to close storage unit: " + unit);
            }
        });
    }

    @Override
    public String toString() {
        return this.getClass().getName() + " <" + torrent.getName() + ">";
    }
}
