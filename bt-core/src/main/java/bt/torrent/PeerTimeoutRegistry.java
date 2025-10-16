package bt.torrent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * PeerTimeoutRegistry - keeps peerId (ConnectionKey.toString()) -> expiry timestamp (ms).
 * Use markTimedOut(peerId) to ban for configured duration.
 * Use isAllowed(peerId) to check whether peer can be accepted.
 *
 * This is intentionally minimal and thread-safe.
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

}
