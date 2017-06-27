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
import bt.torrent.TorrentRegistry;
import bt.tracker.AnnounceKey;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 *<p><b>Note that this class implements a service.
 * Hence, is not a part of the public API and is a subject to change.</b></p>
 */
public class MetadataService implements IMetadataService {
    private static final Logger LOGGER = LoggerFactory.getLogger(MetadataService.class);

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

    private TorrentRegistry torrentRegistry;
    private ConcurrentMap<TorrentId, TorrentMetadata> metadataMap;

    private BEObjectModel torrentModel;
    private Charset defaultCharset;

    @Inject
    public MetadataService(TorrentRegistry torrentRegistry) {
        this.torrentRegistry = torrentRegistry;
        this.metadataMap = new ConcurrentHashMap<>();
        this.defaultCharset = Charset.forName("UTF-8");

        try {
            try (InputStream in = MetadataService.class.getResourceAsStream("/metainfo.yml")) {
                this.torrentModel = new YamlBEObjectModelLoader().load(in);
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
        BEMap metadata = parser.readMap();

        ValidationResult validationResult = torrentModel.validate(metadata);;
        if (!validationResult.isSuccess()) {
            throw new BtException("Validation failed for torrent metainfo: "
                    + Arrays.toString(validationResult.getMessages().toArray()));
        }

        Map<String, BEObject<?>> root = metadata.getValue();
        BEMap infoDictionary;

        try {

            infoDictionary = (BEMap) root.get(INFOMAP_KEY);
            torrent.setTorrentId(TorrentId.fromBytes(CryptoUtil.getSha1Digest(infoDictionary.getContent())));

            Map<String, BEObject<?>> infoMap = infoDictionary.getValue();

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
                BigInteger torrentSize = BigInteger.ZERO;
                for (BEMap file : files) {

                    Map<String, BEObject<?>> fileMap = file.getValue();
                    DefaultTorrentFile torrentFile = new DefaultTorrentFile();

                    BigInteger fileSize = (BigInteger) fileMap.get(FILE_SIZE_KEY).getValue();
                    torrentFile.setSize(fileSize.longValueExact());
                    torrentSize = torrentSize.add(fileSize);

                    List<BEString> pathElements = (List<BEString>) fileMap.get(FILE_PATH_ELEMENTS_KEY).getValue();

                    torrentFile.setPathElements(pathElements.stream()
                            .map(bytes -> bytes.getValue(defaultCharset))
                            .collect(Collectors.toList()));

                    torrentFiles.add(torrentFile);
                }

                torrent.setFiles(torrentFiles);
                torrent.setSize(torrentSize.longValueExact());
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

        saveMetadata(torrent.getTorrentId(), buildMetadata(metadata, infoDictionary));

        return torrent;
    }

    private TorrentMetadata buildMetadata(BEMap metadata, BEMap infoDictionary) {
        return new BEncodedMetadata(metadata, infoDictionary);
    }

    private void saveMetadata(TorrentId torrentId, TorrentMetadata metadata) {
        if (metadataMap.put(torrentId, metadata) != null) {
            LOGGER.warn("Overwriting metadata for torrent ID: " + torrentId);
        }
    }

    @Override
    public TorrentMetadata getMetadata(Torrent torrent) {
        return getExistingMetadata(torrent.getTorrentId()).orElseGet(() -> buildMetadata(torrent));
    }

    @Override
    public Optional<TorrentMetadata> getMetadata(TorrentId torrentId) {
        Optional<TorrentMetadata> metadata = getExistingMetadata(torrentId);
        if (!metadata.isPresent()) {
            Optional<Torrent> torrent = torrentRegistry.getTorrent(torrentId);
            if (torrent.isPresent()) {
                metadata = Optional.of(buildMetadata(torrent.get()));
            }
        }
        return metadata;
    }

    private Optional<TorrentMetadata> getExistingMetadata(TorrentId torrentId) {
        return Optional.ofNullable(metadataMap.get(torrentId));
    }

    /**
     * Build ad-hoc metadata for a given torrent.
     */
    private TorrentMetadata buildMetadata(Torrent torrent) {
        // TODO: implement creating metadata on demand based on the provided torrent
        throw new UnsupportedOperationException();
    }
}
