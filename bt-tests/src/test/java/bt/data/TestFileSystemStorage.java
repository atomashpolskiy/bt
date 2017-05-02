package bt.data;

import bt.data.file.FileSystemStorage;
import bt.metainfo.Torrent;
import bt.metainfo.TorrentFile;
import org.junit.rules.ExternalResource;

import java.io.File;

// TODO: replace this with in-memory storage
class TestFileSystemStorage extends ExternalResource implements Storage {

    private static final String ROOT_PATH = "target/rt";

    private File rootDirectory;
    private Storage delegate;
    private final Object lock;

    public TestFileSystemStorage() {
        rootDirectory = new File(ROOT_PATH);
        if (!rootDirectory.mkdirs()) {
            throw new RuntimeException("Failed to create directories: " + rootDirectory);
        }
        lock = new Object();
    }

    @Override
    public StorageUnit getUnit(Torrent torrent, TorrentFile torrentFile) {
        return getDelegate().getUnit(torrent, torrentFile);
    }

    private Storage getDelegate() {
        if (delegate == null) {
            synchronized (lock) {
                if (delegate == null) {
                    delegate = new FileSystemStorage(rootDirectory);
                }
            }
        }
        return delegate;
    }

    public File getRoot() {
        return rootDirectory;
    }

    @Override
    protected void after() {
        cleanup();
    }

    private void cleanup() {
        if (!deleteRecursive(rootDirectory)) {
            throw new RuntimeException("Failed to delete directory: " + rootDirectory);
        }
    }

    private boolean deleteRecursive(File file) {
        boolean deleted = true;
        if (file.isDirectory()) {
            for (File child : file.listFiles()) {
                deleted = deleted && deleteRecursive(child);
            }
        }
        return deleted && file.delete();
    }
}
