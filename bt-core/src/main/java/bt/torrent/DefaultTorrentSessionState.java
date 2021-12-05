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

package bt.torrent;

import bt.data.DataDescriptor;
import bt.metainfo.TorrentFile;
import bt.net.ConnectionKey;
import bt.processor.ProcessingContext;
import bt.processor.torrent.FilePiecePriorityMapper;
import bt.torrent.fileselector.FilePrioritySelector;
import bt.torrent.messaging.ConnectionState;
import bt.torrent.messaging.TorrentWorker;
import bt.torrent.selector.PrioritizedPieceSelector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static java.util.stream.Collectors.summingLong;

public class DefaultTorrentSessionState implements TorrentSessionState {

    /**
     * Recently calculated amounts of downloaded and uploaded data
     */
    private final Map<ConnectionKey, TransferAmounts> recentAmountsForConnectedPeers;

    /**
     * Historical data (amount of data downloaded from disconnected peers)
     */
    private final AtomicLong downloadedFromDisconnected;

    /**
     * Historical data (amount of data uploaded to disconnected peers)
     */
    private final AtomicLong uploadedToDisconnected;

    private final Supplier<DataDescriptor> descriptor;
    private final TorrentWorker worker;
    private final PrioritizedPieceSelector pieceSelector;

    public DefaultTorrentSessionState(Supplier<DataDescriptor> descriptor, TorrentWorker worker,
                                      PrioritizedPieceSelector pieceSelector) {
        this.recentAmountsForConnectedPeers = new HashMap<>();
        this.downloadedFromDisconnected = new AtomicLong();
        this.uploadedToDisconnected = new AtomicLong();
        this.descriptor = descriptor;
        this.worker = worker;
        this.pieceSelector = pieceSelector;
    }

    @Override
    public int getPiecesTotal() {
        if (descriptor.get() != null) {
            return descriptor.get().getBitfield().getPiecesTotal();
        } else {
            return 1;
        }
    }

    @Override
    public int getPiecesComplete() {
        if (descriptor.get() != null) {
            return descriptor.get().getBitfield().getPiecesComplete();
        } else {
            return 0;
        }
    }

    @Override
    public int getPiecesIncomplete() {
        if (descriptor.get() != null) {
            return descriptor.get().getBitfield().getPiecesIncomplete();
        } else {
            return 1;
        }
    }

    @Override
    public int getPiecesRemaining() {
        if (descriptor.get() != null) {
            return descriptor.get().getBitfield().getPiecesRemaining();
        } else {
            return 1;
        }
    }

    @Override
    public int getPiecesSkipped() {
        if (descriptor.get() != null) {
            return descriptor.get().getBitfield().getPiecesSkipped();
        } else {
            return 0;
        }
    }

    @Override
    public int getPiecesNotSkipped() {
        if (descriptor.get() != null) {
            return descriptor.get().getBitfield().getPiecesNotSkipped();
        } else {
            return 1;
        }
    }

    @Override
    public synchronized long getDownloaded() {
        long downloaded = getCurrentAmounts().values().stream().collect(summingLong(TransferAmounts::getDownloaded));
        downloaded += downloadedFromDisconnected.get();
        return downloaded;
    }

    @Override
    public synchronized long getUploaded() {
        long uploaded = getCurrentAmounts().values().stream().collect(summingLong(TransferAmounts::getUploaded));
        uploaded += uploadedToDisconnected.get();
        return uploaded;
    }

    @Override
    public long getLeft() {
        if (descriptor.get() != null) {
            return descriptor.get().getLeft();
        }
        return UNKNOWN;
    }

    @Override
    public boolean startedAsSeed() {
        return descriptor.get().startedAsSeed();
    }

    private synchronized Map<ConnectionKey, TransferAmounts> getCurrentAmounts() {
        Map<ConnectionKey, TransferAmounts> connectedPeers = getAmountsForConnectedPeers();

        Set<ConnectionKey> disconnectedPeers = new HashSet<>();
        recentAmountsForConnectedPeers.forEach((peer, amounts) -> {
            if (!connectedPeers.containsKey(peer)) {
                downloadedFromDisconnected.addAndGet(amounts.getDownloaded());
                uploadedToDisconnected.addAndGet(amounts.getUploaded());
                disconnectedPeers.add(peer);
            }
        });
        recentAmountsForConnectedPeers.keySet().removeAll(disconnectedPeers);

        recentAmountsForConnectedPeers.putAll(connectedPeers);

        return recentAmountsForConnectedPeers;
    }

    private Map<ConnectionKey, TransferAmounts> getAmountsForConnectedPeers() {
        return worker.getPeers().stream()
                .collect(
                        HashMap::new,
                        (acc, peer) -> {
                            ConnectionState connectionState = worker.getConnectionState(peer);
                            acc.put(
                                    peer,
                                    new TransferAmounts(connectionState.getDownloaded(), connectionState.getUploaded())
                            );
                        },
                        HashMap::putAll);
    }

    @Override
    public Set<ConnectionKey> getConnectedPeers() {
        return worker.getPeers();
    }

    @Override
    public boolean updateFileDownloadPriority(ProcessingContext c, FilePrioritySelector prioritySelector) {
        final Optional<List<TorrentFile>> allNonSkippedFiles = c.getAllNonSkippedFiles();
        if (allNonSkippedFiles.isPresent()) {
            FilePiecePriorityMapper piecePriorityMapper = FilePiecePriorityMapper.createPiecePriorityMapper(
                    descriptor.get(), allNonSkippedFiles.get(), Objects.requireNonNull(prioritySelector));
            pieceSelector.setHighPriorityPieces(piecePriorityMapper.getHighPriorityPieces());
            return true;
        }
        return false;
    }

    private static class TransferAmounts {
        private final long downloaded;
        private final long uploaded;

        public TransferAmounts(long downloaded, long uploaded) {
            this.downloaded = downloaded;
            this.uploaded = uploaded;
        }

        public long getDownloaded() {
            return downloaded;
        }

        public long getUploaded() {
            return uploaded;
        }
    }
}
