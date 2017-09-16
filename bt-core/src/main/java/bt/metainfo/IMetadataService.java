/*
 * Copyright (c) 2016—2017 Andrei Tomashpolskiy and individual contributors.
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

import java.io.InputStream;
import java.net.URL;

/**
 * Service for creating torrents from bencoded sources.
 *
 * @since 1.0
 */
public interface IMetadataService {

    /**
     * Builds a torrent object from its binary representation located at {@code url}.
     *
     * @param url Binary bencoded representation of a torrent object
     * @return Torrent object.
     * @since 1.0
     */
    Torrent fromUrl(URL url);

    /**
     * Builds a torrent object from its binary representation.
     *
     * @param in Input stream, containing a bencoded representation.
     *           Caller should close the stream after the method has returned.
     * @return Torrent object.
     * @since 1.0
     */
    Torrent fromInputStream(InputStream in);

    /**
     * Builds a torrent object from its binary representation.
     *
     * @param bs binary bencoded representation of a torrent object
     * @return Torrent object.
     * @since 1.0
     */
    Torrent fromByteArray(byte[] bs);
}
