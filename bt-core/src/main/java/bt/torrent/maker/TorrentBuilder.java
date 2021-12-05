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

package bt.torrent.maker;

import bt.data.digest.SHA1Digester;
import com.google.common.collect.Sets;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Stream;

/**
 * A builder pattern to make torrent files conforming to <a href="https://www.bittorrent.org/beps/bep_0003.html">BEP-003</a>
 */
public class TorrentBuilder {
    private int numHashingThreads = 0;
    private int maxNumOpenFiles = 128;
    private int hashingBufferSize = SHA1Digester.DEFAULT_BUFFER_SIZE;

    private Path rootPath;
    private Set<Path> files;
    private String announce;
    private List<List<String>> announceGroups;

    private String createdBy;
    private Date creationDate;

    private int pieceSize = 1 << 18; // 256kB by default

    private boolean isPrivate = false;

    public List<Path> getFiles() {
        return new ArrayList<>(files);
    }

    public String getAnnounce() {
        return announce;
    }

    public Path getRootPath() {
        return rootPath;
    }

    public int getPieceSize() {
        return pieceSize;
    }

    public TorrentBuilder addFiles(Path... files) {
        Arrays.stream(Objects.requireNonNull(files)).forEach(this::addFile);
        return this;
    }

    /**
     * Add a file to be included within the torrent
     *
     * @param file the file to include in the torrent
     * @return the torrent builder
     */
    public TorrentBuilder addFile(Path file) {
        if (!Files.isReadable(file)) {
            throw new IllegalArgumentException("Cannot read file: " + file);
        }

        ensureFilesInitialized();
        if (Files.isRegularFile(file)) {
            this.files.add(file.normalize());
        } else if (Files.isDirectory(file)) {
            try (Stream<Path> pathStream = Files.walk(file.normalize())) {
                pathStream.filter(Files::isRegularFile)
                        .forEach(this.files::add);
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        } else {
            throw new IllegalArgumentException("Cannot add file: " + file);
        }

        return this;
    }

    private void ensureFilesInitialized() {
        if (this.files == null) {
            this.files = Sets.newLinkedHashSet(); // preserve order of added files
        }
    }

    public TorrentBuilder announce(String announce) {
        this.announce = announce;
        return this;
    }

    /**
     * Set the root path of the torrent
     *
     * @param rootPath the root path of the torrent
     * @return the torrent builder
     */
    public TorrentBuilder rootPath(Path rootPath) {
        if (!Files.isReadable(rootPath)) {
            throw new IllegalArgumentException("root path is not readable " + rootPath);
        }

        if (!Files.isDirectory(rootPath)) {
            throw new IllegalArgumentException("root path is not directory " + rootPath);
        }

        this.rootPath = rootPath.normalize();
        return this;
    }

    /**
     * Set the piece size of the torrent. Must be a power of 2
     *
     * @param pieceSize the piece size of the torrent
     * @return the torrent builder
     */
    public TorrentBuilder pieceSize(int pieceSize) {
        if (Integer.bitCount(pieceSize) > 1) {
            throw new IllegalArgumentException("Piece size must be a power of 2: " + pieceSize);
        }

        this.pieceSize = pieceSize;
        return this;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    /**
     * Set the author of the torrent stored in the created by flag. {@link bt.metainfo.MetadataConstants#CREATED_BY_KEY}
     *
     * @param createdBy the string to put in the created by position
     */
    public TorrentBuilder createdBy(String createdBy) {
        this.createdBy = createdBy;
        return this;
    }

    /**
     * {@link #creationDate(Date)}
     */
    public long getCreationDate() {
        return (creationDate == null ? System.currentTimeMillis() : creationDate.getTime()) / 1000L;
    }

    /**
     * Set the creation date of the torrent. Defaults to the time which the torrent is built
     *
     * @param creationDate the creation date of the torrent
     */
    public TorrentBuilder creationDate(Date creationDate) {
        this.creationDate = creationDate;
        return this;
    }

    /**
     * {@link #privateFlag(boolean)}
     */
    public boolean isPrivate() {
        return isPrivate;
    }

    /**
     * Set whether this torrent is a private torrent. See <a href="https://www.bittorrent.org/beps/bep_0027.html">BEP-0027</a>
     *
     * @param isPrivate whether the torrent should be marked as private
     */
    public TorrentBuilder privateFlag(boolean isPrivate) {
        this.isPrivate = isPrivate;
        return this;
    }

    /**
     * {@link #maxNumOpenFiles(int)}
     */
    public int getMaxNumOpenFiles() {
        return maxNumOpenFiles;
    }

    /**
     * Set the max number of open files during torrent creation. This is useful if you are creating a torrent which
     * contains many different files and you want to minimize opening the same file when computing hashes while not
     * exceeding the open file descriptor limit
     *
     * @param maxNumOpenFiles the max number of files that will be opened concurrently during torrent creation.
     */
    public TorrentBuilder maxNumOpenFiles(int maxNumOpenFiles) {
        if (maxNumOpenFiles < 1) {
            throw new IllegalArgumentException();
        }
        this.maxNumOpenFiles = maxNumOpenFiles;
        return this;
    }

    public int getNumHashingThreads() {
        return numHashingThreads;
    }

    /**
     * Set the number of threads used to create sha1 hashes for the torrent being created
     * any value less than 1 will use Java's common fork join pool. {@link ForkJoinPool#commonPool()}
     *
     * @param numHashingThreads the number of threads used to create hashes
     * @return the torrent builder
     */
    public TorrentBuilder numHashingThreads(int numHashingThreads) {
        this.numHashingThreads = numHashingThreads;
        return this;
    }

    public TorrentBuilder addAnnounceGroup(Collection<String> announceGroup) {
        Objects.requireNonNull(announceGroup);
        if (announceGroup.isEmpty()) {
            throw new IllegalArgumentException("Cannot add empty announce group.");
        }

        if (announceGroups == null) {
            announceGroups = new ArrayList<>();
        }

        announceGroups.add(new ArrayList<>(announceGroup));
        return this;
    }

    public List<List<String>> getAnnounceGroups() {
        return announceGroups;
    }


    /**
     * Get the digest buffer size
     *
     * @return the size of the digest buffer
     */
    public int getHashingBufferSize() {
        return hashingBufferSize;
    }

    /**
     * Set the size of the io buffer used for reading from files to verify their digest.
     *
     * @param hashingBufferSize the size of the digest buffer
     */
    public TorrentBuilder hashingBufferSize(int hashingBufferSize) {
        this.hashingBufferSize = hashingBufferSize;
        return this;
    }

    /**
     * Build the torrent
     *
     * @return the created torrent file's bytes
     */
    public byte[] build() {
        return new TorrentMaker(this).makeTorrent();
    }
}
