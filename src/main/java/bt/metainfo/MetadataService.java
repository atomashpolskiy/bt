package bt.metainfo;

import bt.BtException;
import bt.bencoding.BEParser;
import bt.bencoding.BEType;
import bt.bencoding.model.BEObjectModel;
import bt.bencoding.model.YamlBEObjectModelLoader;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MetadataService implements IMetadataService {

    private static final String TRACKER_URL_KEY = "announce";
    private static final String INFOMAP_KEY = "info";
    private static final String TORRENT_NAME_KEY = "name";
    private static final String CHUNK_SIZE_KEY = "piece length";
    private static final String CHUNK_HASHES_KEY = "pieces";
    private static final String TORRENT_SIZE_KEY = "length";
    private static final String FILES_KEY = "files";
    private static final String FILE_SIZE_KEY = "length";
    private static final String FILE_PATH_ELEMENTS_KEY = "path";

    private BEObjectModel torrentModel;
    private Charset defaultCharset;

    public MetadataService() {

        defaultCharset = Charset.forName("UTF-8");

        try {
            try (InputStream in = MetadataService.class.getResourceAsStream("/metainfo.yml")) {
                torrentModel = new YamlBEObjectModelLoader().load(in);
            }
        } catch (IOException e) {
            throw new BtException("Failed to create metadata service", e);
        }
    }

    @Override
    public Torrent fromUrl(URL url) {
        try (BEParser parser = new BEParser(url)) {
            return buildTorrent(parser);
        }
    }

    @Override
    public Torrent fromByteArray(byte[] bs) {
        try (BEParser parser = new BEParser(bs)) {
            return buildTorrent(parser);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Torrent buildTorrent(BEParser parser) {

        if (parser.readType() != BEType.MAP) {
            throw new BtException("Invalid metainfo format -- expected a map, got: "
                    + parser.readType().name().toLowerCase());
        }

        DefaultTorrent torrent = new DefaultTorrent();
        Map<String, Object> root = parser.readMap();
        torrentModel.validate(root);

        try {
            byte[] trackerUrl = (byte[]) root.get(TRACKER_URL_KEY);
            torrent.setTrackerUrl(new URL(new String(trackerUrl, defaultCharset)));

            Map info = (Map) root.get(INFOMAP_KEY);

            byte[] name = (byte[]) info.get(TORRENT_NAME_KEY);
            if (name != null) {
                torrent.setName(new String(name, defaultCharset));
            }

            BigInteger chunkSize = (BigInteger) info.get(CHUNK_SIZE_KEY);
            torrent.setChunkSize(chunkSize.longValueExact());

            byte[] chunkHashes = (byte[]) info.get(CHUNK_HASHES_KEY);
            torrent.setChunkHashes(chunkHashes);

            BigInteger torrentSize = (BigInteger) info.get(TORRENT_SIZE_KEY);
            if (torrentSize != null) {
                torrent.setSize(torrentSize.longValueExact());

            } else {
                List<Map> files = (List<Map>) info.get(FILES_KEY);
                List<TorrentFile> torrentFiles = new ArrayList<>(files.size() + 1);
                for (Map file : files) {

                    DefaultTorrentFile torrentFile = new DefaultTorrentFile();

                    BigInteger fileSize = (BigInteger) file.get(FILE_SIZE_KEY);
                    torrentFile.setSize(fileSize.longValueExact());

                    List<byte[]> pathElements = (List<byte[]>) file.get(FILE_PATH_ELEMENTS_KEY);

                    torrentFile.setPathElements(pathElements.stream()
                            .map(bytes -> new String(bytes, defaultCharset))
                            .collect(Collectors.toList()));

                    torrentFiles.add(torrentFile);
                }

                torrent.setFiles(torrentFiles);
            }
        } catch (Exception e) {
            throw new BtException("Invalid metainfo format", e);
        }
        return torrent;
    }
}
