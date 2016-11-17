package bt.data.file;

import bt.data.StorageUnit;
import bt.data.Storage;
import bt.metainfo.Torrent;
import bt.metainfo.TorrentFile;

import java.io.File;

public class FileSystemStorage implements Storage {

    private final File rootDirectory;

    public FileSystemStorage(File rootDirectory) {
        // TODO: there should be a service to track directories for each torrent
        this.rootDirectory = rootDirectory;
    }

    @Override
    public StorageUnit getUnit(Torrent torrent, TorrentFile torrentFile) {

        File torrentDirectory;
        if (torrent.getFiles().size() == 1) {
            torrentDirectory = rootDirectory;
        } else {
            torrentDirectory = new File(rootDirectory, torrent.getName());
        }

        return new FileSystemStorageUnit(torrentDirectory, torrentFile.getPathElements(), torrentFile.getSize());
    }
}
