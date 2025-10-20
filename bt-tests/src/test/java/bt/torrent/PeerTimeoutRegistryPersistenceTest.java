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

package bt.torrent;

import org.junit.After;
import org.junit.Test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class PeerTimeoutRegistryPersistenceTest {

    private File tempFile;

    @After
    public void tearDown() {
        if (tempFile != null && tempFile.exists()) {
            // best-effort cleanup
            tempFile.delete();
        }
    }

    /**
     * Save current bans, then load into a new registry instance and verify bans were restored.
     */
    @Test
    public void testSaveAndLoadRestoresBans() throws IOException, InterruptedException {
        tempFile = File.createTempFile("peer-timeout-registry", ".txt");
        tempFile.deleteOnExit();

        // Create registry with 1 second ban to give us time to save/load
        PeerTimeoutRegistry registry = new PeerTimeoutRegistry(1, TimeUnit.SECONDS);
        String peerId = "persistent-peer";
        registry.markTimedOut(peerId);

        // Save to file while still banned
        registry.saveToFile(tempFile);

        // Create a new registry and load from the saved file
        PeerTimeoutRegistry loadedRegistry = new PeerTimeoutRegistry(1, TimeUnit.SECONDS);
        loadedRegistry.loadFromFile(tempFile);

        // Immediately after loading, the peer should still be banned
        assertFalse("Loaded registry should reflect a currently active ban", loadedRegistry.isAllowed(peerId));

        // Wait for the ban to expire
        Thread.sleep(1100);

        // Now it should be allowed
        assertTrue("Loaded registry should allow peer after expiry", loadedRegistry.isAllowed(peerId));
    }

    /**
     * Verify that loadFromFile ignores expired entries and ignores malformed lines.
     */
    @Test
    public void testLoadIgnoresExpiredAndMalformedLines() throws IOException, InterruptedException {
        tempFile = File.createTempFile("peer-timeout-registry-malformed", ".txt");
        tempFile.deleteOnExit();

        long now = System.currentTimeMillis();
        long expiredTs = now - 1000L; // already expired
        long futureTs = now + 1000L; // not expired

        // Compose a file with:
        // - a valid entry that is expired (should be ignored)
        // - a valid entry that is in the future (should be loaded)
        // - a malformed line (should be ignored)
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile))) {
            writer.write("expired-peer=" + expiredTs);
            writer.newLine();
            writer.write("valid-peer=" + futureTs);
            writer.newLine();
            writer.write("MALFORMED_LINE_WITHOUT_EQUALS");
            writer.newLine();
        }

        // Load
        PeerTimeoutRegistry registry = new PeerTimeoutRegistry(1, TimeUnit.MINUTES);
        registry.loadFromFile(tempFile);

        // expired-peer should be allowed (because it was already expired)
        assertTrue("Expired entry should be ignored on load", registry.isAllowed("expired-peer"));

        // valid-peer should be banned until its timestamp
        assertFalse("Future entry should result in a ban", registry.isAllowed("valid-peer"));

        // Wait past that future timestamp
        Thread.sleep(1100);

        // Now valid-peer should be allowed
        assertTrue("Previously loaded future entry should expire", registry.isAllowed("valid-peer"));
    }
}