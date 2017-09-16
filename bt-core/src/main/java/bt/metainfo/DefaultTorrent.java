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

package bt.metainfo;

import bt.BtException;
import bt.tracker.AnnounceKey;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

class DefaultTorrent implements Torrent {

    private static final int CHUNK_HASH_LENGTH = 20;

    private TorrentSource source;
    private Optional<AnnounceKey> announceKey;
    private TorrentId torrentId;
    private String name;
    private long chunkSize;
    private byte[] chunkHashes;
    private long size;
    private List<TorrentFile> files;
    private boolean isPrivate;
    private Optional<Instant> creationDate;
    private Optional<String> createdBy;

    DefaultTorrent(TorrentSource source) {
        this.source = source;
        this.announceKey = Optional.empty();
        this.creationDate = Optional.empty();
        this.createdBy = Optional.empty();
    }

    @Override
    public TorrentSource getSource() {
        return source;
    }

    @Override
    public Optional<AnnounceKey> getAnnounceKey() {
        return announceKey;
    }

    @Override
    public TorrentId getTorrentId() {
        return torrentId;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public long getChunkSize() {
        return chunkSize;
    }

    @Override
    public Iterable<byte[]> getChunkHashes() {

        return () -> new Iterator<byte[]>() {

            private int read;

            @Override
            public boolean hasNext() {
                return read < chunkHashes.length;
            }

            @Override
            public byte[] next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                int start = read;
                read += CHUNK_HASH_LENGTH;
                return Arrays.copyOfRange(chunkHashes, start, read);
            }
        };
    }

    @Override
    public long getSize() {
        return size;
    }

    @Override
    public List<TorrentFile> getFiles() {

        if (files == null) {
            DefaultTorrentFile file = new DefaultTorrentFile();
            file.setSize(getSize());
            // TODO: Name can be missing according to the spec,
            // so need to make sure that it's present
            // (probably by setting it to a user-defined value after processing the torrent metainfo)
            file.setPathElements(Collections.singletonList(getName()));
            return Collections.singletonList(file);
        } else {
            return Collections.unmodifiableList(files);
        }
    }

    @Override
    public boolean isPrivate() {
        return isPrivate;
    }

    @Override
    public Optional<Instant> getCreationDate() {
        return creationDate;
    }

    @Override
    public Optional<String> getCreatedBy() {
        return createdBy;
    }

    void setAnnounceKey(AnnounceKey announceKey) {
        this.announceKey = Optional.of(announceKey);
    }

    void setTorrentId(TorrentId torrentId) {
        this.torrentId = torrentId;
    }

    void setName(String name) {
        this.name = name;
    }

    void setChunkSize(long chunkSize) {
        if (chunkSize <= 0) {
            throw new BtException("Invalid chunk size: " + chunkSize);
        }
        this.chunkSize = chunkSize;
    }

    void setSize(long size) {
        if (size < 0) {
            throw new BtException("Invalid torrent size: " + size);
        }
        this.size = size;
    }

    void setFiles(List<TorrentFile> files) {
        if (files == null || files.isEmpty()) {
            throw new BtException("Can't create torrent without files");
        }
        this.files = files;
    }

    public void setChunkHashes(byte[] chunkHashes) {
        if (chunkHashes.length % CHUNK_HASH_LENGTH != 0) {
            throw new BtException("Invalid chunk hashes string -- length (" + chunkHashes.length
                    + ") is not divisible by " + CHUNK_HASH_LENGTH);
        }
        this.chunkHashes = chunkHashes;
    }

    public void setPrivate(boolean isPrivate) {
        this.isPrivate = isPrivate;
    }

    public void setCreationDate(Instant creationDate) {
        this.creationDate = Optional.of(creationDate);
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = Optional.of(createdBy);
    }

    @Override
    public String toString() {
        return name;
    }
}
