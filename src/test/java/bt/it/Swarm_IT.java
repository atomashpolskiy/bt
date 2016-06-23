package bt.it;

import bt.data.IChunkDescriptor;
import bt.it.fixture.BaseBtTest;
import bt.it.fixture.BtTestRuntimeFeature;
import bt.it.fixture.PersonalizedThreadNamesFeature;
import bt.it.fixture.SharedTrackerFeature;
import bt.it.fixture.Swarm;
import bt.it.fixture.SwarmPeer;
import bt.service.ConfigurationService;
import bt.service.IConfigurationService;
import bt.torrent.TorrentHandle;
import org.junit.Rule;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class Swarm_IT extends BaseBtTest {

    private static BtTestRuntimeFeature testConfiguration =
            (runtimeBuilder, binder) -> binder.bind(IConfigurationService.class).toInstance(
                    new ConfigurationService() {
                        @Override
                        public long getPeerRefreshThreshold() {
                            return 5000;
                        }
                    });

    @Rule
    public Swarm swarm = buildSwarm().files(getSingleFile()).seeders(10).leechers(10).startingPort(6891).build();

    public Swarm_IT() {
        super(Arrays.asList(new SharedTrackerFeature(), new PersonalizedThreadNamesFeature(), testConfiguration));
    }

    @Test
    public void testSwarm_OneSeederOneLeecher() {

        TorrentHandle seeder = swarm.getSeeders().iterator().next().getHandle(),
                      leecher = swarm.getLeechers().iterator().next().getHandle();

        seeder.getDataDescriptor().getChunkDescriptors().forEach(IChunkDescriptor::verify);
        seeder.startAsync();

        leecher.startAsync(state -> {
            if (state.getPiecesRemaining() == 0) {
                seeder.stop();
                leecher.stop();
            }
        }, 1000).join();

        assertEquals(11, swarm.getSeeders().size());
        assertEquals(9, swarm.getLeechers().size());
    }

    @Test
    public void testSwarm_ManySeedersOneLeecher() {

        List<TorrentHandle> seeders = swarm.getSeeders().stream()
                .map(SwarmPeer::getHandle).collect(Collectors.toList());

        TorrentHandle leecher = swarm.getLeechers().iterator().next().getHandle();

        seeders.forEach(seeder -> {
            seeder.getDataDescriptor().getChunkDescriptors().forEach(IChunkDescriptor::verify);
            seeder.startAsync();
        });

        leecher.startAsync(state -> {
            if (state.getPiecesRemaining() == 0) {
                seeders.forEach(TorrentHandle::stop);
                leecher.stop();
            }
        }, 1000).join();

        assertEquals(11, swarm.getSeeders().size());
        assertEquals(9, swarm.getLeechers().size());
    }

    @Test
    public void testSwarm_OneSeederManyLeechers() {

        TorrentHandle seeder = swarm.getSeeders().iterator().next().getHandle();

        List<TorrentHandle> leechers = swarm.getLeechers().stream()
                .map(SwarmPeer::getHandle).collect(Collectors.toList());

        AtomicInteger leecherCount = new AtomicInteger(leechers.size());

        seeder.getDataDescriptor().getChunkDescriptors().forEach(IChunkDescriptor::verify);
        CompletableFuture<?> seederFuture = seeder.startAsync(state -> {
            if (leecherCount.get() == 0) {
                seeder.stop();
                leechers.forEach(TorrentHandle::stop);
            }
        }, 5000);

        ConcurrentMap<TorrentHandle, Boolean> finishedLeechers = new ConcurrentHashMap<>();
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

        assertEquals(20, swarm.getSeeders().size());
        assertEquals(0, swarm.getLeechers().size());
    }
}
