package bt.it;

import bt.it.fixture.BaseBtTest;
import bt.it.fixture.SharedTrackerModule;
import bt.it.fixture.Swarm;
import bt.protocol.crypto.EncryptionPolicy;
import bt.runtime.BtClient;
import bt.runtime.Config;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertEquals;

public class Swarm_MagnetIT extends BaseBtTest {

    private static final int NUMBER_OF_SEEDERS = 5;
    private static final int NUMBER_OF_LEECHERS = 2;
    private static final int NUMBER_OF_MAGNET_LEECHERS = 3;

    private static final Config CONFIG = new Config() {
        @Override
        public Duration getTrackerQueryInterval() {
            return Duration.ofSeconds(5);
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
            .leechers(NUMBER_OF_LEECHERS, NUMBER_OF_MAGNET_LEECHERS)
            .module(new SharedTrackerModule())
            .useInMemoryFileSystem()
            .build();

    @After
    public void after() {
        // TODO: workaround to shutdown swarm _before_ BaseBtTest removes files;
        // need to come up with something better to not write this everywhere
        swarm.shutdown();
    }

    @Test
    public void testSwarm_OneSeederManyLeechers() {
        BtClient seeder = swarm.getSeederHandles().iterator().next();
        List<BtClient> leechers = swarm.getLeecherHandles();

        CompletableFuture<?>[] leecherFutures =
                leechers.stream().map(BtClient::startAsync).toArray(CompletableFuture<?>[]::new);

        seeder.startAsync();
        CompletableFuture.allOf(leecherFutures).join();
        seeder.stop();

        assertEquals(NUMBER_OF_SEEDERS * 2, swarm.getSeeders().size());
        assertEquals(0, swarm.getLeechers().size());
    }

    @Test
    public void testSwarm_ManySeedersManyLeechers() {
        List<BtClient> seeders = swarm.getSeederHandles();
        List<BtClient> leechers = swarm.getLeecherHandles();

        CompletableFuture<?>[] leecherFutures =
                leechers.stream().map(BtClient::startAsync).toArray(CompletableFuture<?>[]::new);

        seeders.forEach(BtClient::startAsync);
        CompletableFuture.allOf(leecherFutures).join();
        seeders.forEach(BtClient::stop);

        assertEquals(NUMBER_OF_SEEDERS * 2, swarm.getSeeders().size());
        assertEquals(0, swarm.getLeechers().size());
    }
}
