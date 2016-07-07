package bt.metainfo;

import bt.BtException;
import bt.service.CryptoUtil;
import bt.bencoding.BEParser;
import bt.bencoding.BEType;
import bt.bencoding.model.BEMap;
import bt.bencoding.model.BEObject;
import bt.bencoding.model.BEObjectModel;
import bt.bencoding.model.BEString;
import bt.bencoding.model.ValidationResult;
import bt.bencoding.model.YamlBEObjectModelLoader;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
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
        BEMap beMap = parser.readMap();

        ValidationResult validationResult = torrentModel.validate(beMap);;
        if (!validationResult.isSuccess()) {
            throw new BtException("Validation failed for torrent metainfo: "
                    + Arrays.toString(validationResult.getMessages().toArray()));
        }

        Map<String, BEObject<?>> root = beMap.getValue();

        try {
            byte[] trackerUrl = (byte[]) root.get(TRACKER_URL_KEY).getValue();
            torrent.setTrackerUrl(new URL(new String(trackerUrl, defaultCharset)));

            BEMap info = (BEMap) root.get(INFOMAP_KEY);
            torrent.setInfoHash(CryptoUtil.getSha1Digest(info.getContent()));

            Map<String, BEObject<?>> infoMap = info.getValue();

            if (infoMap.get(TORRENT_NAME_KEY) != null) {
                byte[] name = (byte[]) infoMap.get(TORRENT_NAME_KEY).getValue();
                torrent.setName(new String(name, defaultCharset));
            }

            BigInteger chunkSize = (BigInteger) infoMap.get(CHUNK_SIZE_KEY).getValue();
            torrent.setChunkSize(chunkSize.longValueExact());

            byte[] chunkHashes = (byte[]) infoMap.get(CHUNK_HASHES_KEY).getValue();
            torrent.setChunkHashes(chunkHashes);

            if (infoMap.get(TORRENT_SIZE_KEY) != null) {
                BigInteger torrentSize = (BigInteger) infoMap.get(TORRENT_SIZE_KEY).getValue();
                torrent.setSize(torrentSize.longValueExact());

            } else {
                List<BEMap> files = (List<BEMap>) infoMap.get(FILES_KEY).getValue();
                List<TorrentFile> torrentFiles = new ArrayList<>(files.size() + 1);
                for (BEMap file : files) {

                    Map<String, BEObject<?>> fileMap = file.getValue();
                    DefaultTorrentFile torrentFile = new DefaultTorrentFile();

                    BigInteger fileSize = (BigInteger) fileMap.get(FILE_SIZE_KEY).getValue();
                    torrentFile.setSize(fileSize.longValueExact());

                    List<BEString> pathElements = (List<BEString>) fileMap.get(FILE_PATH_ELEMENTS_KEY).getValue();

                    torrentFile.setPathElements(pathElements.stream()
                            .map(bytes -> bytes.getValue(defaultCharset))
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
