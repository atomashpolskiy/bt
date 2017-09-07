package bt.event;

/**
 * Globally ordered, timestamped event.
 *
 * {@link #compareTo(BaseEvent)} orders events according to natural ordering of their IDs.
 *
 * @since 1.5
 */
public abstract class BaseEvent implements Comparable<BaseEvent> {

    private final long id;
    private final Object objectId;
    private final long timestamp;

    /**
     * @param id Unique event ID
     * @param timestamp Timestamp
     * @since 1.5
     */
    protected BaseEvent(long id, long timestamp) {
        if (id <= 0 || timestamp <= 0) {
            throw new IllegalArgumentException("Invalid arguments: id (" + id + "), timestamp (" + timestamp + ")");
        }
        this.id = id;
        this.objectId = id;
        this.timestamp = timestamp;
    }

    /**
     * @return Unique event ID
     * @since 1.5
     */
    public Object getId() {
        return objectId;
    }

    /**
     * Relative time of event. Should NOT be used for ordering of events.
     *
     * @return Timestamp
     * @since 1.5
     */
    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public int compareTo(BaseEvent that) {
        return (int) (this.id - that.id);
    }

    @Override
    public int hashCode() {
        return objectId.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o == null || !(o instanceof BaseEvent)) {
            return false;
        }
        BaseEvent that = (BaseEvent) o;
        return this.id == that.id;
    }
}
