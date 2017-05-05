package bt.data;

import bt.BtException;
import bt.data.digest.Digester;
import bt.metainfo.Torrent;
import bt.metainfo.TorrentFile;
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
        List<TorrentFile> files = torrent.getFiles();

        long totalSize = torrent.getSize();
        long chunkSize = torrent.getChunkSize();

        if (transferBlockSize > chunkSize) {
            transferBlockSize = chunkSize;
        }

        Iterator<byte[]> chunkHashes = torrent.getChunkHashes().iterator();
        List<StorageUnit> storageUnits = files.stream().map(f -> storage.getUnit(torrent, f)).collect(Collectors.toList());

        long limitInLastUnit = storageUnits.get(storageUnits.size() - 1).capacity();
        DataRange data = new ReadWriteDataRange(storageUnits, 0, limitInLastUnit);

        int chunksTotal = (int) Math.ceil(totalSize / chunkSize);
        List<ChunkDescriptor> chunks = new ArrayList<>(chunksTotal + 1);

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

        if (chunkHashes.hasNext()) {
            throw new BtException("Wrong number of chunk hashes in the torrent: too many");
        }

        this.bitfield = buildBitfield(chunks);
        this.chunkDescriptors = chunks;
    }

    private ChunkDescriptor buildChunkDescriptor(DataRange data, long blockSize, byte[] checksum) {
        BlockDataRange blockData = new BlockDataRange(data, blockSize);
        SynchronizedDataRange synchronizedData = new SynchronizedDataRange(blockData);
        BlockSet synchronizedBlockSet = new SynchronizedBlockSet(blockData.getBlockSet());

        return new DefaultChunkDescriptor(synchronizedData, synchronizedBlockSet, checksum);
    }

    private Bitfield buildBitfield(List<ChunkDescriptor> chunks) {
        ChunkDescriptor[] arr = chunks.toArray(new ChunkDescriptor[chunks.size()]);
        Bitfield bitfield = new Bitfield(chunks.size());
        if (numOfHashingThreads > 1) {
            collectParallel(arr, bitfield);
        } else {
            createWorker(arr, 0, arr.length, bitfield).run();
        }
        // try to purge all data that was loaded by the verifiers
        System.gc();

        return bitfield;
    }

    private List<ChunkDescriptor> collectParallel(ChunkDescriptor[] chunks, Bitfield bitfield) {
        int n = numOfHashingThreads;
        ExecutorService workers = Executors.newFixedThreadPool(n);

        List<Future<?>> futures = new ArrayList<>();

        int batchSize = chunks.length / n;
        int i, limit = 0;
        while ((i = limit) < chunks.length) {
            if (futures.size() == n - 1) {
                // assign the remaining bits to the last worker
                limit = chunks.length;
            } else {
                limit = i + batchSize;
            }
            futures.add(workers.submit(createWorker(chunks, i, Math.min(chunks.length, limit), bitfield)));
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

        return Arrays.asList(chunks);
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

    private Runnable createWorker(ChunkDescriptor[] chunks,
                                  int from,
                                  int to,
                                  Bitfield bitfield) {
        return () -> {
            int i = from;
            while (i < to) {
                // optimization to speedup the initial verification of torrent's data
                int[] emptyUnits = new int[]{0};
                chunks[i].getData().visitUnits((u, off, lim) -> {
                    if (u.size() == 0) {
                        emptyUnits[0]++;
                    }
                    return true;
                });

                // if any of this chunk's storage units is empty,
                // then the chunk is neither complete nor verified
                if (emptyUnits[0] == 0) {
                    byte[] expected = chunks[i].getChecksum();
                    byte[] actual = digester.digest(chunks[i].getData());
                    boolean verified = Arrays.equals(expected, actual);
                    if (verified) {
                        bitfield.markVerified(i);
                    }
                }
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
