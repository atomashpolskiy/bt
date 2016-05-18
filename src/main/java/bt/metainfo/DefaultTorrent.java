package bt.metainfo;

import bt.BtException;

import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

public class DefaultTorrent implements Torrent {

    private static final int HASH_LENGTH = 20;

    private URL trackerUrl;
    private String name;
    private long chunkSize;
    private byte[] chunkHashes;
    private long size;
    private List<TorrentFile> files;

    @Override
    public URL getTrackerUrl() {
        return trackerUrl;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public long getChunkSize() {
        return chunkSize;
    }

    @Override
    public Iterable<byte[]> getChunkHashes() {

        return () -> new Iterator<byte[]>() {

            int read;

            @Override
            public boolean hasNext() {
                return read < chunkHashes.length;
            }

            @Override
            public byte[] next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                int start = read;
                read += HASH_LENGTH;
                return Arrays.copyOfRange(chunkHashes, start, read);
            }
        };
    }

    @Override
    public long getSize() {
        return size;
    }

    @Override
    public List<TorrentFile> getFiles() {
        return files == null? null : Collections.unmodifiableList(files);
    }

    void setTrackerUrl(URL trackerUrl) {
        this.trackerUrl = trackerUrl;
    }

    void setName(String name) {
        this.name = name;
    }

    void setChunkSize(long chunkSize) {
        if (chunkSize <= 0) {
            throw new BtException("Invalid chunk size: " + chunkSize);
        }
        this.chunkSize = chunkSize;
    }

    void setSize(long size) {
        if (size <= 0) {
            throw new BtException("Invalid torrent size: " + size);
        }
        this.size = size;
    }

    void setFiles(List<TorrentFile> files) {
        if (files == null || files.isEmpty()) {
            throw new BtException("Can't create torrent without files");
        }
        this.files = files;
    }

    public void setChunkHashes(byte[] chunkHashes) {
        if (chunkHashes.length % HASH_LENGTH != 0) {
            throw new BtException("Invalid chunk hashes string -- length (" + chunkHashes.length
                    + ") is not divisible by " + HASH_LENGTH);
        }
        this.chunkHashes = chunkHashes;
    }
}
