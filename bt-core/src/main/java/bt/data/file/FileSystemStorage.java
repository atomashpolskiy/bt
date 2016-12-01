package bt.data.file;

import bt.data.StorageUnit;
import bt.data.Storage;
import bt.metainfo.Torrent;
import bt.metainfo.TorrentFile;

import java.io.File;

public class FileSystemStorage implements Storage {

    private final File rootDirectory;
    private final PathNormalizer pathNormalizer;

    public FileSystemStorage(File rootDirectory) {
        this.rootDirectory = rootDirectory;
        this.pathNormalizer = new PathNormalizer();
    }

    @Override
    public StorageUnit getUnit(Torrent torrent, TorrentFile torrentFile) {

        File torrentDirectory;
        if (torrent.getFiles().size() == 1) {
            torrentDirectory = rootDirectory;
        } else {
            String normalizedName = pathNormalizer.normalize(torrent.getName());
            torrentDirectory = new File(rootDirectory, normalizedName);
        }

        String normalizedPath = pathNormalizer.normalize(torrentFile.getPathElements());
        return new FileSystemStorageUnit(torrentDirectory, normalizedPath, torrentFile.getSize());
    }
}
