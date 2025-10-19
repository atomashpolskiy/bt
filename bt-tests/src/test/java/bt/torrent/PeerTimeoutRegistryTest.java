package bt.torrent;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class PeerTimeoutRegistryTest {

    /**
     * Mark a peer as timed out and verify:
     *  - immediately after marking it is not allowed
     *  - after the ban duration it becomes allowed again
     */
    @Test
    public void testMarkTimedOutAndExpiry() throws InterruptedException {
        // ban duration: 200 ms
        PeerTimeoutRegistry registry = new PeerTimeoutRegistry(200, TimeUnit.MILLISECONDS);

        String peerId = "peer-1";
        assertTrue("Peer should be allowed before ban", registry.isAllowed(peerId));

        registry.markTimedOut(peerId);
        // Immediately after marking, peer should be banned
        assertFalse("Peer should be banned immediately after markTimedOut", registry.isAllowed(peerId));

        // Wait longer than ban duration
        Thread.sleep(250);

        // After expiry, isAllowed should return true
        assertTrue("Peer should be allowed after ban expiry", registry.isAllowed(peerId));
    }

    /**
     * After expiry, cleanup() should remove expired entries.
     * We test this indirectly: after expiry and cleanup, calling isAllowed should remain true.
     * (isAllowed itself also removes expired entries; cleanup is proactive removal).
     */
    @Test
    public void testCleanupRemovesExpiredEntries() throws InterruptedException {
        PeerTimeoutRegistry registry = new PeerTimeoutRegistry(150, TimeUnit.MILLISECONDS);

        String peerId = "peer-cleanup";
        registry.markTimedOut(peerId);

        // Ensure it's banned initially
        assertFalse(registry.isAllowed(peerId));

        // Wait for expiry
        Thread.sleep(200);

        // Call cleanup proactively
        registry.cleanup();

        // Now it must be allowed
        assertTrue("Peer should be allowed after cleanup and expiry", registry.isAllowed(peerId));
    }
}
