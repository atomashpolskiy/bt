package bt.it;

import bt.it.fixture.BaseBtTest;
import bt.it.fixture.SharedTrackerModule;
import bt.it.fixture.Swarm;
import bt.it.fixture.SwarmPeer;
import bt.protocol.crypto.EncryptionPolicy;
import bt.runtime.BtClient;
import bt.runtime.Config;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class Swarm_IT extends BaseBtTest {

    private static final int NUMBER_OF_SEEDERS = 5;

    private static final Config CONFIG = new Config() {
        @Override
        public int getMaxTransferBlockSize() {
            // use smaller buffer size to trigger buffer compaction
            return 10000;
        }

        @Override
        public EncryptionPolicy getEncryptionPolicy() {
            return EncryptionPolicy.REQUIRE_PLAINTEXT;
        }
    };

    @Rule
    public Swarm swarm = buildSwarm()
            .config(CONFIG)
            .seeders(NUMBER_OF_SEEDERS)
            .leechers(NUMBER_OF_SEEDERS)
            .module(new SharedTrackerModule())
            .build();

    @Test
    public void testSwarm_OneSeederOneLeecher() {

        BtClient seeder = swarm.getSeeders().iterator().next().getHandle(),
                      leecher = swarm.getLeechers().iterator().next().getHandle();

        seeder.startAsync();

        leecher.startAsync(state -> {
            if (state.getPiecesRemaining() == 0) {
                seeder.stop();
                leecher.stop();
            }
        }, 1000).join();

        assertEquals(NUMBER_OF_SEEDERS + 1, swarm.getSeeders().size());
        assertEquals(NUMBER_OF_SEEDERS - 1, swarm.getLeechers().size());
    }

    @Test
    public void testSwarm_ManySeedersOneLeecher() {

        List<BtClient> seeders = swarm.getSeeders().stream()
                .map(SwarmPeer::getHandle).collect(Collectors.toList());

        BtClient leecher = swarm.getLeechers().iterator().next().getHandle();

        seeders.forEach(BtClient::startAsync);

        leecher.startAsync(state -> {
            if (state.getPiecesRemaining() == 0) {
                seeders.forEach(BtClient::stop);
                leecher.stop();
            }
        }, 1000).join();

        assertEquals(NUMBER_OF_SEEDERS + 1, swarm.getSeeders().size());
        assertEquals(NUMBER_OF_SEEDERS - 1, swarm.getLeechers().size());
    }

    @Test
    public void testSwarm_OneSeederManyLeechers() {

        BtClient seeder = swarm.getSeeders().iterator().next().getHandle();

        List<BtClient> leechers = swarm.getLeechers().stream()
                .map(SwarmPeer::getHandle).collect(Collectors.toList());

        AtomicInteger leecherCount = new AtomicInteger(leechers.size());

        CompletableFuture<?> seederFuture = seeder.startAsync(state -> {
            if (leecherCount.get() == 0) {
                leechers.forEach(BtClient::stop);
                seeder.stop();
            }
        }, 5000);

        ConcurrentMap<BtClient, Boolean> finishedLeechers = new ConcurrentHashMap<>();
        leechers.forEach(leecher -> {
            finishedLeechers.put(leecher, Boolean.FALSE);
            leecher.startAsync(state -> {
                if (state.getPiecesRemaining() == 0 && !finishedLeechers.get(leecher)) {
                    synchronized (leechers) {
                        if (!finishedLeechers.get(leecher)) {
                            finishedLeechers.put(leecher, Boolean.TRUE);
                            leecherCount.decrementAndGet();
                        }
                    }
                }
            }, 5000);
        });

        seederFuture.join();

        assertEquals(NUMBER_OF_SEEDERS * 2, swarm.getSeeders().size());
        assertEquals(0, swarm.getLeechers().size());
    }

    @Test
    public void testSwarm_ManySeedersManyLeechers() {

        List<BtClient> seeders = swarm.getSeeders().stream()
                .map(SwarmPeer::getHandle).collect(Collectors.toList());

        List<BtClient> leechers = swarm.getLeechers().stream()
                .map(SwarmPeer::getHandle).collect(Collectors.toList());

        AtomicInteger leecherCount = new AtomicInteger(leechers.size());

        BtClient mainSeeder = seeders.iterator().next();
        CompletableFuture<?> seederFuture = mainSeeder.startAsync(
                state -> {
                    if (leecherCount.get() == 0) {
                        leechers.forEach(BtClient::stop);
                        seeders.forEach(BtClient::stop);
                        mainSeeder.stop();
                    }
                }, 5000
        );

        for (int i = 1; i < seeders.size(); i++) {
            BtClient seeder = seeders.get(i);
            seederFuture = seeder.startAsync();
        }

        ConcurrentMap<BtClient, Boolean> finishedLeechers = new ConcurrentHashMap<>();
        leechers.forEach(leecher -> {
            finishedLeechers.put(leecher, Boolean.FALSE);
            leecher.startAsync(state -> {
                if (state.getPiecesRemaining() == 0 && !finishedLeechers.get(leecher)) {
                    synchronized (leechers) {
                        if (!finishedLeechers.get(leecher)) {
                            finishedLeechers.put(leecher, Boolean.TRUE);
                            leecherCount.decrementAndGet();
                        }
                    }
                }
            }, 5000);
        });

        seederFuture.join();

        assertEquals(NUMBER_OF_SEEDERS * 2, swarm.getSeeders().size());
        assertEquals(0, swarm.getLeechers().size());
    }
}
