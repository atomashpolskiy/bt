package bt.data;

import bt.metainfo.DefaultTorrentFile;
import bt.metainfo.Torrent;
import bt.metainfo.TorrentFile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

class DataDescriptor implements IDataDescriptor {

    private List<IChunkDescriptor> chunkDescriptors;

    public DataDescriptor(DataAccessFactory dataAccessFactory, Torrent torrent, long blockSize) {

        List<TorrentFile> torrentFiles = torrent.getFiles();
        int filesCount;
        if (torrentFiles == null) {
            // single-file torrent
            // TODO: probably this needs to be abstracted by the torrent itself?
            DefaultTorrentFile torrentFile = new DefaultTorrentFile();
            torrentFile.setSize(torrent.getSize());
            // TODO: name can be null according to the spec...
            torrentFile.setPathElements(Collections.singletonList(torrent.getName()));
            torrentFiles = Collections.singletonList(torrentFile);
            filesCount = 1;
        } else {
            filesCount = torrentFiles.size();
        }

        long totalSize = torrent.getSize();
        long chunkSize = torrent.getChunkSize();

        chunkDescriptors = new ArrayList<>((int) Math.ceil(totalSize / chunkSize) + 1);
        Iterator<byte[]> chunkHashes = torrent.getChunkHashes().iterator();
        DataAccess[] files = new DataAccess[filesCount];

        long chunkOffset = 0,
             totalSizeOfFiles = 0;

        int firstFileInChunkIndex = 0;

        for (int currentFileIndex = 0; currentFileIndex < filesCount; currentFileIndex++) {

            TorrentFile torrentFile = torrentFiles.get(currentFileIndex);

            long fileSize = torrentFile.getSize();
            files[currentFileIndex] = dataAccessFactory.getOrCreateDataAccess(torrent, torrentFile);
            totalSizeOfFiles += fileSize;

            if (totalSizeOfFiles >= chunkSize) {

                do {
                    long limitInCurrentFile = chunkSize - (totalSizeOfFiles - fileSize);

                    chunkDescriptors.add(new ChunkDescriptor(
                            Arrays.asList(Arrays.copyOfRange(files, firstFileInChunkIndex, currentFileIndex + 1)),
                            chunkOffset, limitInCurrentFile, chunkHashes.next(), blockSize
                    ));

                    firstFileInChunkIndex = currentFileIndex;
                    chunkOffset = limitInCurrentFile;

                    totalSizeOfFiles -= chunkSize;

                // if surplus is bigger than the chunk size,
                // then we need to catch up and create more than one chunk
                } while (totalSizeOfFiles >= chunkSize);

                if (totalSizeOfFiles == 0) {
                    // no bytes left in the current file,
                    // new chunk will begin with the next file
                    firstFileInChunkIndex++;
                    chunkOffset = 0;
                }
            }
        }
    }

    public List<IChunkDescriptor> getChunkDescriptors() {
        return chunkDescriptors;
    }

    @Override
    public String toString() {

        // TODO: remove me or move to a different method
        StringBuilder buf = new StringBuilder();
        int i = 0;
        for (IChunkDescriptor chunk : chunkDescriptors) {
            buf.append("Chunk #" + ++i + ": " + chunk.toString() + "\n");
        }
        return buf.toString();
    }
}
