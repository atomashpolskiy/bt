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

import bt.BtException;
import bt.data.range.BlockRange;
import bt.data.range.Ranges;
import bt.metainfo.Torrent;
import bt.metainfo.TorrentFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

class DefaultDataDescriptor implements DataDescriptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultDataDescriptor.class);

    private Storage storage;

    private Torrent torrent;
    private List<ChunkDescriptor> chunkDescriptors;
    private Bitfield bitfield;

    private List<StorageUnit> storageUnits;

    private ChunkVerifier verifier;

    public DefaultDataDescriptor(Storage storage,
                                 Torrent torrent,
                                 ChunkVerifier verifier,
                                 int transferBlockSize) {
        this.storage = storage;
        this.torrent = torrent;
        this.verifier = verifier;

        init(transferBlockSize);
    }

    private void init(long transferBlockSize) {
        List<TorrentFile> files = torrent.getFiles();

        long totalSize = torrent.getSize();
        long chunkSize = torrent.getChunkSize();

        if (transferBlockSize > chunkSize) {
            transferBlockSize = chunkSize;
        }

        int chunksTotal = (int) Math.ceil(totalSize / chunkSize);
        List<ChunkDescriptor> chunks = new ArrayList<>(chunksTotal + 1);

        Iterator<byte[]> chunkHashes = torrent.getChunkHashes().iterator();
        this.storageUnits = files.stream().map(f -> storage.getUnit(torrent, f)).collect(Collectors.toList());

        // filter out empty files (and create them at once)
        List<StorageUnit> nonEmptyStorageUnits = new ArrayList<>();
        for (StorageUnit unit : storageUnits) {
            if (unit.capacity() > 0) {
                nonEmptyStorageUnits.add(unit);
            } else {
                try {
                    // TODO: think about adding some explicit "initialization/creation" method
                    unit.writeBlock(new byte[0], 0);
                } catch (Exception e) {
                    LOGGER.warn("Failed to create empty storage unit: " + unit, e);
                }
            }
        }

        if (nonEmptyStorageUnits.size() > 0) {
            long limitInLastUnit = nonEmptyStorageUnits.get(nonEmptyStorageUnits.size() - 1).capacity();
            DataRange data = new ReadWriteDataRange(nonEmptyStorageUnits, 0, limitInLastUnit);

            long off, lim;
            long remaining = totalSize;
            while (remaining > 0) {
                off = chunks.size() * chunkSize;
                lim = Math.min(chunkSize, remaining);

                DataRange subrange = data.getSubrange(off, lim);

                if (!chunkHashes.hasNext()) {
                    throw new BtException("Wrong number of chunk hashes in the torrent: too few");
                }

                chunks.add(buildChunkDescriptor(subrange, transferBlockSize, chunkHashes.next()));

                remaining -= chunkSize;
            }
        }

        if (chunkHashes.hasNext()) {
            throw new BtException("Wrong number of chunk hashes in the torrent: too many");
        }

        this.bitfield = buildBitfield(chunks);
        this.chunkDescriptors = chunks;
    }

    private ChunkDescriptor buildChunkDescriptor(DataRange data, long blockSize, byte[] checksum) {
        BlockRange<DataRange> blockData = Ranges.blockRange(data, blockSize);
        DataRange synchronizedData = Ranges.synchronizedDataRange(blockData);
        BlockSet synchronizedBlockSet = Ranges.synchronizedBlockSet(blockData.getBlockSet());

        return new DefaultChunkDescriptor(synchronizedData, synchronizedBlockSet, checksum);
    }

    private Bitfield buildBitfield(List<ChunkDescriptor> chunks) {
        Bitfield bitfield = new Bitfield(chunks.size());
        verifier.verify(chunks, bitfield);
        return bitfield;
    }

    @Override
    public List<ChunkDescriptor> getChunkDescriptors() {
        return chunkDescriptors;
    }

    @Override
    public Bitfield getBitfield() {
        return bitfield;
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
