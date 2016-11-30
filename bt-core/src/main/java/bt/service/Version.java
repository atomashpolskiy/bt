package bt.service;

/**
 * Application version info.
 *
 * @since 1.0
 */
public class Version {

    private final int major;
    private final int minor;
    private final boolean snapshot;

    /**
     * @since 1.0
     */
    public Version(int major, int minor, boolean snapshot) {
        this.major = major;
        this.minor = minor;
        this.snapshot = snapshot;
    }

    /**
     * @since 1.0
     */
    public int getMajor() {
        return major;
    }

    /**
     * @since 1.0
     */
    public int getMinor() {
        return minor;
    }

    /**
     * @since 1.0
     */
    public boolean isSnapshot() {
        return snapshot;
    }

    @Override
    public String toString() {
        String version = major + "." + minor;
        if (snapshot) {
            version += " (Snapshot)";
        }
        return version;
    }
}
