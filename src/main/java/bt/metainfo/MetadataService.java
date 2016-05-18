package bt.metainfo;

import bt.BtException;
import bt.bencoding.BEParser;
import bt.bencoding.BEType;

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

    private Charset defaultCharset;

    public MetadataService() {
        defaultCharset = Charset.forName("UTF-8");
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

    @SuppressWarnings("rawtypes")
    private Torrent buildTorrent(BEParser parser) {

        if (parser.readType() != BEType.MAP) {
            throw new BtException("Invalid metainfo format -- expected a map, got: "
                    + parser.readType().name().toLowerCase());
        }

        DefaultTorrent torrent = new DefaultTorrent();
        Map<String, Object> root = parser.readMap();
        try {
            String trackerUrl = new String(readNotNull(root, byte[].class, TRACKER_URL_KEY), defaultCharset);
            torrent.setTrackerUrl(new URL(trackerUrl));

            Map info = readNotNull(root, Map.class, INFOMAP_KEY);

            byte[] name = cast(byte[].class, TORRENT_NAME_KEY, info.get(TORRENT_NAME_KEY));
            if (name != null) {
                torrent.setName(new String(name, defaultCharset));
            }

            BigInteger chunkSize = readNotNull(info, BigInteger.class, CHUNK_SIZE_KEY);
            torrent.setChunkSize(chunkSize.longValueExact());

            byte[] chunkHashes = readNotNull(info, byte[].class, CHUNK_HASHES_KEY);
            torrent.setChunkHashes(chunkHashes);

            BigInteger torrentSize = cast(BigInteger.class, TORRENT_SIZE_KEY, info.get(TORRENT_SIZE_KEY));
            if (torrentSize != null) {
                torrent.setSize(torrentSize.longValueExact());

            } else {
                List<Map> files = castList(Map.class, readNotNull(info, List.class, FILES_KEY));
                List<TorrentFile> torrentFiles = new ArrayList<>(files.size() + 1);
                for (Map file : files) {

                    DefaultTorrentFile torrentFile = new DefaultTorrentFile();

                    BigInteger fileSize = readNotNull(file, BigInteger.class, FILE_SIZE_KEY);
                    torrentFile.setSize(fileSize.longValueExact());

                    List<byte[]> pathElements = castList(byte[].class,
                            readNotNull(file, List.class, FILE_PATH_ELEMENTS_KEY));

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

    @SuppressWarnings("rawtypes")
    private <T> T readNotNull(Map map, Class<T> type, Object key) throws Exception {
        Object value = map.get(key);
        if (value == null) {
            throw new Exception("Value is missing for key: " + key);
        }
        return cast(type, key, value);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private <T> T cast(Class<T> type, Object key, Object value) throws Exception {
        if (value == null) {
            return null;
        }
        if (!type.isAssignableFrom(value.getClass())) {
            throw new Exception("Value has invalid type for key: " + key + " -- expected '"
                    + type.getName() + "', got: " + value.getClass().getName());
        }
        return (T) value;
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> castList(Class<T> elementType, List<?> list) throws Exception {
        for (Object element : list) {
            if (!elementType.isAssignableFrom(element.getClass())) {
                throw new Exception("List element has invalid type -- expected '"
                    + elementType.getName() + "', got: " + element.getClass().getName());
            }
        }
        return (List<T>) list;
    }
}
