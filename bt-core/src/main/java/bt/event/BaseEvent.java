/*
 * Copyright (c) 2016—2017 Andrei Tomashpolskiy and individual contributors.
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

package bt.event;

/**
 * Globally ordered, timestamped event.
 *
 * {@link #compareTo(BaseEvent)} orders events according to natural ordering of their IDs.
 *
 * @since 1.5
 */
public abstract class BaseEvent implements Comparable<BaseEvent>, Event {

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

    @Override
    public Object getId() {
        return objectId;
    }

    @Override
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

    @Override
    public String toString() {
        return "[" + this.getClass().getSimpleName() + "] id {" + id + "}, timestamp {" + timestamp + "}";
    }
}
