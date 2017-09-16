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

package bt.torrent.data;

import bt.data.ChunkVerifier;
import bt.data.DataDescriptor;
import bt.service.IRuntimeLifecycleBinder;

/**
 *<p><b>Note that this class implements a service.
 * Hence, is not a part of the public API and is a subject to change.</b></p>
 */
public class DataWorkerFactory implements IDataWorkerFactory {

    private IRuntimeLifecycleBinder lifecycleBinder;
    private ChunkVerifier verifier;
    private int maxIOQueueSize;

    public DataWorkerFactory(IRuntimeLifecycleBinder lifecycleBinder, ChunkVerifier verifier, int maxIOQueueSize) {
        this.lifecycleBinder = lifecycleBinder;
        this.verifier = verifier;
        this.maxIOQueueSize = maxIOQueueSize;
    }

    @Override
    public DataWorker createWorker(DataDescriptor dataDescriptor) {
        return new DefaultDataWorker(lifecycleBinder, dataDescriptor, verifier, maxIOQueueSize);
    }
}
