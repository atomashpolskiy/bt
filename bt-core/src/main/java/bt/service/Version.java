package bt.service;

public class Version {

    private final int major;
    private final int minor;
    private final boolean snapshot;

    public Version(int major, int minor, boolean snapshot) {
        this.major = major;
        this.minor = minor;
        this.snapshot = snapshot;
    }

    public int getMajor() {
        return major;
    }

    public int getMinor() {
        return minor;
    }

    public boolean isSnapshot() {
        return snapshot;
    }
}
