package bt.torrent;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * PeerTimeoutRegistry – keeps peerId (ConnectionKey.toString()) -> expiry timestamp (ms).
 * Use markTimedOut(peerId) to ban for configured duration.
 * Use isAllowed(peerId) to check whether peer can be accepted.
 *
 * Supports optional persistence via saveToFile/loadFromFile so that bans can survive
 * short runtime restarts (important for Android environments where runtime restarts frequently).
 */
public class PeerTimeoutRegistry {

    private final Map<String, Long> expiryMap = new ConcurrentHashMap<>();
    private final long banDurationMillis;

    public PeerTimeoutRegistry(long banDuration, TimeUnit unit) {
        this.banDurationMillis = unit.toMillis(banDuration);
    }

    public void markTimedOut(String peerId) {
        long expiry = System.currentTimeMillis() + banDurationMillis;
        expiryMap.put(peerId, expiry);
    }

    /**
     * True if peer is allowed to connect (ban expired or not present).
     */
    public boolean isAllowed(String peerId) {
        Long expiry = expiryMap.get(peerId);
        if (expiry == null) return true;
        if (System.currentTimeMillis() > expiry) {
            expiryMap.remove(peerId);
            return true;
        }
        return false;
    }

    /**
     * Remove expired entries proactively.
     */
    public void cleanup() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Long> e : expiryMap.entrySet()) {
            if (e.getValue() <= now) {
                expiryMap.remove(e.getKey());
            }
        }
    }

    /**
     * Save current bans to a file so they can be restored later (e.g. after restart).
     * File format: one peerId=expiryTimestamp per line.
     */
    public void saveToFile(File file) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            for (Map.Entry<String, Long> e : expiryMap.entrySet()) {
                writer.write(e.getKey() + "=" + e.getValue());
                writer.newLine();
            }
        } catch (IOException ex) {
            System.err.println("PeerTimeoutRegistry: failed to save state: " + ex.getMessage());
        }
    }

    /**
     * Load bans from a file previously created by saveToFile.
     * Automatically drops expired entries.
     */
    public void loadFromFile(File file) {
        if (!file.exists()) return;
        long now = System.currentTimeMillis();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("=");
                if (parts.length != 2) continue;
                String peerId = parts[0];
                long expiry = Long.parseLong(parts[1]);
                if (expiry > now) {
                    expiryMap.put(peerId, expiry);
                }
            }
        } catch (IOException ex) {
            System.err.println("PeerTimeoutRegistry: failed to load state: " + ex.getMessage());
        }
    }
}
