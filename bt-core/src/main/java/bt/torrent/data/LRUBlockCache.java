/*
 * Copyright (c) 2016—2019 Andrei Tomashpolskiy and individual contributors.
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

package bt.torrent.data;

import bt.data.DataRange;
import bt.event.EventSource;
import bt.metainfo.Torrent;
import bt.metainfo.TorrentId;
import bt.torrent.TorrentRegistry;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.*;

public class LRUBlockCache implements BlockCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(LRUBlockCache.class);

    private static final int MIN_SLOTS_COUNT = 20;

    private final TorrentRegistry torrentRegistry;
    private final Map<TorrentId, ByteBuffer> buffers;
    private final Map<TorrentId, List<Slot>> lruSlots;
    private final Map<TorrentId, Map<Integer, Slot>> slotByPieceIndexMap;

    @Inject
    public LRUBlockCache(TorrentRegistry torrentRegistry, EventSource eventSource) {
        this.torrentRegistry = torrentRegistry;
        this.buffers = new HashMap<>();
        this.lruSlots = new HashMap<>();
        this.slotByPieceIndexMap = new HashMap<>();

        eventSource.onTorrentStarted(e -> initializeBuffer(e.getTorrentId()));
        eventSource.onTorrentStopped(e -> releaseBuffer(e.getTorrentId()));
    }

    private synchronized void initializeBuffer(TorrentId torrentId) {
        if (buffers.containsKey(torrentId)) {
            throw new IllegalStateException("Buffer already exists for torrent ID: " + torrentId);
        }

        Torrent torrent = torrentRegistry.getTorrent(torrentId).get();

        int chunkSize = (int) torrent.getChunkSize();
        int chunksCount = torrentRegistry.getDescriptor(torrentId).get()
                .getDataDescriptor()
                .getChunkDescriptors().size();
        int slotsCount = Math.min(chunksCount, MIN_SLOTS_COUNT);
        int bufferSize = chunkSize * slotsCount;

        ByteBuffer buffer = ByteBuffer.allocateDirect(bufferSize);
        buffers.put(torrentId, buffer);
        lruSlots.put(torrentId, createSlots(buffer, chunkSize));
        slotByPieceIndexMap.put(torrentId, new HashMap<>());

        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace(String.format("Allocated buffer for torrent ID: %s." +
                    " Slots count: %d, total size: %,d bytes.", torrentId, lruSlots.get(torrentId).size(), bufferSize));
        }
    }

    private List<Slot> createSlots(ByteBuffer buffer, int chunkSize) {
        List<Slot> slots = new LinkedList<>();
        for (int i = 0; i < buffer.capacity() / chunkSize; i++) {
            buffer.limit(chunkSize * (i + 1));
            buffer.position(chunkSize * i);
            slots.add(new Slot(i, buffer.slice()));
        }
        return slots;
    }

    private synchronized void releaseBuffer(TorrentId torrentId) {
        buffers.remove(torrentId);
        lruSlots.remove(torrentId);
        slotByPieceIndexMap.remove(torrentId);

        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Released buffer for torrent ID: {}.", torrentId);
        }
    }

    @Override
    public synchronized BlockReader get(TorrentId torrentId, int pieceIndex, int offset, int length) {
        DataRange data = torrentRegistry.getDescriptor(torrentId).get()
                .getDataDescriptor()
                .getChunkDescriptors().get(pieceIndex)
                .getData();

        ByteBuffer buffer = buffers.get(torrentId);
        if (buffer == null) {
            throw new IllegalStateException("Missing buffer for torrent ID: " + torrentId);
        }

        Slot slot = tryGetSlot(torrentId, pieceIndex);
        if (slot == null) {
            slot = tryClaimSlot(torrentId, pieceIndex);
            if (slot == null) {
                // fallback to reading block directly from storage
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Cache is overloaded, can't claim a slot, will read from storage:" +
                            " torrent ID {" + torrentId + "}, piece index {" + pieceIndex + "}," +
                            " offset {" + offset + "}, length {" + length + "}");
                }
                return new BlockReader() {
                    @Override
                    public boolean readTo(ByteBuffer buffer) {
                        int bufferRemaining = buffer.remaining();
                        if (!data.getSubrange(offset, length)
                                .getBytes(buffer)) {
                            throw new IllegalStateException("Failed to read data to buffer:" +
                                    " piece index {" + pieceIndex + "}," +
                                    " offset {" + offset + "}," +
                                    " length: {" + length + "}," +
                                    " buffer space {" + bufferRemaining + "}");
                        }
                        return true;
                    }
                };
            }
            if (!data.getBytes(slot.buffer)) {
                throw new IllegalStateException("Failed to load data into buffer slot:" +
                        "torrent ID {" + torrentId + "}, piece index {" + pieceIndex + "}, slot {" + slot.buffer + "}");
            }
        }

        return readFromSlot(slot, offset, length);
    }

    private /*nullable*/ Slot tryGetSlot(TorrentId torrentId, int pieceIndex) {
        Map<Integer, Slot> mapping = slotByPieceIndexMap.get(torrentId);
        Slot slot = mapping.get(pieceIndex);
        if (slot != null) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Piece is contained in slot {}:" +
                        " torrent ID {}, piece index {}", slot.index, torrentId, pieceIndex);
            }
            List<Slot> lruSlots = this.lruSlots.get(torrentId);
            Iterator<Slot> iter = lruSlots.iterator();
            while (iter.hasNext()) {
                Slot lruSlot = iter.next();
                if (lruSlot.index == slot.index) {
                    iter.remove();
                    lruSlots.add(lruSlot);
                    break;
                }
            }
            return slot;
        } else if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Piece is not contained in any slot:" +
                    " torrent ID {}, piece index {}", torrentId, pieceIndex);
        }
        return null;
    }

    private Slot tryClaimSlot(TorrentId torrentId, int pieceIndex) {
        List<Slot> slots = lruSlots.get(torrentId);
        Iterator<Slot> iter = slots.iterator();
        Slot slot;
        while (iter.hasNext()) {
            slot = iter.next();
            if (slot.currentUsers == 0) {
                iter.remove();
                slot.buffer.clear();
                slots.add(slot);
                slotByPieceIndexMap.get(torrentId).put(pieceIndex, slot);
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Claimed a free slot {} for requested piece:" +
                            " torrent ID {}, piece index {}", slot.index, torrentId, pieceIndex);
                }
                return slot;
            }
        }
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Failed to claim a free slot for requested piece:" +
                    " torrent ID {}, piece index {}", torrentId, pieceIndex);
        }
        return null;
    }

    private BlockReader readFromSlot(Slot slot, int offset, int length) {
        int usedBy = slot.currentUsers += 1;
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Slot {} usage count increased to {}", slot.index, usedBy);
        }

        slot.buffer.limit(offset + length);
        slot.buffer.position(offset);
        ByteBuffer block = slot.buffer.slice();

        return new BlockReader() {
            @Override
            public boolean readTo(ByteBuffer buffer) {
                synchronized (LRUBlockCache.this) {
                    try {
                        if (buffer.remaining() < block.remaining()) {
                            return false;
                        }
                        buffer.put(block);
                        block.clear();
                        return true;
                    } finally {
                        int usedBy = slot.currentUsers -= 1;
                        if (LOGGER.isTraceEnabled()) {
                            LOGGER.trace("Slot {} usage count decreased to {}", slot.index, usedBy);
                        }
                    }
                }
            }
        };
    }

    private static class Slot {
        private final int index;
        private final ByteBuffer buffer;
        private int currentUsers;

        private Slot(int index, ByteBuffer buffer) {
            this.index = index;
            this.buffer = buffer;
            this.currentUsers = 0;
        }
    }
}
