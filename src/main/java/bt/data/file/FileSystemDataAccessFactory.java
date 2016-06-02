package bt.data.file;

import bt.data.DataAccess;
import bt.data.DataAccessFactory;
import bt.metainfo.Torrent;
import bt.metainfo.TorrentFile;

import java.io.File;

public class FileSystemDataAccessFactory implements DataAccessFactory {

    private final File rootDirectory;

    public FileSystemDataAccessFactory(File rootDirectory) {
        // TODO: there should be a service to track directories for each torrent
        this.rootDirectory = rootDirectory;
    }

    @Override
    public DataAccess getOrCreateDataAccess(Torrent torrent, TorrentFile torrentFile) {

        File torrentDirectory;
        if (torrent.getFiles().size() == 1) {
            torrentDirectory = rootDirectory;
        } else {
            torrentDirectory = new File(rootDirectory, torrent.getName());
        }

        return new FileSystemDataAccess(torrentDirectory, torrentFile.getPathElements(), torrentFile.getSize());
    }
}
