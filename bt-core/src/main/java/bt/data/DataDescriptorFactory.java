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
 *<p><b>Note that this class implements a service.
 * Hence, is not a part of the public API and is a subject to change.</b></p>
 */
public class DataDescriptorFactory implements IDataDescriptorFactory {

    private ChunkVerifier verifier;
    private int transferBlockSize;

    public DataDescriptorFactory(ChunkVerifier verifier,
                                 int transferBlockSize) {
        this.verifier = verifier;
        this.transferBlockSize = transferBlockSize;
    }

    @Override
    public DataDescriptor createDescriptor(Torrent torrent, Storage storage) {
        return new DefaultDataDescriptor(storage, torrent, verifier, transferBlockSize);
    }
}
