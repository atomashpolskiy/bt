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
}
