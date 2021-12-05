/*
 * Copyright (c) 2016—2021 Andrei Tomashpolskiy and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package bt.metainfo;

import bt.BtException;
import bt.bencoding.BEType;
import bt.bencoding.model.BEObject;
import bt.bencoding.model.BEObjectModel;
import bt.bencoding.model.ValidationResult;
import bt.bencoding.model.YamlBEObjectModelLoader;
import bt.bencoding.serializers.BEParser;
import bt.bencoding.types.BEInteger;
import bt.bencoding.types.BEList;
import bt.bencoding.types.BEMap;
import bt.bencoding.types.BEString;
import bt.service.CryptoUtil;
import bt.tracker.AnnounceKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * <p><b>Note that this class implements a service.
 * Hence, is not a part of the public API and is a subject to change.</b></p>
 */
public class MetadataService implements IMetadataService {
    private static final Logger LOGGER = LoggerFactory.getLogger(MetadataService.class);

    private BEObjectModel torrentModel;
    private BEObjectModel infodictModel;

    public MetadataService() {
        try {
            try (InputStream in = MetadataService.class.getResourceAsStream("/metainfo.yml")) {
                this.torrentModel = new YamlBEObjectModelLoader().load(in);
            }
            try (InputStream in = MetadataService.class.getResourceAsStream("/infodict.yml")) {
                this.infodictModel = new YamlBEObjectModelLoader().load(in);
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

        BEMap metadata = parser.readMap();

        ValidationResult validationResult = torrentModel.validate(metadata);
        ;
        if (!validationResult.isSuccess()) {
            ValidationResult infodictValidationResult = infodictModel.validate(metadata);
            if (!infodictValidationResult.isSuccess()) {
                throw new BtException("Validation failed for torrent metainfo:\n1. Standard torrent model: "
                        + Arrays.toString(validationResult.getMessages().toArray())
                        + "\n2. Standalone info dictionary model: " +
                        Arrays.toString(infodictValidationResult.getMessages().toArray()));
            }
        }

        BEMap infoDictionary;
        TorrentSource source;

        Map<String, BEObject<?>> root = metadata.getValue();
        if (root.containsKey(MetadataConstants.INFOMAP_KEY)) {
            // standard BEP-3 format
            infoDictionary = (BEMap) root.get(MetadataConstants.INFOMAP_KEY);
            source = new TorrentSource() {
                @Override
                public Optional<byte[]> getMetadata() {
                    return Optional.of(metadata.getContent());
                }

                @Override
                public byte[] getExchangedMetadata() {
                    return infoDictionary.getContent();
                }
            };
        } else {
            // BEP-9 exchanged metadata (just the info dictionary)
            infoDictionary = metadata;
            source = new TorrentSource() {
                @Override
                public Optional<byte[]> getMetadata() {
                    return Optional.empty();
                }

                @Override
                public byte[] getExchangedMetadata() {
                    return infoDictionary.getContent();
                }
            };
        }

        DefaultTorrent torrent = new DefaultTorrent(source);

        try {
            torrent.setTorrentId(TorrentId.fromBytes(CryptoUtil.getSha1Digest(infoDictionary.getContent())));

            Map<String, BEObject<?>> infoMap = infoDictionary.getValue();

            if (infoMap.get(MetadataConstants.TORRENT_NAME_KEY) != null) {
                byte[] name = (byte[]) infoMap.get(MetadataConstants.TORRENT_NAME_KEY).getValue();
                torrent.setName(new String(name, StandardCharsets.UTF_8));
            }

            BEInteger chunkSize = (BEInteger) infoMap.get(MetadataConstants.CHUNK_SIZE_KEY);
            torrent.setChunkSize(chunkSize.longValueExact());

            byte[] chunkHashes = (byte[]) infoMap.get(MetadataConstants.CHUNK_HASHES_KEY).getValue();
            torrent.setChunkHashes(chunkHashes);

            if (infoMap.get(MetadataConstants.TORRENT_SIZE_KEY) != null) {
                BEInteger torrentSize = (BEInteger) infoMap.get(MetadataConstants.TORRENT_SIZE_KEY);
                torrent.setSize(torrentSize.longValueExact());

            } else {
                List<BEMap> files = (List<BEMap>) infoMap.get(MetadataConstants.FILES_KEY).getValue();
                List<TorrentFile> torrentFiles = new ArrayList<>(files.size() + 1);
                long torrentSize = 0;
                for (BEMap file : files) {
                    Map<String, BEObject<?>> fileMap = file.getValue();

                    Number fileSize = (Number) fileMap.get(MetadataConstants.FILE_SIZE_KEY).getValue();

                    List<BEString> pathElements =
                            (List<BEString>) fileMap.get(MetadataConstants.FILE_PATH_ELEMENTS_KEY).getValue();

                    List<String> elementsList = pathElements.stream()
                            .map(BEString::getValueAsString)
                            .collect(Collectors.toList());

                    DefaultTorrentFile torrentFile = new DefaultTorrentFile(fileSize.longValue(), elementsList);
                    torrentSize = Math.addExact(torrentSize, torrentFile.getSize());

                    torrentFiles.add(torrentFile);
                }

                torrent.setFiles(torrentFiles);
                torrent.setSize(torrentSize);
            }

            boolean isPrivate = false;
            final BEInteger privateFlag = (BEInteger) infoMap.get(MetadataConstants.PRIVATE_KEY);
            if (privateFlag != null) {
                if (1L == privateFlag.longValueExact()) {
                    torrent.setPrivate(true);
                    isPrivate = true;
                }
            }

            if (root.get(MetadataConstants.CREATION_DATE_KEY) != null) {
                BEInteger epochSecond = (BEInteger) root.get(MetadataConstants.CREATION_DATE_KEY);
                // TODO: some torrents contain bogus values here (like 101010101010), which causes an exception
                try {
                    torrent.setCreationDate(Instant.ofEpochSecond(epochSecond.getValue().longValue()));
                } catch (DateTimeException ex) {
                    System.out.println("Warning: could not set invalid creation date: " + epochSecond);
                }
            }

            if (root.get(MetadataConstants.CREATED_BY_KEY) != null) {
                byte[] createdBy = (byte[]) root.get(MetadataConstants.CREATED_BY_KEY).getValue();
                torrent.setCreatedBy(new String(createdBy, StandardCharsets.UTF_8));
            }

            AnnounceKey announceKey = null;
            // TODO: support for private torrents with multiple trackers
            if (!isPrivate && root.containsKey(MetadataConstants.ANNOUNCE_LIST_KEY)) {

                List<List<String>> trackerUrls;

                BEList announceList = (BEList) root.get(MetadataConstants.ANNOUNCE_LIST_KEY);
                List<BEList> tierList = (List<BEList>) announceList.getValue();
                trackerUrls = new ArrayList<>(tierList.size() + 1);
                for (BEList tierElement : tierList) {

                    List<String> tierTrackerUrls;

                    List<BEString> trackerUrlList = (List<BEString>) tierElement.getValue();
                    tierTrackerUrls = new ArrayList<>(trackerUrlList.size() + 1);
                    for (BEString trackerUrlElement : trackerUrlList) {
                        tierTrackerUrls.add(trackerUrlElement.getValueAsString());
                    }
                    trackerUrls.add(tierTrackerUrls);
                }

                announceKey = new AnnounceKey(trackerUrls);

            } else if (root.containsKey(MetadataConstants.ANNOUNCE_KEY)) {
                byte[] trackerUrl = (byte[]) root.get(MetadataConstants.ANNOUNCE_KEY).getValue();
                announceKey = new AnnounceKey(new String(trackerUrl, StandardCharsets.UTF_8));
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
