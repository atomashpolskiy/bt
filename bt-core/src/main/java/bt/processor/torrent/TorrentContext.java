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

import bt.data.LocalBitfield;
import bt.data.Storage;
import bt.metainfo.Torrent;
import bt.metainfo.TorrentFile;
import bt.metainfo.TorrentId;
import bt.processor.ProcessingContext;
import bt.torrent.BitfieldBasedStatistics;
import bt.torrent.TorrentSessionState;
import bt.torrent.TrackerAnnouncer;
import bt.torrent.callbacks.FileDownloadCompleteCallback;
import bt.torrent.fileselector.FilePrioritySkipSelector;
import bt.torrent.messaging.Assignments;
import bt.torrent.messaging.MessageRouter;
import bt.torrent.selector.PieceSelector;
import bt.torrent.selector.PrioritizedPieceSelector;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Accumulates data, that is specific to standard torrent download/upload.
 *
 * @since 1.3
 */
public class TorrentContext implements ProcessingContext {

    private final PrioritizedPieceSelector pieceSelector;
    private final FilePrioritySkipSelector fileSelector; // nullable
    private final FileDownloadCompleteCallback fileCompletionCallback; // nullable
    private final Storage storage;
    private final Supplier<Torrent> torrentSupplier;

    /* all of these can be missing, depending on which stage is currently being executed */
    private volatile TorrentId torrentId;
    private volatile Torrent torrent;
    private volatile TorrentSessionState state;
    private volatile MessageRouter router;
    private volatile LocalBitfield bitfield;
    private volatile Assignments assignments;
    private volatile List<TorrentFile> allNonSkippedFiles;
    private volatile BitfieldBasedStatistics pieceStatistics;
    private volatile TrackerAnnouncer announcer;

    public TorrentContext(PieceSelector pieceSelector,
                          FilePrioritySkipSelector fileSelector,
                          FileDownloadCompleteCallback fileCompletionCallback,
                          Storage storage,
                          Supplier<Torrent> torrentSupplier) {
        this.pieceSelector = new PrioritizedPieceSelector(pieceSelector);
        this.fileSelector = fileSelector;
        this.fileCompletionCallback = fileCompletionCallback;
        this.storage = storage;
        this.torrentSupplier = torrentSupplier;
    }

    public TorrentContext(PieceSelector pieceSelector,
                          FilePrioritySkipSelector fileSelector,
                          Storage storage,
                          Supplier<Torrent> torrentSupplier) {
        this(pieceSelector, fileSelector, null, storage, torrentSupplier);
    }

    public TorrentContext(PieceSelector pieceSelector,
                          Storage storage,
                          Supplier<Torrent> torrentSupplier) {
        this(pieceSelector, null, null, storage, torrentSupplier);
    }

    public PrioritizedPieceSelector getPieceSelector() {
        return pieceSelector;
    }

    public Optional<FilePrioritySkipSelector> getFileSelector() {
        return Optional.ofNullable(fileSelector);
    }

    public Storage getStorage() {
        return storage;
    }

    public Supplier<Torrent> getTorrentSupplier() {
        return torrentSupplier;
    }

    public Optional<FileDownloadCompleteCallback> getFileCompletionCallback() {
        return Optional.ofNullable(fileCompletionCallback);
    }

    ///////////////////////////////////////////////

    @Override
    public Optional<TorrentId> getTorrentId() {
        return Optional.ofNullable(torrentId);
    }

    public void setTorrentId(TorrentId torrentId) {
        this.torrentId = torrentId;
    }

    @Override
    public Optional<Torrent> getTorrent() {
        return Optional.ofNullable(torrent);
    }

    @Override
    public Optional<TorrentSessionState> getState() {
        return Optional.ofNullable(state);
    }

    public void setState(TorrentSessionState state) {
        this.state = state;
    }

    public void setTorrent(Torrent torrent) {
        this.torrent = torrent;
    }

    public MessageRouter getRouter() {
        return router;
    }

    public void setRouter(MessageRouter router) {
        this.router = router;
    }

    public LocalBitfield getBitfield() {
        return bitfield;
    }

    public void setBitfield(LocalBitfield bitfield) {
        this.bitfield = bitfield;
    }

    public Assignments getAssignments() {
        return assignments;
    }

    public void setAssignments(Assignments assignments) {
        this.assignments = assignments;
    }

    public Optional<List<TorrentFile>> getAllNonSkippedFiles() {
        return Optional.ofNullable(allNonSkippedFiles);
    }

    public void setAllNonSkippedFiles(List<TorrentFile> allNonSkippedFiles) {
        this.allNonSkippedFiles = allNonSkippedFiles;
    }

    public BitfieldBasedStatistics getPieceStatistics() {
        return pieceStatistics;
    }

    public void setPieceStatistics(BitfieldBasedStatistics pieceStatistics) {
        this.pieceStatistics = pieceStatistics;
    }

    public Optional<TrackerAnnouncer> getAnnouncer() {
        return Optional.ofNullable(announcer);
    }

    public void setAnnouncer(TrackerAnnouncer announcer) {
        this.announcer = announcer;
    }
}
