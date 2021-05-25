package bt.torrent.fileselector;

/**
 * The possible statuses to update a file with while a torrent download is running. This is a separate
 * enum because skipping or unskipping files in the middle of a download is not yet supported.
 */
public enum UpdatedFilePriority {
    NORMAL_PRIORITY(FilePriority.NORMAL_PRIORITY),
    HIGH_PRIORITY(FilePriority.HIGH_PRIORITY);

    private final FilePriority filePriority;

    UpdatedFilePriority(FilePriority filePriority) {
        this.filePriority = filePriority;
    }

    public FilePriority toSelectionResult() {
        return filePriority;
    }
}
