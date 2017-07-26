package bt.data;

import bt.data.file.FileSystemStorage;
import bt.metainfo.Torrent;
import bt.metainfo.TorrentFile;
import org.junit.rules.ExternalResource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

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
        try {
            deleteRecursive(rootDirectory);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void deleteRecursive(File file) throws IOException {
        if (!file.exists()) {
            return;
        }

        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }

        Files.delete(file.toPath());
    }
}
