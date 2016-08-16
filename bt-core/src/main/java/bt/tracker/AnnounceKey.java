package bt.tracker;

import java.net.URL;
import java.util.Collections;
import java.util.List;

public class AnnounceKey {

    private final URL trackerUrl;
    private final List<List<URL>> trackerUrls;

    public AnnounceKey(URL trackerUrl) {
        this.trackerUrl = trackerUrl;
        this.trackerUrls = null;
    }

    public AnnounceKey(List<List<URL>> trackerUrls) {
        this.trackerUrl = null;
        this.trackerUrls = Collections.unmodifiableList(trackerUrls);
    }

    public boolean isMultiKey() {
        return trackerUrls != null;
    }

    public URL getTrackerUrl() {
        return trackerUrl;
    }

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
