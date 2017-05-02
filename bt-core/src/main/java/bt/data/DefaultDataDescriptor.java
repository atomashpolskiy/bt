package bt.data;

import bt.BtException;
import bt.data.digest.Digester;
import bt.metainfo.Torrent;
import bt.metainfo.TorrentFile;
import bt.torrent.Bitfield;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import java.util.stream.Collectors;

class DefaultDataDescriptor implements DataDescriptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultDataDescriptor.class);

    private Storage storage;

    private Torrent torrent;
    private List<ChunkDescriptor> chunkDescriptors;
    private Bitfield bitfield;

    private List<StorageUnit> storageUnits;

    private Digester digester;
    private int numOfHashingThreads;

    public DefaultDataDescriptor(Storage storage,
                                 Torrent torrent,
                                 Digester digester,
                                 int transferBlockSize,
                                 int numOfHashingThreads) {
        this.storage = storage;
        this.torrent = torrent;
        this.digester = digester;
        this.numOfHashingThreads = numOfHashingThreads;

        this.storageUnits = new ArrayList<>();

        init(transferBlockSize);
    }

    private void init(long transferBlockSize) {

        List<TorrentFile> torrentFiles = torrent.getFiles();

        int filesCount = torrentFiles.size();
        long totalSize = torrent.getSize();
        long chunkSize = torrent.getChunkSize();

        if (transferBlockSize > chunkSize) {
            transferBlockSize = chunkSize;
        }

        int chunksTotal = (int) Math.ceil(totalSize / chunkSize);
        List<Supplier<ChunkDescriptor>> suppliers = new ArrayList<>(chunksTotal + 1);
        Iterator<byte[]> chunkHashes = torrent.getChunkHashes().iterator();
        this.storageUnits = torrentFiles.stream().map(f -> storage.getUnit(torrent, f)).collect(Collectors.toList());
        StorageUnit[] files = storageUnits.toArray(new StorageUnit[storageUnits.size()]);

        long chunkOffset = 0,
             totalSizeOfFiles = 0;

        int firstFileInChunkIndex = 0;

        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Creating data descriptor for torrent: {}. " +
                    "Total size: {}, number of files: {}, chunk size: {}, number of chunks: {}, transfer block size: {}.",
                    torrent, totalSize, torrentFiles.size(), chunkSize, chunksTotal, transferBlockSize);
        }

        for (int currentFileIndex = 0; currentFileIndex < filesCount; currentFileIndex++) {
            TorrentFile torrentFile = torrentFiles.get(currentFileIndex);
            long fileSize = torrentFile.getSize();
            totalSizeOfFiles += fileSize;

            if (totalSizeOfFiles >= chunkSize) {
                do {
                    long limitInCurrentFile = chunkSize - (totalSizeOfFiles - fileSize);

                    if (!chunkHashes.hasNext()) {
                        // TODO: this should probably be handled in DefaultTorrent builder
                        throw new BtException("Wrong number of chunk hashes in the torrent: too few");
                    }

                    StorageUnit[] chunkFiles = Arrays.copyOfRange(files, firstFileInChunkIndex, currentFileIndex + 1);
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("Creating chunk descriptor for torrent files: {}. " +
                                "Offset in first file: {}, limit in last file: {}, block size: {}",
                                Arrays.toString(chunkFiles), chunkOffset, limitInCurrentFile, transferBlockSize);
                    }

                    suppliers.add(createSupplier(chunkFiles, chunkOffset, limitInCurrentFile, chunkHashes.next(), transferBlockSize));

                    firstFileInChunkIndex = currentFileIndex;
                    chunkOffset = limitInCurrentFile;

                    totalSizeOfFiles -= chunkSize;

                // if surplus is bigger than the chunk size,
                // then we need to catch up and create more than one chunk
                } while (totalSizeOfFiles >= chunkSize);

                if (totalSizeOfFiles > 0) {
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("Bytes remaining in the current file: {}. Creating next chunk...", totalSizeOfFiles);
                    }
                } else if (totalSizeOfFiles == 0) {
                    // no bytes left in the current file,
                    // new chunk will begin with the next file
                    firstFileInChunkIndex++;
                    chunkOffset = 0;
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("No bytes remaining in the current file. Next chunk will begin in the next file.");
                    }
                }
            }
            if (currentFileIndex == filesCount - 1 && totalSizeOfFiles > 0) {
                // create chunk for the remainder of the last file
                StorageUnit[] chunkFiles = Arrays.copyOfRange(files, firstFileInChunkIndex, currentFileIndex + 1);
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Creating chunk descriptor for torrent files: {}. " +
                            "Offset in first file: {}, limit in last file: {}, block size: {}",
                            Arrays.toString(chunkFiles), chunkOffset, fileSize, transferBlockSize);
                }
                suppliers.add(createSupplier(chunkFiles, chunkOffset, fileSize, chunkHashes.next(), transferBlockSize));
            }
        }

        if (chunkHashes.hasNext()) {
            throw new BtException("Wrong number of chunk hashes in the torrent: too many");
        }

        this.chunkDescriptors = (numOfHashingThreads > 1) ? collectParallel(suppliers) :
                suppliers.stream().map(Supplier::get).collect(Collectors.toList());
        this.bitfield = new Bitfield(this.chunkDescriptors);

        // try to purge all data that was loaded by the verifiers
        System.gc();
    }

    private Supplier<ChunkDescriptor> createSupplier(StorageUnit[] files,
                                                     long offset,
                                                     long limit,
                                                     byte[] checksum,
                                                     long blockSize) {
        return () -> new DefaultChunkDescriptor(files, offset, limit, blockSize, checksum, digester, true);
    }

    private List<ChunkDescriptor> collectParallel(List<Supplier<ChunkDescriptor>> suppliers) {
        ChunkDescriptor[] descriptors = new ChunkDescriptor[suppliers.size()];

        int n = numOfHashingThreads;
        ExecutorService workers = Executors.newFixedThreadPool(n);

        List<Future<?>> futures = new ArrayList<>();

        int batchSize = suppliers.size() / n;
        int i, limit = 0;
        while ((i = limit) < suppliers.size()) {
            if (futures.size() == n - 1) {
                // assign the remaining bits to the last worker
                limit = suppliers.size();
            } else {
                limit = i + batchSize;
            }
            futures.add(workers.submit(createWorker(descriptors, i, Math.min(suppliers.size(), limit), suppliers)));
        }

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Verifying torrent data with {} workers", futures.size());
        }

        Set<Throwable> errors = ConcurrentHashMap.newKeySet();
        futures.forEach(f -> {
            try {
                f.get();
            } catch (Exception e) {
                LOGGER.error("Unexpected error during verification of torrent data", e);
                errors.add(e);
            }
        });

        workers.shutdown();
        while (!workers.isTerminated()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new BtException("Unexpectedly interrupted");
            }
        }

        if (!errors.isEmpty()) {
            throw new BtException("Failed to verify torrent data:" +
                    errors.stream().map(this::errorToString).reduce(String::concat).get());
        }

        return Arrays.asList(descriptors);
    }

    private String errorToString(Throwable e) {
        StringBuilder buf = new StringBuilder();
        buf.append("\n");

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bos);
        e.printStackTrace(out);

        buf.append(bos.toString());
        return buf.toString();
    }

    private Runnable createWorker(ChunkDescriptor[] descriptors,
                                  int from,
                                  int to,
                                  List<Supplier<ChunkDescriptor>> suppliers) {
        return () -> {
            int i = from;
            while (i < to) {
                descriptors[i] = suppliers.get(i).get();
                i++;
            }
        };
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
