package bt;

/**
 * Builds a client and attaches it to the provided runtime.
 *
 * @since 1.1
 */
public class BtClientBuilder extends TorrentClientBuilder<BtClientBuilder> {
    // this class is basically a convenient TorrentClientBuilder without generic parameters

    /**
     * @since 1.4
     */
    protected BtClientBuilder() {
    }
}
