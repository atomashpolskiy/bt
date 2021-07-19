package bt.torrent.maker;

import bt.bencoding.model.BEObject;
import bt.bencoding.types.BEInteger;
import bt.bencoding.types.BEList;
import bt.bencoding.types.BEMap;
import bt.bencoding.types.BEString;
import bt.data.DataRange;
import bt.data.PieceUtils;
import bt.data.StorageUnit;
import bt.data.digest.Digester;
import bt.data.digest.SHA1Digester;
import bt.data.file.FileSystemStorageUnit;
import bt.data.file.OpenFileCache;
import bt.metainfo.MetadataConstants;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * A class which makes a torrent from the builder specification
 */
public class TorrentMaker {
    private final TorrentBuilder torrentBuilder;

    /**
     * Create a new torrent maker class with the specified torrent builder
     *
     * @param torrentBuilder the torrent builder
     */
    public TorrentMaker(TorrentBuilder torrentBuilder) {
        this.torrentBuilder = torrentBuilder;
    }

    /**
     * Make the torrent from the builder specification
     *
     * @return the bytes of the built torrent
     */
    public byte[] makeTorrent() {
        Map<String, BEObject<?>> torrentMap = new HashMap<>();

        if (torrentBuilder.getAnnounceGroups() != null) {
            List<BEList> announceGroupsList = torrentBuilder.getAnnounceGroups().stream()
                    .map(announceGroup -> new BEList(announceGroup.stream()
                            .map(BEString::new)
                            .collect(Collectors.toList())))
                    .collect(Collectors.toList());
            BEList announceGroups = new BEList(announceGroupsList);
            torrentMap.put(MetadataConstants.ANNOUNCE_LIST_KEY, announceGroups);
            // will be overrided if explicitly specified
            torrentMap.put(MetadataConstants.ANNOUNCE_KEY, announceGroupsList.get(0).getValue().get(0));
        }

        if (torrentBuilder.getAnnounce() != null) {
            torrentMap.put(MetadataConstants.ANNOUNCE_KEY,
                    new BEString(torrentBuilder.getAnnounce()));
        }

        if (torrentBuilder.getCreatedBy() != null) {
            torrentMap.put(MetadataConstants.CREATED_BY_KEY, new BEString(torrentBuilder.getCreatedBy()));
        }
        torrentMap.put(MetadataConstants.CREATION_DATE_KEY, new BEInteger(torrentBuilder.getCreationDate()));

        BEMap infoMap = buildInfoMap();
        torrentMap.put(MetadataConstants.INFOMAP_KEY, infoMap);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try {
            new BEMap(torrentMap).writeTo(byteArrayOutputStream);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return byteArrayOutputStream.toByteArray();
    }

    private BEMap buildInfoMap() {
        Map<String, BEObject<?>> infoMap = new HashMap<>();

        infoMap.put(MetadataConstants.CHUNK_SIZE_KEY, new BEInteger(torrentBuilder.getPieceSize()));
        List<Path> files = torrentBuilder.getFiles();

        if (files.isEmpty()) {
            throw new IllegalStateException("No files added to torrent.");
        }

        if (files.size() == 1) {
            Path pathToAdd = files.get(0);
            try {
                infoMap.put(MetadataConstants.FILE_SIZE_KEY,
                        new BEInteger(Files.size(pathToAdd)));
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
            setTorrentName(infoMap, pathToAdd);
        } else {
            Path rootDir = Objects.requireNonNull(torrentBuilder.getRootPath());
            BEList filesList = buildFilesList(files, rootDir);
            infoMap.put(MetadataConstants.FILES_KEY, filesList);
            setTorrentName(infoMap, rootDir);
        }

        byte[] sha1Hashes = getSha1Hashes(files);
        infoMap.put(MetadataConstants.CHUNK_HASHES_KEY, new BEString(sha1Hashes));

        if (torrentBuilder.isPrivate()) {
            infoMap.put(MetadataConstants.PRIVATE_KEY, new BEInteger(1));
        }

        return new BEMap(infoMap);
    }

    private BEList buildFilesList(List<Path> files, Path rootDir) {
        List<BEObject<?>> filesList = new ArrayList<>(files.size());

        files.forEach(path -> {
            BEMap fileMap = buildFileMap(rootDir, path);
            filesList.add(fileMap);
        });

        return new BEList(filesList);
    }

    private BEMap buildFileMap(Path rootDir, Path path) {
        Map<String, BEObject<?>> fileMap = new HashMap<>();
        try {
            fileMap.put(MetadataConstants.FILE_SIZE_KEY, new BEInteger(Files.size(path)));
            if (!path.startsWith(rootDir)) {
                throw new IllegalStateException(
                        "Cannot add file " + path + " because it is not in the root torrent directory: " + rootDir);
            }
            Path relativePath = rootDir.relativize(path);
            int nameCount = relativePath.getNameCount();
            List<BEString> pathParts = new ArrayList<>(nameCount);
            for (int i = 0; i < nameCount; i++) {
                pathParts.add(new BEString(relativePath.getName(i).toString()));
            }
            fileMap.put(MetadataConstants.FILE_PATH_ELEMENTS_KEY, new BEList(pathParts));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return new BEMap(fileMap);
    }

    private void setTorrentName(Map<String, BEObject<?>> infoMap, Path rootDir) {
        Path normalizedPath = rootDir.toAbsolutePath().normalize();
        Path path = normalizedPath.getFileName();
        if (path == null) {
            throw new IllegalStateException("invalid root file " + rootDir);
        }
        infoMap.put(MetadataConstants.TORRENT_NAME_KEY, new BEString(path.toString()));
    }

    private byte[] getSha1Hashes(List<Path> files) {
        final OpenFileCache cache = new OpenFileCache(torrentBuilder.getMaxNumOpenFiles());
        try {
            List<StorageUnit> fileStorageUnits = files.stream()
                    .map(path -> new FileSystemStorageUnit(cache, path))
                    .collect(Collectors.toList());
            final long totalSize = fileStorageUnits.stream().mapToLong(StorageUnit::size).sum();

            DataRange dataRange = PieceUtils.buildReadWriteDataRange(fileStorageUnits);

            return computeSha1Hashes(totalSize, dataRange);
        } finally {
            try {
                cache.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    private byte[] computeSha1Hashes(long totalSize, DataRange dataRange) {
        Digester digester = SHA1Digester.newDigester();
        final int pieceSize = torrentBuilder.getPieceSize();
        int numChunks = PieceUtils.calculateNumberOfChunks(totalSize, pieceSize);
        byte[] hashBuf = new byte[digester.length() * numChunks];
        Runnable computeDigests = createDigestRunnable(totalSize, dataRange, digester, pieceSize, numChunks, hashBuf);
        runComputation(computeDigests);

        return hashBuf;
    }

    private Runnable createDigestRunnable(long totalSize, DataRange dataRange, Digester digester, int pieceSize,
                                          int numChunks, byte[] hashBuf) {
        final int hashLen = digester.length();
        IntStream chunkRange = getChunkNumStream(numChunks);

        return () -> chunkRange
                .forEach(
                        i -> {
                            final long startOffset = i * (long) pieceSize;
                            DataRange subrange =
                                    dataRange.getSubrange(startOffset, Math.min(pieceSize, totalSize - startOffset));
                            byte[] hash = digester.digestForced(subrange);
                            System.arraycopy(hash, 0, hashBuf, hashLen * i, hashLen);
                        });
    }

    private void runComputation(Runnable computeDigests) {
        if (torrentBuilder.getNumHashingThreads() == 1) {
            computeDigests.run();
        } else {
            ForkJoinPool pool = torrentBuilder.getNumHashingThreads() < 2 ?
                    ForkJoinPool.commonPool() : new ForkJoinPool(torrentBuilder.getNumHashingThreads());

            pool.submit(computeDigests).join();
        }
    }

    private IntStream getChunkNumStream(int numChunks) {
        IntStream chunkRange = IntStream.range(0, numChunks);
        if (torrentBuilder.getNumHashingThreads() != 1) {
            chunkRange = chunkRange.parallel();
        }
        return chunkRange;
    }
}
