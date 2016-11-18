package bt.tracker;

import java.net.URL;
import java.util.Collections;
import java.util.List;

/**
 * This class encapsulates information about trackers,
 * that can be used for getting a particular torrent.
 *
 * @since 1.0
 */
public class AnnounceKey {

    private final URL trackerUrl;
    private final List<List<URL>> trackerUrls;

    /**
     * Create a single-tracker announce key
     *
     * @since 1.0
     */
    public AnnounceKey(URL trackerUrl) {
        this.trackerUrl = trackerUrl;
        this.trackerUrls = null;
    }

    /**
     * Create a multi-tracker announce key
     * See BEP-12: Multitracker Metadata Extension for more details
     *
     * @param trackerUrls List of tiers of trackers (first list contains primary trackers,
     *                    subsequent lists contain backup trackers)
     * @since 1.0
     */
    public AnnounceKey(List<List<URL>> trackerUrls) {
        this.trackerUrl = null;
        this.trackerUrls = Collections.unmodifiableList(trackerUrls);
    }

    /**
     * @return true if this announce key supports multi-trackers
     * @since 1.0
     */
    public boolean isMultiKey() {
        return trackerUrls != null;
    }

    /**
     * @return Tracker URL if {@link #isMultiKey()} is false, null otherwise
     * @since 1.0
     */
    public URL getTrackerUrl() {
        return trackerUrl;
    }

    /**
     * @return List of Tracker tiers if {@link #isMultiKey()} is true, null otherwise
     * @since 1.0
     */
    public List<List<URL>> getTrackerUrls() {
        return trackerUrls;
    }

    @Override
    public String toString() {
        if (isMultiKey()) {
            return trackerUrls.toString();
        } else {
            return trackerUrl.toExternalForm();
        }
    }

    @Override
    public int hashCode() {
        return isMultiKey()? trackerUrls.hashCode() : trackerUrl.hashCode();
    }

    @Override
    public boolean equals(Object o) {

        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        AnnounceKey that = (AnnounceKey) o;

        return isMultiKey()? trackerUrls.equals(that.trackerUrls) : trackerUrl.equals(that.trackerUrl);
    }
}
