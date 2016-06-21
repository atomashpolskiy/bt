package bt.it;

import bt.data.IChunkDescriptor;
import bt.it.fixture.BaseBtTest;
import bt.it.fixture.Swarm;
import bt.it.fixture.SharedTrackerFeature;
import bt.torrent.TorrentHandle;
import org.junit.Rule;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class Swarm_IT extends BaseBtTest {

    @Rule
    public Swarm swarm = buildSwarm().files(getSingleFile()).seeders(1).leechers(1).startingPort(6891).build();

    public Swarm_IT() {
        super(Collections.singleton(new SharedTrackerFeature()));
    }

    @Test
    public void testSwarm_OneSeederOneLeecher() {
        assertEquals(1, swarm.getSeeders().size());
        assertEquals(1, swarm.getLeechers().size());

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

        assertEquals(2, swarm.getSeeders().size());
        assertEquals(0, swarm.getLeechers().size());
    }
}
