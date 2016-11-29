package bt.tracker.udp;

import java.time.Duration;
import java.util.Date;

class Session {

    private static final Duration SESSION_DURATION = Duration.ofMinutes(1);
    private static final Session NO_SESSION = new Session(0x41727101980L);

    public static Session noSession() {
        return NO_SESSION;
    }

    private long id;
    private long createdOn;

    public Session(long id) {
        this.id = id;
        this.createdOn = System.currentTimeMillis();
    }

    public boolean isExpired() {
        return (System.currentTimeMillis() - createdOn) >= SESSION_DURATION.toMillis();
    }

    public long getId() {
        return id;
    }

    @Override
    public String toString() {
        return "Session{" +
                "id=" + id +
                ", createdOn=" + new Date(createdOn) +
                '}';
    }
}
