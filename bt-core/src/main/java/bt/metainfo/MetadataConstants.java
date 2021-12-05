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

/**
 * Contains the string constants stored in a torrent file
 */
public class MetadataConstants {
    /**
     * <a href="https://www.bittorrent.org/beps/bep_0003.html">BEP-0003</a>
     */
    public static final String ANNOUNCE_KEY = "announce";

    /**
     * <a href="https://www.bittorrent.org/beps/bep_0012.html">BEP-0012</a>
     */
    public static final String ANNOUNCE_LIST_KEY = "announce-list";

    /**
     * <a href="https://www.bittorrent.org/beps/bep_0003.html">BEP-0003</a>
     */
    public static final String INFOMAP_KEY = "info";
    /**
     * <a href="https://www.bittorrent.org/beps/bep_0003.html">BEP-0003</a>
     */
    public static final String TORRENT_NAME_KEY = "name";
    /**
     * <a href="https://www.bittorrent.org/beps/bep_0003.html">BEP-0003</a>
     */
    public static final String CHUNK_SIZE_KEY = "piece length";
    /**
     * <a href="https://www.bittorrent.org/beps/bep_0003.html">BEP-0003</a>
     */
    public static final String CHUNK_HASHES_KEY = "pieces";
    /**
     * <a href="https://www.bittorrent.org/beps/bep_0003.html">BEP-0003</a>
     */
    public static final String TORRENT_SIZE_KEY = "length";
    /**
     * <a href="https://www.bittorrent.org/beps/bep_0003.html">BEP-0003</a>
     */
    public static final String FILES_KEY = "files";
    /**
     * <a href="https://www.bittorrent.org/beps/bep_0003.html">BEP-0003</a>
     */
    public static final String FILE_SIZE_KEY = "length";

    /**
     * <a href="https://www.bittorrent.org/beps/bep_0003.html">BEP-0003</a>
     */
    public static final String FILE_PATH_ELEMENTS_KEY = "path";

    /**
     * <a href="https://www.bittorrent.org/beps/bep_0027.html">BEP-0027</a>
     */
    public static final String PRIVATE_KEY = "private";


    /**
     * Not in protocol definition, but seems to be widely used
     */
    public static final String CREATION_DATE_KEY = "creation date";

    /**
     * Not in protocol definition, but seems to be widely used
     */
    public static final String CREATED_BY_KEY = "created by";
}
