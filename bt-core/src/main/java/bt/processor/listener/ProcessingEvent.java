package bt.processor.listener;

/**
 * @since 1.5
 */
public enum ProcessingEvent {

    /**
     * Torrent metadata has been fetched.
     *
     * @since 1.5
     */
    TORRENT_FETCHED,

    /**
     * All data has been downloaded.
     *
     * @since 1.5
     */
    DOWNLOAD_COMPLETE
}
