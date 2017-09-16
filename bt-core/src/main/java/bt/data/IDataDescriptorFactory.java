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

package bt.data;

import bt.metainfo.Torrent;

/**
 * Factory of torrent data descriptors.
 *
 * @since 1.0
 */
public interface IDataDescriptorFactory {

    /**
     * Create a data descriptor for a given torrent
     * with the storage provided as the data back-end.
     *
     * It's up to implementations to decide,
     * whether storage units will be allocated eagerly
     * upon creation of data descriptor or delayed
     * until data access is requested.
     *
     * @return Data descriptor
     * @since 1.0
     */
    DataDescriptor createDescriptor(Torrent torrent, Storage storage);
}
