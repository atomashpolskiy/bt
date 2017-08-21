package peertracker;

import bt.metainfo.TorrentId;
import bt.net.Peer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

public class StatsDumper {
    private static final Logger LOGGER = LoggerFactory.getLogger(StatsDumper.class);

    private static final String DUMP_PATH = "./stats.txt";

    private final long startedAt;

    public StatsDumper(long startedAt) {
        this.startedAt = startedAt;
    }

    public void dumpStats(Map<TorrentId, PeerStats> aggregateStats) {
        long t1 = System.nanoTime();

        File tempFile = null;
        try {
            tempFile = File.createTempFile(UUID.randomUUID().toString(), ".tmp");
            try (BufferedWriter w =  new BufferedWriter(new FileWriter(tempFile))) {
                w.write(String.format("Uptime: %s", Duration.ofMillis(System.currentTimeMillis() - startedAt)));
                w.newLine();
                w.newLine();

                for (Map.Entry<TorrentId, PeerStats> e : aggregateStats.entrySet()) {
                    TorrentId torrentId = e.getKey();
                    PeerStats stats = e.getValue();
                    Map<Peer, PeerStats.Counter> counters = stats.getCounters();

                    w.write(String.format("[%s]\ttotal known peers: %6d", torrentId, counters.size()));
                    w.newLine();
                    for (Map.Entry<Peer, PeerStats.Counter> e2 : counters.entrySet()) {
                        Peer peer = e2.getKey();
                        PeerStats.Counter counter = e2.getValue();

                        w.write(String.format("\t(%50s)\ttimes discovered: %6d,\ttimes connected: %6d,\ttimes disconnected: %6d",
                                peer, counter.getDiscoveredTimes(), counter.getConnectedTimes(), counter.getDisconnectedTimes()));
                        w.newLine();
                    }
                    w.newLine();
                    w.newLine();
                }
            }

            File currentFile = new File(DUMP_PATH);
            if (!currentFile.exists() || currentFile.delete()) {
                if (tempFile.renameTo(currentFile)) {
                    LOGGER.info("Dumped stats to {} in {} ms", DUMP_PATH, (System.nanoTime() - t1) / 1_000_000);
                } else {
                    LOGGER.warn("Failed to move temp file {} to {}", tempFile.getPath(), DUMP_PATH);
                }
            } else {
                LOGGER.warn("Failed to delete previous dump, will not replace: {}", DUMP_PATH);
            }

        } catch (IOException e) {
            LOGGER.error("Failed to dump stats to: " + DUMP_PATH, e);
            throw new RuntimeException(e);
        } finally {
            if (tempFile != null) {
                tempFile.delete();
            }
        }
    }
}
