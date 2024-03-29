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

package bt.torrent;

import bt.data.DataDescriptor;

import java.util.Optional;

class DefaultTorrentDescriptor implements TorrentDescriptor {

    // !! this can be null in case with magnets (and in the beginning of processing) !!
    private volatile DataDescriptor dataDescriptor;
    // !! this can be null at the beginning of processing !!
    private volatile TorrentSessionState sessionState;

    private volatile boolean active;

    DefaultTorrentDescriptor() {
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public synchronized void start() {
        active = true;
    }

    @Override
    public synchronized void stop() {
        active = false;
    }

    @Override
    public void complete() {
        // do nothing
        // TODO: should this be deprecated in TorrentDescriptor interface?
    }

    @Override
    public DataDescriptor getDataDescriptor() {
        return dataDescriptor;
    }

    void setDataDescriptor(DataDescriptor dataDescriptor) {
        this.dataDescriptor = dataDescriptor;
    }

    @Override
    public Optional<TorrentSessionState> getSessionState() {
        return Optional.ofNullable(sessionState);
    }

    void setSessionState(TorrentSessionState sessionState) {
        this.sessionState = sessionState;
    }
}
