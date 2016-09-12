package bt.tracker;

import bt.net.Origin;

import java.net.URL;
import java.util.Objects;

public class TrackerOrigin implements Origin {

    private URL trackerUrl;

    TrackerOrigin(URL trackerUrl) {
        this.trackerUrl = Objects.requireNonNull(trackerUrl, "Missing tracker URL");
    }

    @Override
    public int hashCode() {
        return trackerUrl.hashCode();
    }

    @Override
    public boolean equals(Object that) {

        if (that == null) {
            return false;
        } else if (that == this) {
            return true;
        }

        return (that instanceof TrackerOrigin) && trackerUrl.equals(((TrackerOrigin) that).trackerUrl);

    }
}
