package bt.metainfo;

import bt.BtException;
import bt.bencoding.BEParser;
import bt.bencoding.BEType;
import bt.bencoding.model.BEList;
import bt.bencoding.model.BEMap;
import bt.bencoding.model.BEObject;
import bt.bencoding.model.BEObjectModel;
import bt.bencoding.model.BEString;
import bt.bencoding.model.ValidationResult;
import bt.bencoding.model.YamlBEObjectModelLoader;
import bt.service.CryptoUtil;
import bt.tracker.AnnounceKey;

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

/**
 *<p><b>Note that this class implements a service.
 * Hence, is not a part of the public API and is a subject to change.</b></p>
 */
public class MetadataService implements IMetadataService {

    private static final String ANNOUNCE_KEY = "announce";
    private static final String ANNOUNCE_LIST_KEY = "announce-list";
    private static final String INFOMAP_KEY = "info";
    private static final String TORRENT_NAME_KEY = "name";
    private static final String CHUNK_SIZE_KEY = "piece length";
    private static final String CHUNK_HASHES_KEY = "pieces";
    private static final String TORRENT_SIZE_KEY = "length";
    private static final String FILES_KEY = "files";
    private static final String FILE_SIZE_KEY = "length";
    private static final String FILE_PATH_ELEMENTS_KEY = "path";
    private static final String PRIVATE_KEY = "private";

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
    public Torrent fromInputStream(InputStream in) {
        try (BEParser parser = new BEParser(in)) {
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

            BEMap info = (BEMap) root.get(INFOMAP_KEY);
            torrent.setTorrentId(TorrentId.fromBytes(CryptoUtil.getSha1Digest(info.getContent())));

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

            boolean isPrivate = false;
            if (infoMap.get(PRIVATE_KEY) != null) {
                if (BigInteger.ONE.equals(infoMap.get(PRIVATE_KEY).getValue())) {
                    torrent.setPrivate(true);
                    isPrivate = true;
                }
            }

            AnnounceKey announceKey = null;
            // TODO: support for private torrents with multiple trackers
            if (!isPrivate && root.containsKey(ANNOUNCE_LIST_KEY)) {

                List<List<String>> trackerUrls;

                BEList announceList = (BEList) root.get(ANNOUNCE_LIST_KEY);
                List<BEList> tierList = (List<BEList>) announceList.getValue();
                trackerUrls = new ArrayList<>(tierList.size() + 1);
                for (BEList tierElement : tierList) {

                    List<String> tierTackerUrls;

                    List<BEString> trackerUrlList = (List<BEString>) tierElement.getValue();
                    tierTackerUrls = new ArrayList<>(trackerUrlList.size() + 1);
                    for (BEString trackerUrlElement : trackerUrlList) {
                        tierTackerUrls.add(trackerUrlElement.getValue(defaultCharset));
                    }
                    trackerUrls.add(tierTackerUrls);
                }

                announceKey = new AnnounceKey(trackerUrls);

            } else if (root.containsKey(ANNOUNCE_KEY)) {
                byte[] trackerUrl = (byte[]) root.get(ANNOUNCE_KEY).getValue();
                announceKey = new AnnounceKey(new String(trackerUrl, defaultCharset));
            }

            if (announceKey != null) {
                torrent.setAnnounceKey(announceKey);
            }

        } catch (Exception e) {
            throw new BtException("Invalid metainfo format", e);
        }
        return torrent;
    }
}
